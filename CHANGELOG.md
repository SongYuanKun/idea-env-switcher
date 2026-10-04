# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

## [0.5.0] - 2026-10-04

### Added
- Copy an environment profile with its staged description and variables, an independent copy, and automatic unique naming
- Import UTF-8 `.env` files as new profiles in the settings editor; review changes before Apply or discard them with Reset
- Parse dotenv comments, optional `export`, quoted and multiline values, and common double-quoted escapes while preserving variable references as literal text
- Report invalid imports with line numbers while preserving existing drafts and keeping variable values out of diagnostics

### Fixed
- Accept current opaque Marketplace tokens as well as legacy permanent tokens in the GTR release tooling

## [0.4.0] - 2026-10-02

### Added
- Project Settings UI for adding, editing, renaming and removing profiles and variables
- Tools → Manage Environment Profiles shortcut to the settings editor
- Optional runtime environment injection for local Python Run Configurations with Python Community installed
- Headless GTR release tooling with signed package verification, publication checks, and duplicate-upload prevention

### Changed
- Document Marketplace installation and remove the completed publication TODO
- Pin the Gradle 9.0.0 distribution checksum
- Replace deprecated Node.js 20 CI actions with verified Node.js 24 releases and pin the Ubuntu runner

### Fixed
- Reject invalid profiles and duplicate names before saving
- Prevent overwriting changes made outside the settings editor
- Preserve working profiles after a failed reload, synchronize the active environment after reloading, and clear a missing profile selection
- Show a warning for invalid profiles during project startup instead of failing the startup activity
- Read the active profile atomically during runtime injection to avoid a concurrent-save gap
- Refresh the selected profile and generated `.env` after applying settings; preserve manual `.env` edits when deleting the active profile

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

[Unreleased]: https://github.com/SongYuanKun/idea-env-switcher/compare/v0.5.0...HEAD
[0.5.0]: https://github.com/SongYuanKun/idea-env-switcher/compare/v0.4.0...v0.5.0
[0.4.0]: https://github.com/SongYuanKun/idea-env-switcher/compare/v0.3.2...v0.4.0
[0.3.2]: https://github.com/SongYuanKun/idea-env-switcher/compare/v0.3.1...v0.3.2
[0.3.1]: https://github.com/SongYuanKun/idea-env-switcher/compare/v0.3.0...v0.3.1
[0.3.0]: https://github.com/SongYuanKun/idea-env-switcher/releases/tag/v0.3.0
[0.2.0]: https://github.com/SongYuanKun/idea-env-switcher/releases/tag/v0.2.0
[0.1.0]: https://github.com/SongYuanKun/idea-env-switcher/releases/tag/v0.1.0
