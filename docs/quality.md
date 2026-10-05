# Quality gate

## Commands

Requires JDK 21 and Python 3.11+, plus the Android SDK for Android checks.
No Node.js or npm installation is required.

One-time Python tooling setup:

```sh
python3 -m venv .venv
.venv/bin/python -m pip install -r requirements-quality.txt
```

Daily commands:

```sh
./gradlew formatAll
./gradlew checkAll
```

Run formatting first, then checks. Formatting cannot fix every static-analysis violation.
Gradle automatically uses the repository's Python virtual environment when present.

| Command | Scope |
| --- | --- |
| `formatAll` | Spotless/ktlint Kotlin and Gradle formatting, project text whitespace, Ruff fixes/formatting |
| `qualityCheck` | Spotless, detekt, architecture/file-length checks and regression tests, Ruff lint/format |
| `checkAll` | Everything in qualityCheck, Android lint, debug assembly, domain and JMAP host tests, and local Android UI tests |

Project text formatting covers Markdown, YAML, JSON, XML/plist, and TOML with trailing-whitespace
removal and a final newline. It does not enforce full syntax-specific formatting or Markdown
structure. See decision 0002 for the deliberate removal of the JavaScript tooling stack.
Generated Gradle wrappers, Xcode project files, caches, and build output are excluded.

Swift formatting/lint and the full iOS app build require the separate macOS workflow below; they are
not included in `formatAll` or `checkAll`.

Shared Kotlin iOS source compilation can also run on Linux with
`./gradlew :shared:compileKotlinIosSimulatorArm64 :shared:compileKotlinIosArm64`.
This checks the Kotlin sources and platform bindings; it does not link or run the Xcode app.

On macOS install `nicklockwood/SwiftFormat@0.63.1` and `realm/SwiftLint@0.65.1` via Mint, then run:

```sh
mint run swiftformat iosApp
mint run swiftformat --lint iosApp
mint run swiftlint lint --strict
```

The CI `quality` workflow runs portable checks, Android lint/debug assembly and local UI tests, and Swift
formatting/lint plus a simulator build. The simulator build verifies compilation without device
signing and targets `arm64`, matching the configured Apple Silicon simulator framework; JMAP iOS
storage tests also run through `:jmap:iosSimulatorArm64Test`. Android setup installs `platform-tools`
and the pinned platform/build-tools packages rather than the removed legacy `tools` package.

## File-length limits and exceptions

Aim for 100–200 lines for most screens and components. The hard limits are **300 lines for Kotlin**
and **200 lines for Swift**, including blank lines and comments. These apply to handwritten app and
test source, not Gradle scripts, generated build output, assets, or documentation. Split by
responsibility before reaching the limit; do not compress code or remove useful comments to satisfy
it.

A rare exception must be the first line of the file, with an explicit higher limit and a concrete
reason of at least 20 characters:

```kotlin
// otus-file-length: 420 -- Keep this protocol mapping together for review.
```

This permits at most 420 total lines in that file. It does not disable architecture, function/class
complexity, formatting, or other lint rules. Missing/short reasons, duplicate or misplaced
directives, and exceptions below the default limit fail. Remove the exception when the file fits the
normal limit; stale exceptions also fail. Explain why splitting would harm clarity in the reason and
review the exception with the change. Do not increase the limit routinely as a file grows.

The architecture checker owns file-length enforcement for both languages. SwiftLint's overlapping
`file_length` rule is disabled so that the same reviewed exception works consistently; its other
rules, including function and type length, remain enabled.

## Enforcement

No lint baselines, blanket warning allowances, or failure-tolerant checks. The narrowly scoped
file-length exception above is permitted. CI evaluates every pull request and push to main.
Repository administrators must require the `portable`, `android`, and `ios` checks on main and
disallow bypasses for merge enforcement; adding workflow files alone does not enable GitHub branch
protection. Local Git hooks are not a security boundary and are not relied upon.

Detekt runs without full application type resolution. Architecture checks inspect source references,
not the whole Kotlin semantic graph. Neither claims proof of design quality. Compiler/build checks
complement the linters. Kotlin source warnings are errors; warnings from third-party Gradle plugins
may still occur.

The independent repositories follow the same quality policy and agent rules. Keep them
aligned when modifying suite policy. No cross-checkout dependency is required to run any gate.

## Commit message review

Enable the versioned `commit-msg` hook once per checkout:

```sh
git config --local core.hooksPath .githooks
```

The hook uses Python 3.11+ to reject invalid title syntax, missing blank separators, and bodies
outside the 1–8 bullet limit. Optional footers follow a blank line after the bullets. Footers
begin with a token such as `Refs: #123`, `Closes #123`, or `BREAKING CHANGE: ...`; multiline
footer text is allowed. Wrapped bullet lines start with at least two spaces. The checker ignores
editor comments according to Git's configured comment character.

Review the message against [the commit rules](../AGENTS.md#commit-messages) for clear wording,
imperative verbs, accurate claims, breaking-change explanations, and the 72-character title target.
Git-generated merge messages are exempt during a merge. Temporary `fixup!`, `squash!`, and
`amend!` messages are exempt; review the final squash message before committing it.

`checkAll` and portable CI run the checker's regression tests, including Git rejection in a
temporary repository. They do not inspect commit history or install local hooks. New clones must
enable the hook with the command above. See [the decision](decisions/0004-commit-messages.md).

## Tool references

- [Spotless](https://github.com/diffplug/spotless/tree/main/plugin-gradle)
- [detekt](https://detekt.dev/docs/1.23.8/introduction/configurations/)
- [SwiftFormat](https://github.com/nicklockwood/SwiftFormat)
- [SwiftLint](https://github.com/realm/SwiftLint)

## Context-panel tests

Run `./gradlew :androidApp:testDebugUnitTest` for local Android UI checks.
Robolectric and Compose test dependencies are pinned in the version catalog. Native-graphics screenshots
are written to `androidApp/build/reports/context-panel/`; physical-device testing is still required.

Inbox matching tests run via `:shared:testAndroidHostTest`, also included in `checkAll` and Android CI.
Inbox UI screenshots are written to `androidApp/build/reports/inbox/`.
