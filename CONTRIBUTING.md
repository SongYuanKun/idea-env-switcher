# Contributing to Env Switcher

Thanks for your interest in contributing. This is a **non-commercial, volunteer-maintained**
open source IntelliJ IDEA plugin under the [Apache License 2.0](LICENSE).

Please also read our [Code of Conduct](CODE_OF_CONDUCT.md).

## Development setup

- JDK **21+**
- IntelliJ IDEA 2024.3+ (Community or Ultimate)
- Clone the repository and open it in IDEA; wait for Gradle sync

Useful commands:

```bash
./gradlew test
./gradlew runIde
./gradlew buildPlugin
```

Plugin zip output: `build/distributions/idea-env-switcher-<version>.zip`

## How to contribute

1. Search [existing issues](https://github.com/SongYuanKun/idea-env-switcher/issues) before opening a new one.
2. For larger features, open an issue first so we can align on scope.
3. Fork the repo, create a topic branch from `main`.
4. Keep PRs focused (one concern per PR).
5. Add or update tests when changing parsing, file I/O, or other non-trivial logic.
6. Update [CHANGELOG.md](CHANGELOG.md) under **Unreleased** when user-visible behavior changes.
7. Run `./gradlew test` before requesting review.

## Coding notes

- Prefer clear, small Java classes over large “god” classes.
- Comments in code may be concise Chinese; user-facing strings go through the resource bundle.
- Do not commit `.env`, secrets, local paths, or IDE sandbox outputs (`.intellijPlatform/`).

## Commit / PR style

- Commit messages: short imperative summary (e.g. `Add workspace persistence for last profile`).
- Link related issues in the PR description (`Fixes #123`).

## Security

See [SECURITY.md](SECURITY.md) for private vulnerability reporting. Do not disclose security
issues in public issues until a fix is available.

## License of contributions

By submitting a contribution, you agree that your work is provided under the same
Apache License 2.0 terms as this project, and that you have the right to submit it.
