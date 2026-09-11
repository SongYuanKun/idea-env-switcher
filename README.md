# Env Switcher

[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE)
[![Build](https://github.com/SongYuanKun/idea-env-switcher/actions/workflows/build.yml/badge.svg)](https://github.com/SongYuanKun/idea-env-switcher/actions/workflows/build.yml)
[![Release](https://img.shields.io/github/v/release/SongYuanKun/idea-env-switcher)](https://github.com/SongYuanKun/idea-env-switcher/releases)

**Env Switcher** is a free, **non-commercial** open source plugin for IntelliJ IDEA.
It lets you switch named environment profiles (dev / staging / prod …) with one click.

This project is volunteer-maintained. It does **not** offer paid support, consulting, or commercial editions.

- **License (OSI)**: [Apache License 2.0](https://github.com/SongYuanKun/idea-env-switcher/blob/main/LICENSE)
- **Latest plugin zip**: [GitHub Releases](https://github.com/SongYuanKun/idea-env-switcher/releases)
- **Code of Conduct**: [CODE_OF_CONDUCT.md](CODE_OF_CONDUCT.md)
- **Contributing**: [CONTRIBUTING.md](CONTRIBUTING.md)
- **Security**: [SECURITY.md](SECURITY.md)
- **Changelog**: [CHANGELOG.md](CHANGELOG.md)

## Features

- Load profiles from project-root `env-profiles.json`
- **Tools → Switch Environment** (shortcut `Ctrl+Alt+E`)
- Write the active profile into a generated `.env` file
- Status bar widget for the current profile (click to switch again)
- **Tools → Reload Environment Profiles**

## Install

1. Download `idea-env-switcher-*.zip` from [Releases](https://github.com/SongYuanKun/idea-env-switcher/releases).
2. IntelliJ IDEA → **Settings → Plugins → ⚙️ → Install Plugin from Disk…**
3. Restart the IDE if prompted.

Requires **IntelliJ IDEA 2024.3+** (`sinceBuild=243`).

## Quick start

1. Copy [examples/env-profiles.json](examples/env-profiles.json) to your project root as `env-profiles.json` and edit it.
2. **Tools → Switch Environment**, pick a profile.
3. Check that `.env` was generated/updated and the status bar shows `Env: <name>`.

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

## Develop from source

```bash
export JAVA_HOME=/path/to/jdk-21
./gradlew test
./gradlew runIde
./gradlew buildPlugin
```

Artifact: `build/distributions/idea-env-switcher-<version>.zip`

## Roadmap

See [CHANGELOG.md](CHANGELOG.md) (Unreleased) and open
[enhancement issues](https://github.com/SongYuanKun/idea-env-switcher/issues?q=is%3Aissue+label%3Aenhancement).

Planned after 0.1:

- Persist last selected profile
- Inject variables into Run Configurations
- Settings UI for profile editing
- JetBrains Marketplace publication

## Non-commercial / open source statement

Env Switcher is published solely as free open source software for the community.
There is no commercial product, paid tier, or company sponsorship tied to day-to-day
development. One-time voluntary donations for infrastructure (if any) do not change
the free nature of the software.

## License

Copyright 2026 SongYuanKun

Licensed under the Apache License, Version 2.0. See [LICENSE](LICENSE).
