# Hosting and CI

[Otus Mail on GitHub](https://github.com/reazndev/otus-mail) hosts development, pull requests,
issues, and CI. Meta's [hosting guide](https://github.com/reazndev/otus-meta/blob/main/docs/hosting.md) describes the workflows, when they run,
the shared actions, and the runners for every Otus repository.

New clones go next to otus-meta, which Mail builds from source:

```sh
git clone git@github.com:reazndev/otus-meta.git
git clone git@github.com:reazndev/otus-mail.git
cd otus-mail
git config --local core.hooksPath .githooks
```

CI checks Mail out to `otus-mail` and otus-meta beside it, on the Meta branch with this
branch's name when there is one, else `main`. It reads otus-meta with the read-only
`OTUS_META_DEPLOY_KEY` deploy key, stored as an Actions secret in this repository.

The `nightly` workflow publishes a signed APK of `main` each night that Mail or Meta changes; see
[nightly builds](nightly.md). The `quality` workflow also builds the release APK and comments its
size against the latest nightly. Dependabot also updates the APNs bridge's base image.
