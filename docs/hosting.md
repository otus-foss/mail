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
`.github/workflows`. Forgejo requires registered runners labelled `otus-linux` and
`otus-macos`. The Mac needs Xcode 26.3. The required checks remain `portable`, `android`, and
`ios`; none has been verified on Forgejo during the Git cutover.

Mail continues downloading its pinned Meta release from GitHub Packages. Forgejo CI needs a
GitHub read token as `OTUS_META_PACKAGES_TOKEN`; the username variable
`OTUS_META_PACKAGES_USERNAME` defaults to `reazndev`. See [shared libraries](shared-libraries.md).

Runners, secrets, Actions enablement, branch protection, and automatic mirroring require
authenticated server access and remain unverified. The
[suite hosting guide](https://git.hilfikers.com/florian.ruby/otus-meta/src/branch/main/docs/hosting.md)
records runner requirements and the separate package migration.
