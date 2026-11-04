# Hosting and CI

[Otus Mail on GitHub](https://github.com/reazndev/otus-mail) hosts development, pull requests,
issues, and CI.

New clones use:

```sh
git clone git@github.com:reazndev/otus-mail.git
cd otus-mail
git config --local core.hooksPath .githooks
```

`.github/workflows` holds every workflow. Linux jobs run on Ubicloud's `ubicloud-standard-2`
runners. The `ios` job runs on GitHub's `macos-15` runners with Xcode 26.3, because Ubicloud has
no macOS runners. Like the Linux jobs, it runs once per pull request that is ready for review. The `nightly` workflow publishes a signed APK of main each
night that main changes; see [nightly builds](nightly.md).

Mail downloads its pinned Meta release from GitHub Packages at
`https://maven.pkg.github.com/reazndev/otus-meta`. CI reads a GitHub token with `read:packages`
from the `OTUS_META_PACKAGES_TOKEN` secret for local use; while Meta publishing is paused, every
quality job and the nightly build Meta from source. See [shared libraries](shared-libraries.md) for local
credentials, and the
[suite hosting guide](https://github.com/reazndev/otus-meta/blob/main/docs/hosting.md) for package
publishing.
