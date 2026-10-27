# Shared libraries

Code that Mail and Calendar both use lives in
[otus-meta](https://github.com/reazndev/otus-meta) and arrives here as a pinned release. Mail
uses:

| Library | Version catalog entry |
| --- | --- |
| `com.otus:jmap` | `libs.otus.jmap` |
| `com.otus:avatar` | `libs.otus.avatar` |
| `com.otus:dateformat` | `libs.otus.dateformat` |
| `com.otus:designsystem` | `libs.otus.designsystem` |
| `com.otus:settings` | `libs.otus.settings` |
| `com.otus:accounts` | `libs.otus.accounts` |
| `com.otus:jmap-push` | `libs.otus.jmap.push` |
| `com.otus:jscontact` | `libs.otus.jscontact` |
| `com.otus:jmap-contacts` | `libs.otus.jmap.contacts` |

Mail pins Meta 0.10.0 for all Otus libraries. While Meta publishing is paused, builds use Meta's
source through `otus.meta.path`, as [Changing shared code](#changing-shared-code) describes.
See [the component inventory](component-inventory.md) for historical ownership decisions.

All otus-meta libraries share one version, `otus-meta` in `gradle/libs.versions.toml`.

## Token setup

The release registry is `https://maven.pkg.github.com/reazndev/otus-meta`. `settings.gradle.kts`
declares it as exclusive content for the `com.otus` group, so Gradle never resolves a `com.otus`
artifact from Maven Central or Google. GitHub Packages needs a token for every download. Create a classic personal access token with the `read:packages` scope.

On a development machine, add credentials to `~/.gradle/gradle.properties`, never to this repository:

```properties
otusMetaUsername=<GitHub username>
otusMetaPassword=<GitHub token with read:packages>
```

In Mail's GitHub Actions settings, store the token as the `OTUS_META_PACKAGES_TOKEN` secret.
SSH Git credentials do not authenticate Maven downloads.

## Package status

Meta publishing is paused. Tags do not upload packages, and manual publisher runs validate without
uploading. The release flow needs to resume before 0.10.0 becomes a downloadable version.
Mail's quality workflow uses a Meta branch with the same name as the branch it checks when one exists,
otherwise Meta main. Versions 0.3.1 to 0.5.0 are not on GitHub Packages.
iOS linking against the artifacts is unverified while the macOS `ios` job is paused.
See [hosting and CI](hosting.md).

## Design-system package migration

Meta keeps one `designsystem` artifact. Its UI now lives in
`com.otus.designsystem.shared.<group>.<elements|components>` and
`com.otus.designsystem.mail.<feature>.<elements|components>`. Update the imports using
[Meta's declaration map](https://github.com/reazndev/otus-meta/blob/main/docs/ui-package-migration.md).
Theme tokens, resource identifiers, and the app's own feature layers are unchanged.

The nightly builds Mail main against Meta main, so merge a Meta package move and Mail's import
update one after the other, away from the nightly's 00:00 UTC run.

## Changing shared code

1. Change the library in otus-meta.
2. Try it here with `./gradlew checkAll -Potus.meta.path=../otus-meta`. Gradle then builds the
   library from that checkout instead of downloading it.
3. Release the library from otus-meta.
4. Update `otus-meta` in `gradle/libs.versions.toml` and run `./gradlew checkAll` without the
   property.

`main` must always build from a released version. See
[otus-meta's consuming guide](https://github.com/reazndev/otus-meta/blob/main/docs/consuming.md)
for details.

Meta is the central owner of visual elements, reusable components, tokens, and symbols.
Mail retains feature screens and adapters. Message, folder, and rule rows and empty states
use shared `ContentRow`, `DetailRow`, and `EmptyState`; all symbols and `InboxTokens` come
from `designsystem`. These additions are included in Meta 0.3.0.

## Checking a release candidate without source substitution

Publish Meta into a temporary Maven repository from its own checkout:

```sh
./gradlew publishToMavenLocal -Dmaven.repo.local=/tmp/otus-meta-candidate
```

Then run Mail against those artifacts from this checkout:

```sh
./gradlew checkAll -Potus.meta.repository=file:///tmp/otus-meta-candidate
./gradlew :shared:compileKotlinIosSimulatorArm64 :shared:compileKotlinIosArm64 -Potus.meta.repository=file:///tmp/otus-meta-candidate
```

`otus.meta.repository` is an explicit development override. It resolves only `com.otus` from
that repository and disables composite substitution, even if `otus.meta.path` is set in your
machine's Gradle properties. Other dependencies keep their existing repositories. No remote
package credentials are required for a file repository. The pinned version still applies.

This checks artifact metadata, transitive dependencies, compilation, and Android resource
packaging independently of a sibling source build. Kotlin iOS compilation on Linux does not
verify Xcode linking or simulator resource loading. After the remote release, repeat the build
without either override to verify GitHub Packages access and the released artifacts.
