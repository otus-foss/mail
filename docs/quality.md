# Quality gate

## Commands

Requires JDK 21 and Python 3.11+, plus the Android SDK for Android checks.
No Node.js or npm installation is required.

One-time Python tooling setup:

```sh
python3 -m venv .venv
.venv/bin/python -m pip install -r requirements-quality.txt
```

To build against an unreleased otus-meta checkout, set
`GRADLE_OPTS=-Dorg.gradle.project.otus.meta.path=../otus-meta`.

During development, batch edits and run the linters plus the test classes affected by the change:

```sh
./gradlew spotlessCheck detekt ruffCheck ruffFormatCheck
./gradlew :androidApp:testDebugUnitTest --tests 'com.otus.mail.FilterPanelScreenshotTest'
```

Use `:shared:testAndroidHostTest --tests '<fully qualified class>'` for shared tests. Use
`qualityCheck` when architecture and tooling checks are also needed. Run `formatAll` when files
need formatting; it cannot fix every static-analysis violation. Run `checkAll` once after the
final batch, and repeat it only when later changes or failures require another full check.
Gradle uses the repository's Python virtual environment when present. A linked worktree without
one uses the main checkout's `.venv`.

| Command | Scope |
| --- | --- |
| `formatAll` | Spotless/ktlint Kotlin and Gradle formatting, project text whitespace, Ruff fixes/formatting |
| `qualityCheck` | Spotless, detekt, architecture/file-length checks and regression tests, Ruff lint/format |
| `checkAll` | Everything in qualityCheck, Android lint, debug assembly, domain and JMAP host tests, local Android UI tests, and APNs bridge tests |

Project text formatting covers Markdown, YAML, JSON, XML/plist, and TOML with trailing-whitespace
removal and a final newline. It does not enforce full syntax-specific formatting or Markdown
structure. See decision 0002 for the deliberate removal of the JavaScript tooling stack.
Generated Gradle wrappers, Xcode project files, caches, and build output are excluded.

Test code gets ktlint formatting only. Android lint (`ignoreTestSources`) and detekt skip test
source sets, because analyzing them took about a third of lint's time and found little.

SwiftFormat uses `--commas inline` to match SwiftLint's trailing-comma rule.

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

**CI runs once per pull request,** on Ubicloud runners. The `quality` workflow starts when a pull
request that is not a draft opens, or when a draft is marked ready for review. Later pushes do not
start it again, so open pull requests as drafts and mark them ready once every commit is pushed.
Start it by hand from GitHub Actions to check again.

Meta publishing is paused, so Mail's `check` job builds against Meta's source: the Meta branch
with the pull request's branch name when one exists, and `main` otherwise.

The CI `quality` workflow runs on pull requests that change more than Markdown. Its `check` job runs
`checkAll` as four parallel jobs: `checkTooling`, `checkAndroidBuild`, and `checkTests` in two
shards that split the test classes by name. Its `ios` job runs Swift formatting/lint plus a simulator build. The iOS job runs on macOS 15 with Xcode 26.3; Compose
Multiplatform links symbols from the iOS 26 SDK, so Xcode 16 cannot link the app. The simulator
build verifies compilation without device signing and targets `arm64`, matching the configured Apple Silicon simulator framework.
JMAP iOS storage tests live in otus-meta as `:jmap:iosSimulatorArm64Test`. Meta's publish job
does not run them; Meta's macOS `ios` job runs them on each ready pull request in Meta.
Android setup installs `platform-tools`
and the pinned platform/build-tools packages rather than the removed legacy `tools` package.

## Profiling and cache reuse

Use `./gradlew checkAll --profile --console=plain` to write task timings to
`build/reports/profile/`. Compare it with an unchanged repeat. To measure actual UI test execution
without forcing compilation and lint to rerun, use
`./gradlew :androidApp:testDebugUnitTest --rerun --profile --console=plain`.
Use `--info` to see why a task ran or whether it restored outputs from the build cache.

Record queue waits separately. On the shared machine, the build queue waits inside a task action,
so Gradle can charge waiting time to an otherwise cheap task. Test timings also include JVM,
Robolectric, and native-renderer startup. Compare the same source revisions and test selection.

Screenshot baselines are task inputs with relative path sensitivity. Their names and contents
must invalidate verification when they change; the checkout's absolute path must not. Keep these
inputs when changing the test configuration. A test task also tracks its runtime classpath, so
editing application code or a dependency can legitimately rerun the entire UI test task.
Recording with `-Proborazzi.record` always executes and does not cache test results, because
baseline PNGs are written outside the test task's declared output directories.

On 2026-10-26, the 83 tracked Android test classes took 83.3 seconds with one worker, 77.9 seconds
with two, and 114.1 seconds with three on the shared machine with eight logical CPUs. Peak combined test-worker
RSS was 1.7, 2.8, and 3.8 GiB respectively. These are individual runs with queue time excluded,
not a CI benchmark. Keep the two-worker default. After the relative-path fix, a fresh checkout
restored the same full test result from cache in 0.17 seconds.

## Coverage

Kover measures line and branch coverage. It reports numbers but does not fail any build.
Tests run without coverage instrumentation unless the Gradle command names a Kover task, so
`checkAll` does not pay for it.

```sh
./gradlew koverXmlReportCoverage koverHtmlReportCoverage
python3 tools/coverage_report.py build/reports/kover/reportCoverage.xml --base origin/main
```

The `coverage` variant runs Shared's host tests and the app's debug unit tests once each. The app's
tests include the Robolectric and Roborazzi screenshot tests, so screens they render count. Kover's
default `koverHtmlReport` also runs the app's tests again for every other build type.
The reports go to `build/reports/kover/htmlCoverage/` and `build/reports/kover/reportCoverage.xml`.

The `coverage` workflow runs these tasks on the same events as `quality`: a ready pull request,
later pushes while it has the `ci on every push` label, pushes to `main`, and runs by hand. It
writes the summary to the run page, uploads the HTML and XML reports as the `coverage-report`
artifact, and on a pull request keeps one comment up to date. A separate job with only
`pull-requests: write` posts that comment. Low coverage never fails the run; a failing test does,
and then there is no report.

`tools/coverage_report.py` writes the summary. It counts only handwritten main Kotlin sources, so
generated code such as SQLDelight queries and Compose resources is left out.

- **Totals**: covered and total lines, branches, instructions, methods, and classes.
- **This pull request**: coverage of the lines the pull request adds or changes in main Kotlin
  sources, from `git diff -U0` against the merge base. Lines with no bytecode, such as comments,
  blank lines, and most declarations, do not count. A table lists each touched file with its
  uncovered changed lines, and a list names touched composables that no test renders.
- **UI**: how many public composables in `presentation/screens` and `presentation/components` a
  test renders. A composable counts as rendered when any code in its compiled function ran.
  Private helpers and previews do not count. Screens that no test renders are listed.
- **By module** and **By layer**: line coverage per Gradle module, and for `app`, `core`, and each
  feature's `domain`, `data`, and `presentation` screens and components.
- **Least-covered files**: the 15 files with the most uncovered lines.

Limits:

- Only code that runs on the JVM counts. iOS tests and `iosMain` do not.
- Composables are matched to compiled methods by name, allowing for the suffixes Kotlin adds for
  value-class parameters and default arguments. Code inside composable and inline lambdas
  compiles to separate methods, so it counts toward lines but not toward whether a composable
  rendered. Inlined code counts where it is called.
- A composable whose name the scanner cannot find in the bytecode is reported as not found
  rather than as unrendered.

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
file-length exception above is permitted. The versioned Git hooks
are the main gate. `pre-commit` runs the Spotless and Ruff checks on the working tree, so stash
unrelated changes first. No hook runs `checkAll`, because a full build on every push is too
heavy for a laptop. Run `./gradlew checkAll` before pushing work that needs it, such as a direct
push to `main`. `git commit --no-verify` skips the hooks; do not use it to get past a failing
check.

CI runs `checkAll` once per ready pull request that changes more than Markdown, to save runner
minutes.
The repositories are private on a GitHub plan that cannot require checks, so CI reports results
but cannot block a merge. See [suite decision 0013](https://github.com/reazndev/otus-meta/blob/main/docs/decisions/0013-local-quality-gate.md).

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

The hook also removes AI attribution lines, such as `Co-authored-by` trailers that name Claude,
Anthropic, or Cursor and "Generated with Claude Code" lines, before Git saves the commit.

`checkAll`, and so CI, runs the checker's regression tests, including Git rejection in a
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

Inbox matching tests run via `:shared:testAndroidHostTest`, also included in `checkAll` and CI.
Inbox UI screenshots are written to `androidApp/build/reports/inbox/`.

## Runners

Linux jobs run on Ubicloud's `ubicloud-standard-2` runners. The `ios` job runs on GitHub's
`macos-15` runners, because Ubicloud has no macOS runners. See [hosting and CI](hosting.md).
