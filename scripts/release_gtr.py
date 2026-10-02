"""On-demand GTR releases. Credentials never enter command arguments or logs."""
import argparse
from contextlib import contextmanager
import fcntl
import hashlib
import io
import json
import os
from pathlib import Path
import re
import shutil
import socket
import stat
import subprocess
import sys
import tempfile
import urllib.parse
import urllib.request
import uuid
import xml.etree.ElementTree as ET
import zipfile

REPO = "SongYuanKun/idea-env-switcher"
PLUGIN_ID = "io.github.ideaenvswitcher"
MARKETPLACE = "https://plugins.jetbrains.com"
JDK_NAME = "jdk-21.0.12.1+1"
# Verified against the official Temurin archive (SHA-256 ce79869e...faee94).
JDK_TREE_SHA = "8752eb91e52234353bb3163bcbf05d8b943daeb181d6f13b1ae68613ccd5b5c5"
GH_SHA = "d2c22fcdb510e312385701f0fcbbc37930b899b55102ab7430a9e441d142c748"
WRAPPER_SHA = "76805e32c009c0cf0dd5d206bddc9fb22ea42e84db904b764f3047de095493f3"
GRADLE_SHA = "8fad3d78296ca518113f3d29016617c7f9367dc005f932bd9d93bf45ba46072b"
SIGNER_SHA = "2958a0f42221d6062b50f22754e413ae5cc42f60c441467202c6700df2e22f44"
SIGNER_URL = "https://github.com/JetBrains/marketplace-zip-signer/releases/download/0.1.43/marketplace-zip-signer-cli-0.1.43.jar"
INPUTS = ("src", "build.gradle.kts", "settings.gradle.kts", "gradle.properties", "gradlew", "gradlew.bat", "gradle")
SECRET_NAMES = ("CERTIFICATE_CHAIN", "PRIVATE_KEY", "PRIVATE_KEY_PASSWORD", "PUBLISH_TOKEN", "GH_TOKEN", "GITHUB_TOKEN")


class ReleaseError(Exception):
    pass


def sha(data):
    return hashlib.sha256(data).hexdigest()


def clean_env():
    return {k: v for k, v in os.environ.items() if k not in SECRET_NAMES}


def command(args, cwd, env=None, data=None):
    try:
        result = subprocess.run(args, cwd=cwd, env=env or clean_env(), input=data,
                                stdout=subprocess.PIPE, stderr=subprocess.PIPE, timeout=900)
    except (OSError, subprocess.TimeoutExpired):
        raise ReleaseError(f"{Path(args[0]).name} could not finish; no automatic retry") from None
    if result.returncode:
        # A subprocess may echo credentials. Do not print its captured output.
        raise ReleaseError(f"{Path(args[0]).name} failed (exit {result.returncode})")
    return result.stdout


class HTTPSRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, request, fp, code, msg, headers, new_url):
        if urllib.parse.urlsplit(new_url).scheme != "https":
            raise ReleaseError("refusing a non-HTTPS redirect")
        redirected = super().redirect_request(request, fp, code, msg, headers, new_url)
        if redirected is None:
            return None
        if urllib.parse.urlsplit(request.full_url).netloc != urllib.parse.urlsplit(new_url).netloc:
            redirected.remove_header("Authorization")
        return redirected


def request(url, token=None, data=None, content_type=None):
    if urllib.parse.urlsplit(url).scheme != "https":
        raise ReleaseError("HTTPS is required for release requests")
    headers = {"User-Agent": "idea-env-switcher-gtr-release"}
    if token:
        if urllib.parse.urlsplit(url).netloc != "plugins.jetbrains.com":
            raise ReleaseError("token destination is not Marketplace")
        headers["Authorization"] = "Bearer " + token
    if content_type:
        headers["Content-Type"] = content_type
    try:
        req = urllib.request.Request(url, data=data, headers=headers)
        with urllib.request.build_opener(HTTPSRedirect()).open(req, timeout=60) as response:
            return response.read()
    except ReleaseError:
        raise
    except Exception:
        raise ReleaseError("HTTPS request failed; no automatic retry") from None


def parse_json(data):
    try:
        return json.loads(data)
    except (ValueError, TypeError):
        raise ReleaseError("API returned invalid JSON") from None


def release_notes(text, version):
    match = re.search(r"^## \[" + re.escape(version) + r"\][^\n]*\n(.*?)(?=^## |\Z)", text, re.M | re.S)
    if not match or not match.group(1).strip():
        raise ReleaseError(f"CHANGELOG has no release notes for {version}")
    return match.group(1).strip() + "\n"


def inspect_package(data, version):
    try:
        with zipfile.ZipFile(io.BytesIO(data)) as outer:
            names = [n for n in outer.namelist() if not n.endswith("/")]
            if len(names) != len(set(names)):
                raise ReleaseError("duplicate archive entries")
            jar_name = f"idea-env-switcher/lib/idea-env-switcher-{version}.jar"
            if jar_name not in names:
                raise ReleaseError("plugin JAR version or path does not match")
            jar_data = outer.read(jar_name)
            files = {n: sha(outer.read(n)) for n in names}
        with zipfile.ZipFile(io.BytesIO(jar_data)) as jar:
            metadata = ET.fromstring(jar.read("META-INF/plugin.xml"))
        if metadata.findtext("id") != PLUGIN_ID or metadata.findtext("version") != version:
            raise ReleaseError("plugin ID or version does not match")
        return {"zip_sha256": sha(data), "jar_sha256": sha(jar_data), "files": files}
    except (zipfile.BadZipFile, KeyError, ET.ParseError):
        raise ReleaseError("invalid plugin distribution") from None


def require_same(a, b):
    if a["files"] != b["files"]:
        raise ReleaseError("plugin runtime content differs; refusing replacement")


def private_file(path):
    try:
        info = path.lstat()
    except FileNotFoundError:
        raise ReleaseError(f"missing credential: {path}") from None
    if not stat.S_ISREG(info.st_mode) or info.st_uid != os.getuid() or stat.S_IMODE(info.st_mode) != 0o600:
        raise ReleaseError(f"credential must be an owned regular file with mode 0600: {path}")
    return path.read_bytes()


def tree_sha(root):
    records = []
    for path in root.rglob("*"):
        name = path.relative_to(root).as_posix()
        if path.is_symlink():
            records.append((name, "link:" + os.readlink(path)))
        elif path.is_file():
            records.append((name, sha(path.read_bytes())))
    return sha("\n".join(f"{name} {digest}" for name, digest in sorted(records)).encode())


def check_wrapper_properties(text):
    expected = {
        "distributionBase": "GRADLE_USER_HOME", "distributionPath": "wrapper/dists",
        "distributionUrl": "https://services.gradle.org/distributions/gradle-9.0.0-bin.zip",
        "distributionSha256Sum": GRADLE_SHA, "networkTimeout": "10000",
        "validateDistributionUrl": "true", "zipStoreBase": "GRADLE_USER_HOME", "zipStorePath": "wrapper/dists",
    }
    found = {}
    for line in text.splitlines():
        if not line.strip() or line.lstrip().startswith("#"):
            continue
        key, separator, value = line.partition("=")
        if not separator or key in found:
            raise ReleaseError("invalid or duplicate Gradle wrapper property")
        found[key] = value.replace("\\:", ":")
    if found != expected:
        raise ReleaseError("Gradle wrapper properties must match the pinned official distribution")


class Release:
    def __init__(self, root, version, notes):
        self.root, self.version, self.notes = root, version, notes
        self.tag = "v" + version
        self.asset_name = f"idea-env-switcher-{version}-signed.zip"
        self.output = root / "build" / "release"
        self.config = Path.home() / ".config" / "idea-env-switcher"
        self.jdk = Path.home() / ".jdks" / JDK_NAME
        self.manifest = self.output / "prepared.json"

    def git(self, *args):
        return command(["git", *args], self.root).decode().strip()

    def gh(self, *args):
        return command(["gh", *args], self.root)

    @contextmanager
    def lock(self):
        if socket.gethostname() != "kun-GTR":
            raise ReleaseError("release commands must run on kun-GTR")
        path = Path(self.git("rev-parse", "--absolute-git-dir")) / "idea-env-release.lock"
        with path.open("a") as lock:
            try:
                fcntl.flock(lock, fcntl.LOCK_EX | fcntl.LOCK_NB)
            except BlockingIOError:
                raise ReleaseError("another release command holds the repository lock") from None
            try:
                yield
            finally:
                fcntl.flock(lock, fcntl.LOCK_UN)

    def preflight(self, read_only=False):
        if socket.gethostname() != "kun-GTR":
            raise ReleaseError("release commands must run on kun-GTR")
        for tool in ("git", "gh", "openssl"):
            if not shutil.which(tool):
                raise ReleaseError(f"missing tool: {tool}")
        if sha(Path(shutil.which("gh")).read_bytes()) != GH_SHA:
            raise ReleaseError("gh must be the verified official 2.70.0 Linux amd64 binary")
        self.gh("auth", "status")
        if self.git("branch", "--show-current") != "main":
            raise ReleaseError("checkout main before releasing")
        # Fetch updates local refs only; no push or release is performed here.
        self.git("fetch", "--no-recurse-submodules", "--tags", "origin")
        if self.git("rev-parse", "HEAD") != self.git("rev-parse", "origin/main"):
            raise ReleaseError("HEAD must equal origin/main")
        paths = [*INPUTS, ":(exclude)gradle/wrapper/gradle-wrapper.properties"]
        self.git("diff", "--exit-code", self.tag, "HEAD", "--", *paths)
        self.git("diff", "--exit-code", "HEAD", "--", *(paths if read_only else ["."]))
        untracked = self.git("ls-files", "--others", "--exclude-standard").splitlines()
        if any(p == entry or p.startswith(entry + "/") for p in untracked for entry in INPUTS):
            raise ReleaseError("untracked build inputs must be committed first")
        if sha((self.root / "gradle/wrapper/gradle-wrapper.jar").read_bytes()) != WRAPPER_SHA:
            raise ReleaseError("Gradle wrapper checksum mismatch")
        check_wrapper_properties((self.root / "gradle/wrapper/gradle-wrapper.properties").read_text())

    def java_tools(self):
        if not self.jdk.is_dir() or tree_sha(self.jdk) != JDK_TREE_SHA:
            raise ReleaseError(f"verified Temurin 21.0.12.1+1 is required at {self.jdk}")
        env = clean_env()
        env["JAVA_HOME"] = str(self.jdk)
        env["PATH"] = str(self.jdk / "bin") + os.pathsep + env.get("PATH", "")
        return env

    def signer(self, allow_download=False):
        path = Path.home() / ".cache/idea-env-switcher/marketplace-zip-signer-cli-0.1.43.jar"
        if not path.exists() and allow_download:
            data = request(SIGNER_URL)
            if sha(data) != SIGNER_SHA:
                raise ReleaseError("ZIP Signer download checksum mismatch")
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_bytes(data)
        if not path.exists() or sha(path.read_bytes()) != SIGNER_SHA:
            raise ReleaseError("verified Marketplace ZIP Signer 0.1.43 is required; run prepare")
        return path

    def credentials(self, need_token=False):
        for folder in (self.config, self.config / "signing"):
            if not folder.is_dir() or folder.is_symlink() or folder.stat().st_uid != os.getuid() or stat.S_IMODE(folder.stat().st_mode) != 0o700:
                raise ReleaseError(f"credential directory must be owned and mode 0700: {folder}")
        chain = self.config / "signing/chain.crt"
        key = self.config / "signing/private.pem"
        password = private_file(self.config / "signing/private-key-password").rstrip(b"\r\n")
        certificate, private_key = private_file(chain), private_file(key)
        if not password or b"\n" in password or b"\r" in password:
            raise ReleaseError("private-key-password must contain one nonempty line")
        if b"BEGIN ENCRYPTED PRIVATE KEY" not in private_key and b"Proc-Type: 4,ENCRYPTED" not in private_key:
            raise ReleaseError("private.pem must remain encrypted")
        command(["openssl", "x509", "-in", str(chain), "-checkend", "0", "-noout"], self.root)
        command(["openssl", "verify", "-CAfile", str(chain), str(chain)], self.root)
        public_key = command(["openssl", "pkey", "-in", str(key), "-passin", "stdin", "-pubout"], self.root, data=password + b"\n")
        certificate_key = command(["openssl", "x509", "-in", str(chain), "-pubkey", "-noout"], self.root)
        if public_key != certificate_key:
            raise ReleaseError("signing certificate and private key do not match")
        result = {"CERTIFICATE_CHAIN": certificate.decode(), "PRIVATE_KEY": private_key.decode(),
                  "PRIVATE_KEY_PASSWORD": password.decode()}
        if need_token:
            token = private_file(self.config / "marketplace.token").decode().strip()
            if not token.startswith("perm:") or "\n" in token:
                raise ReleaseError("marketplace.token must contain a permanent token")
            result["PUBLISH_TOKEN"] = token
        return result

    def verify_signature(self, path, env):
        command([str(self.jdk / "bin/java"), "-jar", str(self.signer()), "verify",
                 "-in", str(path), "-cert", str(self.config / "signing/chain.crt")], self.root, env=env)

    def marketplace_updates(self, token=None):
        updates = []
        for page in range(1000):
            batch = parse_json(request(f"{MARKETPLACE}/api/plugins/34289/updates?size=100&page={page}", token))
            if not isinstance(batch, list):
                raise ReleaseError("unexpected Marketplace updates response")
            updates.extend(batch)
            if len(batch) < 100:
                return updates
        raise ReleaseError("Marketplace pagination did not terminate")

    def remote_state(self, token=None):
        pages = parse_json(self.gh("api", "--paginate", "--slurp", f"repos/{REPO}/releases?per_page=100"))
        releases = [item for page in pages for item in page]
        matches = [r for r in releases if r.get("tag_name") == self.tag]
        if len(matches) > 1:
            raise ReleaseError("duplicate GitHub releases")
        github = matches[0] if matches else None
        matches = [u for u in self.marketplace_updates(token) if u.get("version") == self.version and not u.get("channel")]
        if len(matches) > 1:
            raise ReleaseError("duplicate Stable Marketplace updates")
        marketplace = matches[0] if matches else None
        state = {"github": github, "marketplace": marketplace, "github_package": None, "marketplace_package": None}
        if github:
            assets = [a for a in github.get("assets", []) if a.get("name") == self.asset_name]
            if len(assets) > 1:
                raise ReleaseError("duplicate signed GitHub assets")
            if assets:
                state["github_package"] = inspect_package(request(assets[0]["browser_download_url"]), self.version)
        if marketplace:
            if marketplace.get("isHidden") or marketplace.get("hidden"):
                raise ReleaseError("Marketplace update is hidden; inspect it before publishing")
            file_path = marketplace.get("file", "")
            if not file_path.startswith("34289/") or ".." in file_path:
                raise ReleaseError("unexpected Marketplace download path")
            state["marketplace_package"] = inspect_package(request(f"{MARKETPLACE}/files/{file_path}", token), self.version)
        return state

    def pending_path(self):
        git_dir = Path(self.git("rev-parse", "--absolute-git-dir"))
        return git_dir / f"idea-env-release-{self.version}-inflight.json"

    def pending_operation(self):
        path = self.pending_path()
        return parse_json(path.read_bytes()) if path.exists() else None

    def save_marker(self, target):
        path = self.pending_path()
        try:
            fd = os.open(path, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
        except FileExistsError:
            raise ReleaseError("previous write outcome is unknown; refusing to overwrite its marker") from None
        with os.fdopen(fd, "w") as marker:
            json.dump({"target": target, "version": self.version}, marker)
            marker.flush()
            os.fsync(marker.fileno())
        directory_fd = os.open(path.parent, os.O_RDONLY | os.O_DIRECTORY)
        try:
            os.fsync(directory_fd)
        finally:
            os.close(directory_fd)

    def prepare(self):
        if self.pending_operation():
            raise ReleaseError("previous write outcome is unknown; resolve it before cleaning artifacts")
        env = self.java_tools()
        secrets = self.credentials()
        self.signer(allow_download=True)
        try:
            command(["./gradlew", "clean", "test", "verifyPlugin", "signPlugin", "--no-daemon",
                     "--no-build-cache", "--no-configuration-cache"], self.root, env={**env, **secrets})
        finally:
            secrets.clear()
        path = self.root / "build/distributions" / self.asset_name
        artifact = inspect_package(path.read_bytes(), self.version)
        self.verify_signature(path, env)
        self.output.mkdir(parents=True, exist_ok=True)
        manifest = {**artifact, "head": self.git("rev-parse", "HEAD"), "version": self.version,
                    "tag_tree": self.git("rev-parse", self.tag + "^{tree}"),
                    "head_tree": self.git("rev-parse", "HEAD^{tree}"),
                    "path": str(path), "certificate_sha256": sha(private_file(self.config / "signing/chain.crt"))}
        self.manifest.write_text(json.dumps(manifest, indent=2) + "\n")
        print(f"Prepared {self.tag}; tests, Plugin Verifier and independent signature verification passed")
        print(f"Signed ZIP: {path}\nSHA-256: {artifact['zip_sha256']}\nJAR SHA-256: {artifact['jar_sha256']}")
        print(self.notes)
        self.publish(dry_run=True)

    def prepared(self, env):
        if not self.manifest.exists():
            raise ReleaseError("run prepare for this HEAD before publishing")
        manifest = parse_json(self.manifest.read_bytes())
        if manifest.get("head") != self.git("rev-parse", "HEAD") or manifest.get("version") != self.version:
            raise ReleaseError("prepare evidence is stale; run prepare again")
        path = self.root / "build/distributions" / self.asset_name
        artifact = inspect_package(path.read_bytes(), self.version)
        if artifact != {k: manifest.get(k) for k in artifact}:
            raise ReleaseError("prepared distribution was modified")
        if manifest.get("certificate_sha256") != sha(private_file(self.config / "signing/chain.crt")):
            raise ReleaseError("signing certificate changed after prepare")
        self.verify_signature(path, env)
        return path, artifact

    def write_github(self, state, path):
        with tempfile.TemporaryDirectory(prefix="idea-env-notes-") as tmp:
            notes = Path(tmp) / "notes.md"
            notes.write_text(self.notes)
            if not state["github"]:
                self.gh("release", "create", self.tag, str(path), "--repo", REPO, "--verify-tag",
                        "--title", self.tag, "--notes-file", str(notes))
            else:
                if not state["github_package"]:
                    self.gh("release", "upload", self.tag, str(path), "--repo", REPO)
                if state["github"].get("draft"):
                    self.gh("release", "edit", self.tag, "--repo", REPO, "--draft=false",
                            "--title", self.tag, "--notes-file", str(notes))

    def write_marketplace(self, path, token):
        boundary = "ideaenv" + uuid.uuid4().hex
        fields = [("pluginId", "34289"), ("channel", "")]
        body = b"".join(f'--{boundary}\r\nContent-Disposition: form-data; name="{name}"\r\n\r\n{value}\r\n'.encode() for name, value in fields)
        body += (f'--{boundary}\r\nContent-Disposition: form-data; name="file"; filename="{self.asset_name}"\r\nContent-Type: application/zip\r\n\r\n'.encode()
                 + path.read_bytes() + f"\r\n--{boundary}--\r\n".encode())
        parse_json(request(f"{MARKETPLACE}/api/updates/upload", token, body, "multipart/form-data; boundary=" + boundary))

    def publish(self, dry_run=False):
        state = self.remote_state()
        if state["github_package"] and state["marketplace_package"]:
            require_same(state["github_package"], state["marketplace_package"])
        github_done = bool(state["github"] and not state["github"].get("draft") and state["github_package"])
        marketplace_done = bool(state["marketplace_package"])
        actions = ([] if github_done else ["github"]) + ([] if marketplace_done else ["marketplace"])
        print(f"{self.tag}: GitHub={'published' if github_done else 'incomplete'}; Marketplace="
              + ("approved" if marketplace_done and state["marketplace"].get("approve") else "submitted, awaiting approval" if marketplace_done else "not visible in public updates"))
        marker = self.pending_operation()
        if not actions:
            if marker and not dry_run:
                self.pending_path().unlink()
            print("All destinations have identical runtime content; external write requests: 0")
            return
        if marker:
            raise ReleaseError("previous write outcome is unknown; inspect remote state before another upload")
        if dry_run:
            print("Would complete: " + ", ".join(actions) + "; external write requests: 0")
            return
        secrets = self.credentials(need_token=True)
        try:
            env = self.java_tools()
            path, artifact = self.prepared(env)
            # Authenticated refresh may reveal a pending Marketplace update.
            state = self.remote_state(secrets["PUBLISH_TOKEN"])
            for key in ("github_package", "marketplace_package"):
                if state[key]:
                    require_same(artifact, state[key])
            actions = ([] if state["github"] and not state["github"].get("draft") and state["github_package"] else ["github"])
            actions += [] if state["marketplace_package"] else ["marketplace"]
            if not actions:
                print("Authenticated refresh confirms completion; external write requests: 0")
                return
            phrase = f"publish {self.version} github marketplace"
            if not sys.stdin.isatty() or input(f"To publish to GitHub Release and Marketplace Stable, type '{phrase}': ") != phrase:
                raise ReleaseError("current-context publication confirmation was not provided")
            for target in actions:
                self.save_marker(target)
                try:
                    if target == "github":
                        self.write_github(state, path)
                    else:
                        self.write_marketplace(path, secrets["PUBLISH_TOKEN"])
                except ReleaseError:
                    # Read back once, even when the write response was lost. Never repeat the write.
                    pass
                state = self.remote_state(secrets["PUBLISH_TOKEN"])
                published = state[target + "_package"]
                if not published or (target == "github" and state["github"].get("draft")):
                    raise ReleaseError(f"{target} write outcome is unknown; marker retained, no automatic retry")
                require_same(artifact, published)
                if target == "github" and state["github"].get("body", "").strip() != self.notes.strip():
                    raise ReleaseError("GitHub release notes differ; inspect the release")
                self.pending_path().unlink()
                print(f"{target}: upload verified" + ("; Marketplace approval may still be pending" if target == "marketplace" else ""))
        finally:
            secrets.clear()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("action", choices=("prepare", "publish"))
    parser.add_argument("--dry-run", action="store_true", help="read-only checks for publish")
    args = parser.parse_args()
    if args.dry_run and args.action != "publish":
        parser.error("--dry-run is only supported with publish")
    root = Path(__file__).resolve().parents[1]
    versions = re.findall(r'^version\s*=\s*"([0-9]+\.[0-9]+\.[0-9]+)"', (root / "build.gradle.kts").read_text(), re.M)
    if len(versions) != 1:
        raise ReleaseError("expected exactly one semantic plugin version")
    release = Release(root, versions[0], release_notes((root / "CHANGELOG.md").read_text(), versions[0]))
    with release.lock():
        release.preflight(args.dry_run)
        if args.action == "prepare":
            release.prepare()
        else:
            release.publish(args.dry_run)


if __name__ == "__main__":
    try:
        main()
    except (ReleaseError, OSError, ValueError) as error:
        # Never include subprocess output or request headers in failures.
        print(f"Release stopped: {error}", file=sys.stderr)
        sys.exit(1)
