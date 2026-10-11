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

Mail pins Meta 0.5.0 for all six libraries. Local source substitution is optional development
configuration; normal builds download the pinned Forgejo Packages artifacts. Calendar adoption
is deferred. See [the component inventory](component-inventory.md) for ownership and release state.

All otus-meta libraries share one version, `otus-meta` in `gradle/libs.versions.toml`.

## Token setup

The release registry is `https://git.hilfikers.com/api/packages/florian.ruby/maven`.
Mail uses this registry by default, locally and on both CI hosts. `otusMetaRegistry` in private
Gradle properties or `OTUS_META_REGISTRY_URL` in Actions variables can override it.

Create a token in Forgejo's user Settings → Applications with the `read:package` scope.
The token's account must have access to the packages owned by `florian.ruby`. Package scopes
require an access token for all accessible resources, rather than a token restricted to specific
repositories. See [Forgejo token scopes](https://forgejo.org/docs/latest/user/authentication/token-scope/).

On a development machine, add credentials to `~/.gradle/gradle.properties`, never to this repository:

```properties
otusMetaUsername=florian.ruby
otusMetaPassword=<Forgejo package-read token>
```

In Mail's Actions settings, on each CI host that runs the workflow, configure:

- Secret `OTUS_META_PACKAGES_TOKEN`: the Forgejo package-read token.
- Variable `OTUS_META_PACKAGES_USERNAME`: the token's Forgejo username, defaulting to `florian.ruby`.
- Variable `OTUS_META_REGISTRY_URL`: optional registry override; normally leave it unset.

An existing GitHub registry override or GitHub token must be replaced when switching to Forgejo.
SSH Git credentials do not authenticate Maven downloads.

## Package migration status

Forgejo Packages is the selected registry. Meta's tag workflow publishes all six modules and
their Android, iOS device, and iOS simulator variants there, starting with 0.3.1. Version 0.3.0
exists only on GitHub Packages. Git repository migration does not transfer Maven packages.
Mail's Linux CI resolves the pinned release from Forgejo. iOS linking against the Forgejo
artifacts is unverified until a macOS runner is available. See [hosting and CI](hosting.md).

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
machine's Gradle properties. Other dependencies keep their existing repositories. No remote
package credentials are required for a file repository. The pinned version still applies.

This checks artifact metadata, transitive dependencies, compilation, and Android resource
packaging independently of a sibling source build. Kotlin iOS compilation on Linux does not
verify Xcode linking or simulator resource loading. After the remote release, repeat the build
without either override to verify Forgejo Packages access and the released artifacts.
