# Otus Mail developer guide

Development, pull requests, and issues use
[mail on GitHub](https://github.com/otus-foss/mail). See [hosting and CI](hosting.md).

Kotlin Multiplatform + Compose app for Android and iOS, intended as the mail client in the Otus JMAP
suite. Mail starts with an empty inbox and working local search/filter UI over supplied messages.
No sample mail is shipped. JMAP account connection, a cached inbox kept in sync with the server,
and sending are implemented; see [storage](storage.md) and [compose](compose.md).
Calendar operations remain unimplemented. Read-only JMAP contact-photo lookup is available for
sender avatars. Folders (JMAP mailboxes) can
be browsed and managed, and Sieve rules (RFC 9661) file arriving mail into them on servers that
support it; see [Folders and rules](folders.md).
See [JMAP](jmap.md) for supported behavior and test coverage.

## Structure

- `shared`: common Kotlin code and Compose UI, with platform integrations.
- `androidApp`: Android application entry point.
- `iosApp`: Xcode project hosting the shared Compose UI.

Application and bundle ID: `com.otusfoss.mail` (verify ownership before publishing). On Android, nightly
builds add `.nightly` and dev and debug builds add `.dev`, so each channel installs beside the others.

## Android

Android is the primary platform; iOS remains a secondary target.

Use JDK 21 and Android Studio with the Kotlin Multiplatform plugin. Install Android SDK 37 and set
`ANDROID_HOME` or add `sdk.dir=/your/android/sdk` to untracked `local.properties`. Open this
directory in Android Studio and run `androidApp`.

The build compiles the shared Otus libraries from an core checkout next to this one,
`../core`, or from the checkout the `otus.core.path` Gradle property names. See
[Shared libraries](shared-libraries.md).

```sh
./gradlew :androidApp:assembleDebug
```

For performance checks, build an optimized release with `./gradlew :androidApp:assembleRelease`.
Release enables R8 and resource shrinking; its APK is unsigned until local or production signing
is supplied. Debug builds retain debugging support and can scroll much more slowly on devices
without JIT compilation. See [the calendar comparison](selection-sheets.md#pixel-scroll-comparison-2026-10-03)
for measured results and test limitations.

## iOS

On macOS, install Xcode 26 or newer and JDK 21. Open `iosApp/iosApp.xcodeproj`, select an iPhone simulator and
run. For a physical device, set `TEAM_ID` in `iosApp/Configuration/Config.xcconfig` and configure
signing. Xcode builds the shared Kotlin framework automatically. Targets: iOS ARM64 devices and
Apple Silicon simulators, iOS 15.3+.

See [Apple push delivery](apple-push.md) for APNs bridge deployment, signing, and notification tests.

## Toolchain and attribution

Versions are pinned in core's `gradle/libs.versions.toml`, the suite catalog, and in
`gradle/app.versions.toml` for what only Mail uses. The Gradle wrapper includes a distribution
checksum. Derived from [Kotlin/KMP-App-Template](https://github.com/Kotlin/KMP-App-Template) at
commit `6b6d09e4f3f5e845116422d59b658791ed6149c6`; its Apache 2.0 license is retained in `LICENSES/Apache-2.0-KMP-App-Template.txt`.
Sample museum code and unrelated platform targets were removed.

## Engineering policy

See [date formatting](date-formatting.md) for device-locale defaults, saved format choices,
and the reusable API for future settings and screens.

Read [AGENTS.md](../AGENTS.md), [architecture](architecture.md), and
[quality checks](quality.md). UI follows elements → components → screens. Install the tools in the quality guide and
run `./gradlew checkAll` before submitting changes. Use `./gradlew formatAll` to format.

## Commit messages

Use `type(scope): short action`, a blank line, and 1–8 bullets explaining the changes:

```text
feat(inbox): add avatars

- Show contact photos beside inbox senders.
- Fall back to initials when a photo is unavailable.
```

Use lowercase types and scopes, start the subject with an imperative verb, and aim for a title
of at most 72 characters without a final period. Apply the unslop skill to the entire message
when available. Keep the wording plain and describe only changes in the commit.

Enable the versioned Git hooks once per checkout, including new clones. `pre-commit` checks
formatting, and `commit-msg` checks the message with core's checker. Run this from the
repository root with Python 3.11+ installed:

```sh
git config --local core.hooksPath .githooks
```

The `commit-msg` hook rejects invalid title syntax, missing separators, and bodies outside the 1–8 bullet
limit. Review wording and the title-length target manually. Git-generated merge messages and
temporary autosquash messages are exempt; final squash messages follow the rules. core's CI
tests the checker; nothing inspects commit history or installs local hooks.
See [the full commit rules](../AGENTS.md#commit-messages) and
[hook details](https://github.com/otus-foss/core/blob/main/docs/quality.md#commit-messages).

## Inbox and reusable controls

See [Inbox](inbox.md) for the default screen and search scope, and
[Action bar](action-bar.md) for the open message page, selection, and configurable actions, and
[Compose](compose.md) for new messages, replies, forwards, and drafts, and
[Context panel](context-panel.md) for the reusable API and component test playground, and
[Sidebar](sidebar.md) for page navigation, [Notifications](notifications.md) for instant
delivery and the new-mail check, and [Settings](settings.md) for subpages and
JMAP account setup, and [JMAP](jmap.md) for the protocol client.

See [sender and attendee avatars](avatars.md) for the reusable contact-photo, optional domain-icon,
and generated-avatar service.

Core is the central owner of visual elements, reusable components, tokens, and symbols.
Mail retains feature screens and adapters. Message rows use Core's `CompactContentRow`, empty
states `EmptyState`, and all symbols and tokens come from `designsystem`.
