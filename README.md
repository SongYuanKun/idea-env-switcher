# Env Switcher

[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE)
[![Build](https://github.com/SongYuanKun/idea-env-switcher/actions/workflows/build.yml/badge.svg)](https://github.com/SongYuanKun/idea-env-switcher/actions/workflows/build.yml)
[![Release](https://img.shields.io/github/v/release/SongYuanKun/idea-env-switcher)](https://github.com/SongYuanKun/idea-env-switcher/releases)

**Env Switcher** is a free, **non-commercial** open source plugin for IntelliJ IDEA.
It lets you switch named environment profiles (dev / staging / prod …) with one click.

This project is volunteer-maintained. It does **not** offer paid support, consulting, or commercial editions.

- **License (OSI)**: [Apache License 2.0](https://github.com/SongYuanKun/idea-env-switcher/blob/main/LICENSE)
- **Latest plugin zip**: [GitHub Releases](https://github.com/SongYuanKun/idea-env-switcher/releases)
- **Marketplace**: [Env Switcher](https://plugins.jetbrains.com/plugin/34289-env-switcher)
- **Code of Conduct**: [CODE_OF_CONDUCT.md](CODE_OF_CONDUCT.md)
- **Contributing**: [CONTRIBUTING.md](CONTRIBUTING.md)
- **Security**: [SECURITY.md](SECURITY.md)
- **Changelog**: [CHANGELOG.md](CHANGELOG.md)

## Features

- Manage profiles and variables in **Settings → Tools → Env Switcher** or **Tools → Manage Environment Profiles**
- Load profiles from project-root `env-profiles.json`
- **Tools → Switch Environment** (shortcut `Ctrl+Alt+E`)
- Write the active profile into a generated `.env` file
- **Inject the active profile into Java Run Configurations at runtime** (Application / JUnit / etc.; does not rewrite saved configs)
- Inject the active profile into **local Python Run Configurations** when the optional **Python Community** plugin is installed
- Remember the last selected profile in the workspace and restore it on project open
- Status bar widget for the current profile (click to switch again)
- **Tools → Reload Environment Profiles**

## Install

In IntelliJ IDEA, open **Settings → Plugins → Marketplace**, search for
**Env Switcher**, and click **Install**.

For offline installation:

1. Download `idea-env-switcher-*.zip` from [Releases](https://github.com/SongYuanKun/idea-env-switcher/releases).
2. IntelliJ IDEA → **Settings → Plugins → ⚙️ → Install Plugin from Disk…**
3. Restart the IDE if prompted.

Requires **IntelliJ IDEA 2024.3+** (`sinceBuild=243`) with the bundled Java plugin.

## Quick start

1. Open **Tools → Manage Environment Profiles**, add a profile and its variables, and click **Apply**. Alternatively, copy [examples/env-profiles.json](examples/env-profiles.json) to your project root as `env-profiles.json`.
2. **Tools → Switch Environment**, pick a profile.
3. Check that `.env` was generated/updated and the status bar shows `Env: <name>`.
4. Run an Application / JUnit configuration, or a local Python configuration with Python Community installed — process environment includes the active profile variables (same-name keys from the profile win).

### Edit profiles

Use **Settings → Tools → Env Switcher** to add, rename or remove profiles, edit
descriptions, and add or remove variable rows. Changes remain staged until
**Apply**; **Reset** reloads the current file and discards staged edits. Applying
edits to the active profile also refreshes `.env` and the workspace selection.
Deleting the active profile clears its selection and clears `.env` only when
its contents still match the generated file, preserving manual changes.

Names must be unique and non-empty. Variable names use letters, digits and
underscores and cannot start with a digit. Invalid JSON is displayed as an error;
the editor refuses to overwrite a malformed file or an external change.
**Reload Environment Profiles** also updates the selected environment after
changes made directly to the JSON file.

### `env-profiles.json` format

```json
{
  "profiles": [
    {
      "name": "dev",
      "description": "Local development",
      "env": {
        "APP_ENV": "dev",
        "API_BASE": "http://localhost:8080"
      }
    }
  ]
}
```

### How Run Configuration injection works

- Injection happens **when the configuration starts**, via the Java or optional Python `RunConfigurationExtension`.
- Saved Run Configuration XML under `.idea/` is **not** modified.
- Profile values **override** existing env keys with the same name; other keys from the Run Configuration are kept.
- Python support requires the **Python Community** (`PythonCore`) plugin and a local interpreter. Remote / target Python execution is outside this support scope.
- Gradle, Node.js and npm runners can read the generated `.env` when configured by their tooling; direct injection into those runners is not supported.

## Develop from source

```bash
export JAVA_HOME=/path/to/jdk-21
./gradlew test
./gradlew runIde
./gradlew buildPlugin
```

Artifact: `build/distributions/idea-env-switcher-<version>.zip`

## Release from GTR

Run [scripts/release-on-gtr.sh](scripts/release-on-gtr.sh) on `kun-GTR`.
Routine releases use the command line, with no browser or persistent runner.
GitHub Actions continues to build and test without release credentials.

```bash
# Read-only: compare the published packages and show missing destinations.
./scripts/release-on-gtr.sh publish --dry-run

# Build, test, verify compatibility, sign, then independently verify the ZIP.
./scripts/release-on-gtr.sh prepare

# Upload prepared content only to destinations that are still missing it.
./scripts/release-on-gtr.sh publish
```

The script requires `main` synchronized with `origin/main`, a `v<version>` tag,
and release notes in `CHANGELOG.md`. Plugin build inputs must match the tag;
later documentation and release-tool commits are allowed. `prepare` and writes
also require a clean tracked working tree. Gradle 9.0.0 and its wrapper have
pinned checksums. The script verifies the official `gh` 2.70.0 Linux amd64 binary
and the full Temurin 21.0.12.1+1 installation at `~/.jdks/jdk-21.0.12.1+1`.
ZIP Signer 0.1.43 is downloaded from JetBrains with a pinned SHA-256 on the
first `prepare`; it is stored under `~/.cache/idea-env-switcher/`.

Set up these **local files**, outside the repository, once:

| Path under `~/.config/idea-env-switcher/` | Contents | Permissions |
| --- | --- | --- |
| `signing/chain.crt` | Author certificate chain (self-signed is supported) | `0600` |
| `signing/private.pem` | Existing **encrypted** signing key | `0600` |
| `signing/private-key-password` | Key password, one line | `0600` |
| `marketplace.token` | Marketplace permanent token | `0600` |

Both the base directory and `signing/` must be owned by you with permissions
`0700`. Keep the certificate and encrypted key on GTR and back them up securely.
A new GTR signing certificate was created for 0.4.0 at the author's request;
older released packages retain their existing signatures. For the initial
Marketplace token, log in using your browser
and open [My Tokens](https://plugins.jetbrains.com/author/me/tokens).
Enter the password and token in a local interactive terminal without echo:

```bash
umask 077
mkdir -p ~/.config/idea-env-switcher/signing
chmod 700 ~/.config/idea-env-switcher ~/.config/idea-env-switcher/signing
read -rsp 'Signing key password: ' release_password; printf '\n'
printf '%s' "$release_password" > ~/.config/idea-env-switcher/signing/private-key-password
unset release_password
read -rsp 'Marketplace token: ' release_token; printf '\n'
printf '%s' "$release_token" > ~/.config/idea-env-switcher/marketplace.token
unset release_token
# Secure the signing files and Marketplace token:
chmod 600 ~/.config/idea-env-switcher/signing/* ~/.config/idea-env-switcher/marketplace.token
```

GitHub authentication uses `gh auth status` and the existing CLI credential
store. Secrets are passed only to the signing subprocess or the Marketplace
HTTP request; they are never command arguments or release metadata. Gradle
signing disables the daemon, build cache, and configuration cache.

`prepare` records the commit, certificate, ZIP, and every runtime file digest
in ignored `build/release/prepared.json`. `publish` checks them again and asks
for the exact version and both destinations in the current terminal before
writing. Already published matching packages need no signing secrets and cause
zero write requests; a submitted Marketplace update is not uploaded again
while awaiting approval. Differing runtime content stops publication.

A repository lock prevents concurrent preparation or publication. An uncertain
upload leaves an operation marker under `.git/`. The script reads
the remote state once and never retries the write automatically. If it cannot
confirm the result, inspect the release/update before resolving that marker.
The existing 0.3.2 packages were built on Mac: a GTR rebuild has different
`Build-OS`/`Build-JVM` manifest fields, so it must not replace those packages.

Release safety tests: `python3 -m unittest discover -s scripts/tests -v`.
Python 3 is required; all Python dependencies are in the standard library.

## Roadmap

See [CHANGELOG.md](CHANGELOG.md) (Unreleased) and open
[enhancement issues](https://github.com/SongYuanKun/idea-env-switcher/issues?q=is%3Aissue+label%3Aenhancement).

The settings editor and local Python runner support shipped in 0.4.0.
Runner support boundaries are described above; further coverage can be tracked
in enhancement issues.

## Non-commercial / open source statement

Env Switcher is published solely as free open source software for the community.
There is no commercial product, paid tier, or company sponsorship tied to day-to-day
development. One-time voluntary donations for infrastructure (if any) do not change
the free nature of the software.

## License

Copyright 2026 SongYuanKun

Licensed under the Apache License, Version 2.0. See [LICENSE](LICENSE).
