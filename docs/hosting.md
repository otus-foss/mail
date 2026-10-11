# Hosting and CI

[Otus Mail on Forgejo](https://git.hilfikers.com/florian.ruby/otus-mail) is the primary
repository. Use it for development, pull requests, and new issues. GitHub remains a secondary
copy. Historical GitHub issue links remain until imported numbering is verified.

Existing workspaces use `origin` for Forgejo and `github` for GitHub. New clones use:

```sh
git clone git@git.hilfikers.com:florian.ruby/otus-mail.git
cd otus-mail
git config --local core.hooksPath .githooks
git remote add github git@github.com:reazndev/otus-mail.git
git config --local remote.pushDefault origin
```

Normal fetches and pushes use Forgejo. The secondary remote does not synchronize automatically.
Push accepted commits explicitly to GitHub when needed for GitHub CI. Keep the same history
and respect branch protection on both hosts.

Forgejo workflows live in `.forgejo/workflows`; the working GitHub workflows remain in
`.github/workflows`. Forgejo requires registered runners labelled `ubuntu-latest` and
`otus-macos`. The Mac needs Xcode 26.3. The required checks remain `portable`, `android`, and
`ios`; none has been verified on Forgejo during the Git cutover.

Mail downloads its pinned Meta release from the Forgejo Maven registry at
`https://git.hilfikers.com/api/packages/florian.ruby/maven`. Configure a Forgejo `read:package`
token as `OTUS_META_PACKAGES_TOKEN`; `OTUS_META_PACKAGES_USERNAME` defaults to `florian.ruby`.
Clear a stale GitHub `OTUS_META_REGISTRY_URL` override. Both CI hosts use the same registry.
See [shared libraries](shared-libraries.md) for local credentials and migration status.

The Linux label follows the instance maintainer's `runs-on: ubuntu-latest` guidance.
The `ios` job still requires a separate `otus-macos` runner with Xcode 26.3. A Linux
runner cannot build the Xcode app; a queued iOS job needs its own runner.

The GitHub workflows run their Linux jobs on Ubicloud's `ubicloud-standard-2` runners.
The GitHub `ios` job is paused with `if: false`; delete that condition to run it again.

Runners, secrets, Actions enablement, branch protection, and automatic mirroring require
authenticated server access and remain unverified. The
[suite hosting guide](https://git.hilfikers.com/florian.ruby/otus-meta/src/branch/main/docs/hosting.md)
records runner requirements and the separate package migration.
