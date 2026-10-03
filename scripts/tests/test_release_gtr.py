"""Safety checks for release orchestration; all external writes are mocked."""
import importlib.util
import io
from pathlib import Path
import tempfile
import unittest
from unittest.mock import Mock, patch
import zipfile

MODULE_PATH = Path(__file__).parents[1] / "release_gtr.py"
SPEC = importlib.util.spec_from_file_location("release_gtr", MODULE_PATH)
release = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(release)


def package(version="0.3.2", plugin_id="io.github.ideaenvswitcher", extra=None):
    jar_data = io.BytesIO()
    with zipfile.ZipFile(jar_data, "w") as jar:
        jar.writestr("META-INF/plugin.xml", f"<idea-plugin><id>{plugin_id}</id><version>{version}</version></idea-plugin>")
        jar.writestr("Plugin.class", b"compiled-plugin")
    data = io.BytesIO()
    with zipfile.ZipFile(data, "w") as outer:
        outer.writestr(f"idea-env-switcher/lib/idea-env-switcher-{version}.jar", jar_data.getvalue())
        if extra:
            outer.writestr("idea-env-switcher/lib/extra.jar", extra)
    return release.inspect_package(data.getvalue(), "0.3.2")


class PackageTests(unittest.TestCase):
    def test_duplicate_wrapper_url_cannot_override_verified_download(self):
        props = (Path(__file__).parents[2] / "gradle/wrapper/gradle-wrapper.properties").read_text()
        release.check_wrapper_properties(props)
        with self.assertRaises(release.ReleaseError):
            release.check_wrapper_properties(props + "\ndistributionUrl=https://example.com/gradle.zip\n")

    def test_refuses_initial_plain_http_before_network_access(self):
        with patch.object(release.urllib.request, "build_opener") as opener:
            with self.assertRaisesRegex(release.ReleaseError, "HTTPS"):
                release.request("http://example.com/plugin.zip")
            opener.assert_not_called()

    def test_rejects_wrong_plugin_identity_and_version(self):
        for kwargs in ({"plugin_id": "wrong.plugin"}, {"version": "0.3.1"}):
            with self.subTest(kwargs=kwargs), self.assertRaises(release.ReleaseError):
                package(**kwargs)

    def test_compares_every_runtime_file(self):
        with self.assertRaises(release.ReleaseError):
            release.require_same(package(), package(extra=b"different-library"))

    def test_selects_only_requested_changelog_entry(self):
        text = "## [Unreleased]\n\nUpcoming\n\n## [0.3.2] - date\n\n### Fixed\n- Bundle API\n\n## [0.3.1] - date\nOld\n"
        self.assertEqual(release.release_notes(text, "0.3.2"), "### Fixed\n- Bundle API\n")
        with self.assertRaises(release.ReleaseError):
            release.release_notes(text, "0.4.0")

    def test_refuses_readable_private_files_and_symlinks(self):
        with tempfile.TemporaryDirectory() as d:
            path = Path(d) / "token"
            path.write_text("private-test-value")
            path.chmod(0o644)
            with self.assertRaises(release.ReleaseError):
                release.private_file(path)
            path.chmod(0o600)
            self.assertEqual(release.private_file(path), b"private-test-value")
            link = Path(d) / "link"
            link.symlink_to(path)
            with self.assertRaises(release.ReleaseError):
                release.private_file(link)


class CredentialTests(unittest.TestCase):
    def setUp(self):
        directory = tempfile.TemporaryDirectory()
        self.addCleanup(directory.cleanup)
        self.obj = release.Release(Path(directory.name), "0.4.0", "notes")
        self.obj.config = Path(directory.name) / "credentials"
        self.obj.config.mkdir(mode=0o700)
        signing = self.obj.config / "signing"
        signing.mkdir(mode=0o700)
        for name, value in {"chain.crt": "test certificate",
                            "private.pem": "BEGIN ENCRYPTED PRIVATE KEY",
                            "private-key-password": "test password"}.items():
            path = signing / name
            path.write_text(value)
            path.chmod(0o600)

    def read_token(self, value):
        path = self.obj.config / "marketplace.token"
        path.write_text(value)
        path.chmod(0o600)
        # Certificate validation is unrelated to token parsing; no real keys are used.
        with patch.object(release, "command", return_value=b"test public key"):
            return self.obj.credentials(need_token=True)["PUBLISH_TOKEN"]

    def test_preserves_current_and_legacy_marketplace_tokens(self):
        for value in ("opaque.fixture.token", "perm:legacy-fixture"):
            with self.subTest(value=value):
                self.assertEqual(self.read_token(value + "\r\n"), value)

    def test_rejects_empty_tokens_and_header_control_characters(self):
        for value in ("", "\n", "perm:one\ntwo", "perm:one\rtwo", "perm:one\ttwo",
                      "perm:one two", "perm:one\x00two", "perm:one\x7ftwo", "perm:非ASCII"):
            with self.subTest(value=value), self.assertRaises(release.ReleaseError):
                self.read_token(value)


class PublishTests(unittest.TestCase):
    def test_prepare_and_publish_cannot_hold_the_same_repository_lock(self):
        with tempfile.TemporaryDirectory() as directory:
            a = release.Release(Path(directory), "0.3.2", "notes")
            b = release.Release(Path(directory), "0.3.2", "notes")
            a.git = b.git = Mock(return_value=directory)
            with a.lock():
                with self.assertRaisesRegex(release.ReleaseError, "another release"):
                    with b.lock():
                        self.fail("concurrent release lock was granted")
            with b.lock():
                pass

    def make_release(self, github=True, marketplace=True, approved=True):
        obj = release.Release(Path.cwd(), "0.3.2", "### Fixed\n- Bundle API\n")
        artifact = package()
        state = {
            "github": {"draft": False} if github else None,
            "marketplace": {"approve": approved} if marketplace else None,
            "github_package": artifact if github else None,
            "marketplace_package": artifact if marketplace else None,
        }
        obj.remote_state = Mock(return_value=state)
        obj.credentials = Mock(side_effect=AssertionError("must not load credentials"))
        obj.write_github = Mock(side_effect=AssertionError("must not write GitHub"))
        obj.write_marketplace = Mock(side_effect=AssertionError("must not upload Marketplace"))
        obj.pending_operation = Mock(return_value=None)
        return obj, state

    def test_already_published_needs_no_secrets_and_writes_nothing(self):
        obj, _ = self.make_release()
        obj.publish(False)
        obj.credentials.assert_not_called()
        obj.write_github.assert_not_called()
        obj.write_marketplace.assert_not_called()

    def test_dry_run_does_not_require_secrets_or_upload_missing_targets(self):
        obj, _ = self.make_release(False, False)
        obj.publish(True)
        obj.credentials.assert_not_called()
        obj.write_github.assert_not_called()
        obj.write_marketplace.assert_not_called()

    def test_pending_moderation_is_not_uploaded_again(self):
        obj, _ = self.make_release(approved=False)
        obj.publish(False)
        obj.write_marketplace.assert_not_called()

    def test_authenticated_pending_update_does_not_upload_or_confirm(self):
        obj, anonymous = self.make_release(True, False)
        authenticated = {**anonymous, "marketplace": {"approve": False}, "marketplace_package": package()}
        obj.remote_state.side_effect = [anonymous, authenticated]
        obj.credentials = Mock(return_value={"PUBLISH_TOKEN": "perm:test-fixture"})
        obj.java_tools = Mock(return_value={})
        obj.prepared = Mock(return_value=(Path("test-artifact.zip"), package()))
        with patch("builtins.input", side_effect=AssertionError("must not confirm")):
            obj.publish(False)
        obj.write_marketplace.assert_not_called()
        self.assertEqual(obj.remote_state.call_count, 2)

    def test_unknown_write_is_not_retried_and_marker_tracks_readback(self):
        for recovered in (True, False):
            with self.subTest(recovered=recovered), tempfile.TemporaryDirectory() as directory:
                obj, missing = self.make_release(True, False)
                returned = {**missing, "marketplace": {"approve": False}, "marketplace_package": package()} if recovered else missing
                obj.remote_state.side_effect = [missing, missing, returned]
                obj.credentials = Mock(return_value={"PUBLISH_TOKEN": "perm:test-fixture"})
                obj.java_tools = Mock(return_value={})
                obj.prepared = Mock(return_value=(Path("test-artifact.zip"), package()))
                obj.write_marketplace = Mock(side_effect=release.ReleaseError("unknown write response"))
                marker = Path(directory) / "marker.json"
                obj.pending_path = Mock(return_value=marker)
                with patch.object(release.sys, "stdin") as stdin, patch("builtins.input", return_value="publish 0.3.2 github marketplace"):
                    stdin.isatty.return_value = True
                    if recovered:
                        obj.publish(False)
                    else:
                        with self.assertRaisesRegex(release.ReleaseError, "unknown"):
                            obj.publish(False)
                obj.write_marketplace.assert_called_once()
                self.assertEqual(obj.remote_state.call_count, 3)
                self.assertEqual(marker.exists(), not recovered)

    def test_conflicting_remote_packages_stop_before_loading_secrets(self):
        obj, state = self.make_release()
        state["marketplace_package"] = package(extra=b"changed-content")
        with self.assertRaises(release.ReleaseError):
            obj.publish(False)
        obj.credentials.assert_not_called()

    def test_unresolved_previous_write_blocks_another_upload(self):
        obj, _ = self.make_release(True, False)
        obj.pending_operation.return_value = {"target": "marketplace"}
        with self.assertRaisesRegex(release.ReleaseError, "unknown"):
            obj.publish(False)
        obj.credentials.assert_not_called()
        obj.write_marketplace.assert_not_called()

    def test_stale_preparation_blocks_signature_verification(self):
        with tempfile.TemporaryDirectory() as directory:
            obj = release.Release(Path(directory), "0.3.2", "notes")
            obj.output.mkdir(parents=True)
            obj.manifest.write_text('{"head":"old-commit","version":"0.3.2"}')
            obj.git = Mock(return_value="new-commit")
            obj.verify_signature = Mock()
            with self.assertRaisesRegex(release.ReleaseError, "stale"):
                obj.prepared({})
            obj.verify_signature.assert_not_called()


if __name__ == "__main__":
    unittest.main()
