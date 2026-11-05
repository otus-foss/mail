# Shared libraries

Code that Mail and Calendar both use lives in
[otus-meta](https://github.com/reazndev/otus-meta). Mail uses:

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

See [the component inventory](component-inventory.md) for historical ownership decisions.

## Building against Meta

Mail builds otus-meta from source: the checkout next to it, `../otus-meta`, or the one the
`otus.meta.path` Gradle property names, for example in `~/.gradle/gradle.properties` or with
`-Potus.meta.path=../meta-worktree`. Gradle replaces each `com.otus:<module>` with the matching
project of that checkout, and the build also takes Meta's convention plugins, its version catalog
as `libs`, its tools, and its detekt configuration from it. Nothing is downloaded from GitHub
Packages and no Meta version is pinned. See
[otus-meta's consuming guide](https://github.com/reazndev/otus-meta/blob/main/docs/consuming.md).

`gradle/app.versions.toml` holds the versions only Mail uses, SQLDelight, WorkManager, and
UnifiedPush, as the `appLibs` catalog. Every other version comes from Meta's catalog.

## Changing shared code

1. Change the library in otus-meta on a branch, and make the matching Mail change on a branch
   with the same name.
2. Build Mail against that Meta branch locally. CI does the same: it checks out the Meta branch
   with this branch's name when there is one, else Meta's `main`, and the `pull request`
   workflow labels the pull request `needs meta`.
3. Merge the Meta pull request first, then Mail's, so Mail's `main` always builds against
   Meta's `main`.

The nightly builds Mail's `main` against Meta's `main`, so merge such a pair one after the other,
away from the nightly's 00:00 UTC run.

Meta is the central owner of visual elements, reusable components, tokens, and symbols. Add new
ones there, even when Mail is their only consumer. Mail keeps feature screens and the adapters
that map mail data into shared renderers.
