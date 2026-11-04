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

`actionlint` lints workflows in pull requests that change `.github/`. `issue link` warns when a
pull request closes no issue, and `issue report` lists each Monday the open issues that merged pull
requests name, in an issue called "Open issues with merged work". Dependabot opens one grouped
pull request a week for GitHub Actions and one for Gradle, leaving the pinned Otus libraries alone.
The `quality` workflow also builds the release APK and comments its size against the latest
nightly.

Each `quality` run first lists the changed files: workflow-only and Markdown-only pull requests skip
the build, and pull requests that change only Android code skip the macOS `ios` job. The
`pull request` workflow labels each pull request by size (`size:XS` to `size:XL`, tests left
out), comments the screenshots it adds or changes beside the old ones, and warns when UI code
changes without a screenshot. In Mail it also adds a `needs meta` label when Otus Meta has a branch with the same name,
since that pair has to land together, Meta first.

Mail downloads its pinned Meta release from GitHub Packages at
`https://maven.pkg.github.com/reazndev/otus-meta`. CI reads a GitHub token with `read:packages`
from the `OTUS_META_PACKAGES_TOKEN` secret for local use; while Meta publishing is paused, every
quality job and the nightly build Meta from source. See [shared libraries](shared-libraries.md) for local
credentials, and the
[suite hosting guide](https://github.com/reazndev/otus-meta/blob/main/docs/hosting.md) for package
publishing.
