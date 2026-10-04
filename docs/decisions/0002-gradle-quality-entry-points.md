# 0002: Gradle quality commands without npm

The user requested removing npm because these are Kotlin applications. Gradle now owns
formatAll, qualityCheck, and checkAll. Python remains for the existing architecture checks,
their regression tests, and pinned Ruff. Swift tools remain pinned in Mintfile.

Remove the package manifest, lockfile, JavaScript Markdown checker, ESLint, Prettier,
markdownlint, and npm-distributed Taplo. There is no remaining JavaScript tooling to lint.
Spotless retains basic whitespace formatting for documentation and configuration files.
Full Markdown structure checks and language-specific documentation/config formatting are
intentionally no longer enforced. This is a tooling simplification, not a workaround for a
failing check. Kotlin, architecture, file-length, Python, Android, and Swift rules remain.

CI uses Gradle directly and no longer installs Node.js. Each repository is self-contained.
