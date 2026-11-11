# Nightly builds

`.github/workflows/nightly.yml` runs at 00:00 UTC, which is 01:00 in Berlin in winter and 02:00 in
summer. It can also be started by hand. Changes to either Mail main or Core main trigger a signed
APK, published as a prerelease of this repository. Each release is tagged `nightly-<number>` on the
Mail commit it was built from, with a version code one higher than the previous release. That adds
about one tag a day. A prerelease never becomes the repository's "Latest" release, so a stable
release can take that place later. The version script stops at any tag that isn't `nightly-<number>`,
so teach it to skip stable tags before the first stable release. The first release starts at Mail's commit count. Draft releases reserve their version codes,
so a retry uses a code above every existing release, including unfinished drafts.

The version job resolves both commits. The build checks out those exact revisions and publishes
them in `provenance.json` beside the APK. Notes include only `feat`, `fix`, and `perf` commits from
Mail and Core since the preceding recorded revisions. Internal changes such as `refactor`, `docs`, `test`, `build`, `ci`, and `chore`
are omitted from both the release notes and the in-app changelog. When the preceding release has no
recorded revision, the notes list the commits since it was published instead. Only a published release can make an unchanged pair skip scheduled and manual runs.
The version check reads every release page, so unfinished drafts cannot hide the last publication.
A rerun after a failed build uses the next unpublished version code; a rerun after publication skips an unchanged pair.

The `build` job runs Gradle with a read-only token and uploads the unsigned APK. The `release`
job, which runs no Gradle, signs it with `zipalign` and `apksigner` from build-tools 36.0.0 and
publishes it with a token that can write releases. So Core's build scripts and the Gradle plugins
never see the key, its password, or a write token. The key file is deleted afterwards, even when a
step failed. The `version` job also gets a write token, because only that sees draft releases.

Core source is used because package publishing is paused. The first release after this workflow
change rebuilds even if Mail is unchanged, since older releases have no Core provenance. Its code
still increases from the previous release, so Android can update it. API or download failures stop
the workflow instead of treating an unavailable release as an empty history.

## The nightly app

The `nightly` build type is the release build with these changes:

- The app id is `com.otusfoss.mail.nightly` and the name is "Otus Mail Nightly", so it installs beside
  a debug build.
- The version code is the nightly number. The version name is the build's date as d.m.yy, such as
  `18.10.26`, and the sidebar footer shows it.
- It is signed with the nightly key. Android installs an update only when its key matches the
  installed app's key, so keep the same key for every nightly.

At each launch the nightly app reads the recent releases of the nightly repository. When one is
newer than the installed build, a circled arrow leads the version in the sidebar footer. Tapping
the version opens a sheet with the changes of every newer build, grouped by commit scope. Scopes spelled with and without hyphens share a group, and a change
listed by both Mail and Core appears once. Only
`feat`, `fix`, and `perf` commits appear there. Changelog, beside Install update, lists every
published nightly by date, with the installed ones on a lighter panel under an "Installed"
divider. A failed check shows nothing. Other builds never check.

The sheet's Install update button downloads the APK inside the app, with a ring around the
arrow for progress, and hands it to Android's `PackageInstaller`. Android asks once to let Otus
install apps and to confirm the first update. After Otus has installed itself, later updates need no confirmation on
Android 12 and newer. Android closes the app when the update lands. Only the nightly manifest
declares `REQUEST_INSTALL_PACKAGES`.

The app only offers a build whose APK is an `https://github.com/<repository>/releases/download/`
address with a SHA-256 `digest`, which GitHub's release API adds to each uploaded asset. It
installs the download only when its SHA-256 matches. The hash comes from the same API answer as the
address, so it catches a changed or broken download, not a changed release.

## One-time setup

1. Create the signing key and keep the file and password outside the repository, for example in a
   password manager. Losing it means every phone must uninstall the nightly app once.

   ```sh
   keytool -genkeypair -keystore otus-nightly.p12 -storetype PKCS12 -alias otus-nightly \
     -keyalg RSA -keysize 4096 -validity 10000 -dname "CN=Otus Mail Nightly"
   ```

2. Add the key and its password to mail. The workflow publishes with its own token, and
   core is public, so it needs no other secret:

   ```sh
   base64 -w0 otus-nightly.p12 | gh secret set OTUS_NIGHTLY_KEYSTORE -R otus-foss/mail
   gh secret set OTUS_NIGHTLY_KEYSTORE_PASSWORD -R otus-foss/mail
   ```

`GitHubNightlyFeed` names the repository the app reads. Change it when the repository moves.
Releases from before 2026-11 are in the archived `otus-mail-nightly` repository.

## Local build

```sh
./gradlew :androidApp:assembleNightly -Potus.versionCode=1 -Potus.versionName=18.10.26
```

The build is unsigned, and Android will not install it. To sign it as the workflow does:

```sh
apksigner sign --ks otus-nightly.p12 --ks-key-alias otus-nightly \
  --out androidApp-nightly.apk androidApp/build/outputs/apk/nightly/androidApp-nightly-unsigned.apk
```
