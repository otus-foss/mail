# Quality gate

Meta's [quality guide](https://github.com/reazndev/otus-meta/blob/main/docs/quality.md) describes the commands, the convention plugins, the
shared tools and rules, file-length exceptions, commit messages, and coverage for every Otus
repository. Mail runs them from its otus-meta checkout; see [shared libraries](shared-libraries.md).
This guide covers what only Mail adds.

## Commands

Requires JDK 21, Python 3.11+, the Android SDK, and an otus-meta checkout at `../otus-meta` or at
`otus.meta.path`. One-time setup:

```sh
python3 -m venv .venv
.venv/bin/python -m pip install -r requirements-quality.txt
git config --local core.hooksPath .githooks
```

`requirements-quality.txt` also installs the APNs bridge's packages, so `pushBridgeTest` can run.

During development, batch edits and run the linters plus the test classes affected by the change:

```sh
./gradlew spotlessCheck detekt ruffCheck ruffFormatCheck
./gradlew :androidApp:testDebugUnitTest --tests 'com.otus.mail.FilterPanelScreenshotTest'
```

Use `:shared:testAndroidHostTest --tests '<fully qualified class>'` for shared tests.

| Command | Scope |
| --- | --- |
| `checkTooling` | `qualityCheck` and the APNs bridge tests |
| `checkAndroidBuild` | Android lint and debug assembly |
| `checkTests` | Shared's host tests and the app's unit tests, screenshot tests included |
| `checkAll` | all three |

`architectureTest` runs Mail's own tool tests in `tools/tests`: the nightly version and release
notes, and that only the signing step gets the nightly key.

## iOS

Swift formatting and lint and the full iOS app build need macOS; they are not part of `formatAll`
or `checkAll`. On macOS install `nicklockwood/SwiftFormat@0.63.1` and `realm/SwiftLint@0.65.1`
via Mint, then run:

```sh
mint run swiftformat iosApp
mint run swiftformat --lint iosApp
mint run swiftlint lint --strict
```

SwiftFormat uses `--commas inline` to match SwiftLint's trailing-comma rule. SwiftLint's
`file_length` rule is off, because Meta's architecture check enforces the Swift limit.

Shared Kotlin iOS source compilation can also run on Linux with
`./gradlew :shared:compileKotlinIosSimulatorArm64 :shared:compileKotlinIosArm64`.
This checks the Kotlin sources and platform bindings; it does not link or run the Xcode app.

## CI

The `quality` workflow's `check` job runs `checkAll` as four parallel jobs: `checkTooling`,
`checkAndroidBuild`, and `checkTests` in two shards that split the test classes by name. A fifth
job builds the release APK, and the `apk-size` job comments its size against the latest nightly.
The `ios` job runs Swift formatting and lint plus a simulator build on macOS 15 with Xcode 26.3;
Compose Multiplatform links symbols from the iOS 26 SDK, so Xcode 16 cannot link the app. The
simulator build verifies compilation without device signing and targets `arm64`, matching the
configured Apple Silicon simulator framework. JMAP iOS storage tests live in otus-meta as
`:jmap:iosSimulatorArm64Test`. See [hosting and CI](hosting.md).

## Test timings

On 2026-10-26, the 83 tracked Android test classes took 83.3 seconds with one worker, 77.9 seconds
with two, and 114.1 seconds with three on the shared machine with eight logical CPUs. Peak combined
test-worker RSS was 1.7, 2.8, and 3.8 GiB respectively. These are individual runs with queue time
excluded, not a CI benchmark. Keep the two-worker default. A test task also tracks its runtime
classpath, so editing application code or a dependency can legitimately rerun the entire UI test
task.

## Coverage

```sh
./gradlew koverXmlReportCoverage koverHtmlReportCoverage
python3 ../otus-meta/tools/coverage_report.py build/reports/kover/reportCoverage.xml --base origin/main
```

The `coverage` variant runs Shared's host tests and the app's debug unit tests once each. The app's
tests include the Robolectric and Roborazzi screenshot tests, so screens they render count. Kover's
default `koverHtmlReport` also runs the app's tests again for every other build type. The reports
go to `build/reports/kover/htmlCoverage/` and `build/reports/kover/reportCoverage.xml`.

## Context-panel tests

Run `./gradlew :androidApp:testDebugUnitTest` for local Android UI checks. Native-graphics
screenshots are written to `androidApp/build/reports/context-panel/`; physical-device testing is
still required. Inbox matching tests run via `:shared:testAndroidHostTest`, also included in
`checkAll` and CI. Inbox UI screenshots are written to `androidApp/build/reports/inbox/`.
