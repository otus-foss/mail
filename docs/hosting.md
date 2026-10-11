# Hosting and CI

[Otus Mail on GitHub](https://github.com/reazndev/otus-mail) hosts development, pull requests,
issues, and CI. The repository used Forgejo from 2026-10-08 to 2026-10-11; every Forgejo branch
was pushed to GitHub before the switch. Do not push new work to Forgejo.

New clones use:

```sh
git clone git@github.com:reazndev/otus-mail.git
cd otus-mail
git config --local core.hooksPath .githooks
```

`.github/workflows` holds every workflow. Linux jobs run on Ubicloud's `ubicloud-standard-2`
runners. The `ios` job is paused with `if: false`; delete that condition to run it again. The
job needs macOS 15 with Xcode 26.3.

Mail downloads its pinned Meta release from GitHub Packages at
`https://maven.pkg.github.com/reazndev/otus-meta`. CI reads a GitHub token with `read:packages`
from the `OTUS_META_PACKAGES_TOKEN` secret. See [shared libraries](shared-libraries.md) for local
credentials, and the
[suite hosting guide](https://github.com/reazndev/otus-meta/blob/main/docs/hosting.md) for the
switch from Forgejo.
