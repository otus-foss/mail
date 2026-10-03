# Otus Mail

Kotlin Multiplatform + Compose app for Android and iOS, intended as the mail client in the Otus JMAP suite. This initial scaffold includes a welcome screen; JMAP discovery, authentication, synchronization and product features are not implemented yet.

## Structure

- `shared`: common Kotlin code and Compose UI, with platform integrations.
- `androidApp`: Android application entry point.
- `iosApp`: Xcode project hosting the shared Compose UI.

Application and bundle ID: `com.otus.mail` (verify ownership before publishing).

## Android

Use JDK 21 and Android Studio with the Kotlin Multiplatform plugin. Install Android SDK 37 and set `ANDROID_HOME` or add `sdk.dir=/your/android/sdk` to untracked `local.properties`. Open this directory in Android Studio and run `androidApp`.

```sh
./gradlew :androidApp:assembleDebug
```

## iOS

On macOS, install Xcode and JDK 21. Open `iosApp/iosApp.xcodeproj`, select an iPhone simulator and run. For a physical device, set `TEAM_ID` in `iosApp/Configuration/Config.xcconfig` and configure signing. Xcode builds the shared Kotlin framework automatically. Targets: iOS ARM64 devices and Apple Silicon simulators, iOS 15.3+.

## Toolchain and attribution

Versions are pinned in `gradle/libs.versions.toml`. The Gradle wrapper includes a distribution checksum. Derived from [Kotlin/KMP-App-Template](https://github.com/Kotlin/KMP-App-Template) at commit `6b6d09e4f3f5e845116422d59b658791ed6149c6`; its Apache 2.0 license is retained in `LICENSE`. Sample museum code and unrelated platform targets were removed.
