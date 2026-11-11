# Shared libraries

Code that Mail and Calendar both use lives in
[otus-core](https://github.com/otus-foss/otus-core). Mail uses:

| Library | Version catalog entry |
| --- | --- |
| `com.otusfoss.core:jmap` | `libs.otus.jmap` |
| `com.otusfoss.core:avatar` | `libs.otus.avatar` |
| `com.otusfoss.core:dateformat` | `libs.otus.dateformat` |
| `com.otusfoss.core:designsystem` | `libs.otus.designsystem` |
| `com.otusfoss.core:settings` | `libs.otus.settings` |
| `com.otusfoss.core:accounts` | `libs.otus.accounts` |
| `com.otusfoss.core:jmap-push` | `libs.otus.jmap.push` |
| `com.otusfoss.core:jscontact` | `libs.otus.jscontact` |
| `com.otusfoss.core:jmap-contacts` | `libs.otus.jmap.contacts` |

## Building against Core

Mail builds otus-core from source: the checkout next to it, `../otus-core`, or the one the
`otus.core.path` Gradle property names, for example in `~/.gradle/gradle.properties` or with
`-Potus.core.path=../core-worktree`. Gradle replaces each `com.otusfoss.core:<module>` with the matching
project of that checkout, and the build also takes Core's convention plugins, its version catalog
as `libs`, its tools, and its detekt configuration from it. Nothing is downloaded from GitHub
Packages and no Core version is pinned. See
[otus-core's consuming guide](https://github.com/otus-foss/otus-core/blob/main/docs/consuming.md).

`gradle/app.versions.toml` holds the versions only Mail uses, SQLDelight, WorkManager, and
UnifiedPush, as the `appLibs` catalog. Every other version comes from Core's catalog.

## Changing shared code

1. Change the library in otus-core on a branch, and make the matching Mail change on a branch
   with the same name.
2. Build Mail against that Core branch locally. CI does the same: it checks out the Core branch
   with this branch's name when there is one, else Core's `main`, and the `pull request`
   workflow labels the pull request `needs core`.
3. Merge the Core pull request first, then Mail's, so Mail's `main` always builds against
   Core's `main`.

The nightly builds Mail's `main` against Core's `main`, so merge such a pair one after the other,
away from the nightly's 00:00 UTC run.

Core is the central owner of visual elements, reusable components, tokens, and symbols. Add new
ones there, even when Mail is their only consumer. Mail keeps feature screens and the adapters
that map mail data into shared renderers.
