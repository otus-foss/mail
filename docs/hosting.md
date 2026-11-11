# Hosting and CI

[Otus Mail on GitHub](https://github.com/otus-foss/otus-mail) hosts development, pull requests,
issues, and CI. Core's [hosting guide](https://github.com/otus-foss/otus-core/blob/main/docs/hosting.md) describes the workflows, when they run,
the shared actions, and the runners for every Otus repository.

New clones go next to otus-core, which Mail builds from source:

```sh
git clone git@github.com:otus-foss/otus-core.git
git clone git@github.com:otus-foss/otus-mail.git
cd otus-mail
git config --local core.hooksPath .githooks
```

CI checks Mail out to `otus-mail` and otus-core beside it, on the Core branch with this
branch's name when there is one, else `main`. otus-core is public, so the checkout needs no key.

The `nightly` workflow publishes a signed APK of `main` each night that Mail or Core changes; see
[nightly builds](nightly.md). The `quality` workflow also builds the release APK and comments its
size against the latest nightly. Dependabot also updates the APNs bridge's base image.
