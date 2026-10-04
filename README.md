# Otus Mail

Kotlin Multiplatform + Compose app for Android and iOS, intended as the mail client in the Otus JMAP
suite. Mail starts with an empty inbox and working local search/filter UI over supplied messages.
No sample mail is shipped. JMAP discovery, authentication, synchronization, and account connection
are not implemented yet.

## Structure

- `shared`: common Kotlin code and Compose UI, with platform integrations.
- `androidApp`: Android application entry point.
- `iosApp`: Xcode project hosting the shared Compose UI.

Application and bundle ID: `com.otus.mail` (verify ownership before publishing).

## Android

Android is the primary platform; iOS remains a secondary target.

Use JDK 21 and Android Studio with the Kotlin Multiplatform plugin. Install Android SDK 37 and set
`ANDROID_HOME` or add `sdk.dir=/your/android/sdk` to untracked `local.properties`. Open this
directory in Android Studio and run `androidApp`.

```sh
./gradlew :androidApp:assembleDebug
```

For performance checks, build an optimized release with `./gradlew :androidApp:assembleRelease`.
Release enables R8 and resource shrinking; its APK is unsigned until local or production signing
is supplied. Debug builds retain debugging support and can scroll much more slowly on devices
without JIT compilation. See [the calendar comparison](docs/selection-sheets.md#pixel-scroll-comparison-2026-10-03)
for measured results and test limitations.

## iOS

On macOS, install Xcode and JDK 21. Open `iosApp/iosApp.xcodeproj`, select an iPhone simulator and
run. For a physical device, set `TEAM_ID` in `iosApp/Configuration/Config.xcconfig` and configure
signing. Xcode builds the shared Kotlin framework automatically. Targets: iOS ARM64 devices and
Apple Silicon simulators, iOS 15.3+.

## Toolchain and attribution

Versions are pinned in `gradle/libs.versions.toml`. The Gradle wrapper includes a distribution
checksum. Derived from [Kotlin/KMP-App-Template](https://github.com/Kotlin/KMP-App-Template) at
commit `6b6d09e4f3f5e845116422d59b658791ed6149c6`; its Apache 2.0 license is retained in `LICENSE`.
Sample museum code and unrelated platform targets were removed.

## Engineering policy

See [date formatting](docs/date-formatting.md) for device-locale defaults, saved format choices,
and the reusable API for future settings and screens.

Read [AGENTS.md](AGENTS.md), [architecture](docs/architecture.md), and
[quality checks](docs/quality.md). UI follows elements → components → screens. Install the tools in the quality guide and
run `./gradlew checkAll` before submitting changes. Use `./gradlew formatAll` to format.

## Inbox and reusable controls

See [Inbox](docs/inbox.md) for the default screen and search scope, and
[Context panel](docs/context-panel.md) for the reusable API and component test playground.
