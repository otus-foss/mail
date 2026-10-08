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

All otus-meta libraries share one version, `otus-meta` in `gradle/libs.versions.toml`.

## Token setup

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
[otus-meta's consuming guide](https://github.com/reazndev/otus-meta/blob/main/docs/consuming.md)
for details.
