# Hosting and CI

[Otus Mail on GitHub](https://github.com/otus-foss/mail) hosts development, pull requests,
issues, and CI. Core's [hosting guide](https://github.com/otus-foss/core/blob/main/docs/hosting.md) describes the workflows, when they run,
the shared actions, and the runners for every Otus repository.

New clones go next to core, which Mail builds from source:

```sh
git clone git@github.com:otus-foss/core.git
git clone git@github.com:otus-foss/mail.git
cd mail
git config --local core.hooksPath .githooks
```

CI checks Mail out to `mail` and core beside it, on the Core branch with this
branch's name when there is one, else `main`. core is public, so the checkout needs no key.

The `nightly` workflow publishes a signed APK of `main` each night that Mail or Core changes; see
[nightly builds](nightly.md). The `quality` workflow also builds the release APK and comments its
size against the latest nightly. Dependabot also updates the APNs bridge's base image.
