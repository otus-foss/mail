![Otus Mail](.github/banner.png)

[![Latest nightly](https://img.shields.io/github/v/release/otus-foss/mail?include_prereleases&sort=date&display_name=release&label=latest&color=5a7ea4)](https://github.com/otus-foss/mail/releases)

Otus Mail is a mail app for Android and iOS. It uses JMAP with the server you choose, such as a
self-hosted Stalwart. It is part of the Otus suite with
[Otus Calendar](https://github.com/otus-foss/calendar), built on [Otus Core](https://github.com/otus-foss/core).

> [!WARNING]
> Otus Mail is still in development. Expect bugs, missing features, and changes that break you and your
> settings between builds.

## Install

On Android, download the APK from the [latest nightly](https://github.com/otus-foss/mail/releases)
and open it. The first time, Android asks you to allow installs from your browser or file
manager. After that, the app tells you when a new nightly is out and installs it for you.

There are no iOS builds yet. To run it on an iPhone, [build it from source](#build-from-source).

Then open Settings, choose Email accounts, and connect your JMAP server with your username and
password. Use an app password if your server offers one.

## Build from source

Otus Mail builds Otus Core from a checkout next to this one. With JDK 21 and the Android SDK
installed, run:

```sh
git clone https://github.com/otus-foss/core
git clone https://github.com/otus-foss/mail
cd mail
./gradlew :androidApp:assembleDebug
```

On a Mac, open `iosApp/iosApp.xcodeproj` in Xcode to run it on an iPhone.

## Contributing

Read the [developer guide](docs/README.md) before you change code.

## License

GPL-3.0-or-later, with an [additional permission](LICENSE-EXCEPTION.md) that allows app-store
distribution while the source stays public. See [LICENSE](LICENSE).
