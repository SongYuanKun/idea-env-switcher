# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added
- Headless GTR release tooling with signed package verification, publication checks, and duplicate-upload prevention

### Changed
- Document Marketplace installation and remove the completed publication TODO
- Pin the Gradle 9.0.0 distribution checksum

### Planned
- Settings UI for profile management
- Broader runner coverage beyond Java Run Configurations

## [0.3.2] - 2026-09-28

### Fixed
- Replace deprecated `DynamicBundle(String)` usage with the supported bundle instance API

## [0.3.1] - 2026-09-16

### Fixed
- Show the environment chooser in the current project window when no focused editor or context component is available
- Refresh generated `.env` files asynchronously so environment switching is safe on the event dispatch thread
- Reload profiles after project startup before restoring the last selected environment

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

[Unreleased]: https://github.com/SongYuanKun/idea-env-switcher/compare/v0.3.2...HEAD
[0.3.2]: https://github.com/SongYuanKun/idea-env-switcher/compare/v0.3.1...v0.3.2
[0.3.1]: https://github.com/SongYuanKun/idea-env-switcher/compare/v0.3.0...v0.3.1
[0.3.0]: https://github.com/SongYuanKun/idea-env-switcher/releases/tag/v0.3.0
[0.2.0]: https://github.com/SongYuanKun/idea-env-switcher/releases/tag/v0.2.0
[0.1.0]: https://github.com/SongYuanKun/idea-env-switcher/releases/tag/v0.1.0
