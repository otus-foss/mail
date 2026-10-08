# Shared libraries

Code that Mail and Calendar both use lives in
[otus-meta](https://git.hilfikers.com/florian.ruby/otus-meta) and arrives here as a pinned release. Mail
uses:

| Library | Version catalog entry |
| --- | --- |
| `com.otus:jmap` | `libs.otus.jmap` |
| `com.otus:avatar` | `libs.otus.avatar` |
| `com.otus:dateformat` | `libs.otus.dateformat` |
| `com.otus:designsystem` | `libs.otus.designsystem` |
| `com.otus:settings` | `libs.otus.settings` |
| `com.otus:accounts` | `libs.otus.accounts` |

Mail pins Meta 0.3.0 for all six libraries. Local source substitution is optional development
configuration; normal builds download the pinned GitHub Packages artifacts. Calendar adoption
is deferred. See [the component inventory](component-inventory.md) for ownership and release state.

All otus-meta libraries share one version, `otus-meta` in `gradle/libs.versions.toml`.

## Token setup

Forgejo is the primary Git host, but released artifacts remain on GitHub Packages during
the cutover. After the pinned version and every platform variant have been verified on
Forgejo, `otusMetaRegistry` can select its Maven URL in private Gradle properties. Use
credentials for that registry. Forgejo CI selects it through the `OTUS_META_REGISTRY_URL`
variable. See [hosting and CI](hosting.md). An SSH key does not authenticate Maven downloads.

GitHub Packages requires a token to download Maven packages, even for read access. Create a classic
token with the `read:packages` scope and add it to `~/.gradle/gradle.properties`, never to this
repository:

```properties
otusMetaUsername=<GitHub username>
otusMetaPassword=<token>
```

CI reads the same token from the repository secret `OTUS_META_PACKAGES_TOKEN`. The Maven registry
scopes package access to the repository that published it, so CI cannot use its own `GITHUB_TOKEN`.

## Changing shared code

1. Change the library in otus-meta.
2. Try it here with `./gradlew checkAll -Potus.meta.path=../otus-meta`. Gradle then builds the
   library from that checkout instead of downloading it.
3. Release the library from otus-meta.
4. Update `otus-meta` in `gradle/libs.versions.toml` and run `./gradlew checkAll` without the
   property.

`main` must always build from a released version. See
[otus-meta's consuming guide](https://git.hilfikers.com/florian.ruby/otus-meta/src/branch/main/docs/consuming.md)
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
machine's Gradle properties. Other dependencies keep their existing repositories. No GitHub
Packages credentials are required for a file repository. The pinned version still applies.

This checks artifact metadata, transitive dependencies, compilation, and Android resource
packaging independently of a sibling source build. Kotlin iOS compilation on Linux does not
verify Xcode linking or simulator resource loading. After the remote release, repeat the build
without either override to verify GitHub Packages access and the released artifacts.
