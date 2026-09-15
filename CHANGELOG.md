# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Planned
- Settings UI for profile management
- JetBrains Marketplace publication
- Broader runner coverage beyond Java Run Configurations

## [0.3.0] - 2026-09-15

### Added
- Inject the active profile into Java Run Configurations at runtime (does not rewrite saved configs)

## [0.2.0] - 2026-09-11

### Added
- Persist last selected profile in the IDE workspace
- Restore the last profile (and rewrite `.env`) when the project opens

## [0.1.0] - 2026-09-11

### Added
- Load environment profiles from project-root `env-profiles.json`
- **Tools → Switch Environment** (`Ctrl+Alt+E`)
- Write active profile to generated `.env`
- Status bar widget showing the current profile
- **Tools → Reload Environment Profiles**
- Example config under `examples/env-profiles.json`
- Unit tests for profile JSON parsing

[Unreleased]: https://github.com/SongYuanKun/idea-env-switcher/compare/v0.3.0...HEAD
[0.3.0]: https://github.com/SongYuanKun/idea-env-switcher/releases/tag/v0.3.0
[0.2.0]: https://github.com/SongYuanKun/idea-env-switcher/releases/tag/v0.2.0
[0.1.0]: https://github.com/SongYuanKun/idea-env-switcher/releases/tag/v0.1.0
