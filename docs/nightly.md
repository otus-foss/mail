# Nightly builds

`.github/workflows/nightly.yml` runs at 00:00 UTC, which is 01:00 in Berlin in winter and 02:00 in
summer. It can also be started by hand. Changes to Mail main or the selected Meta revision trigger a signed
APK release in [otus-mail-nightly](https://github.com/reazndev/otus-mail-nightly). The source stays
private. Each release is tagged `nightly-<number>`, with a version code one higher than the previous
release. The first release starts at Mail's commit count. Draft releases reserve their version codes,
so a retry uses a code above every existing release, including unfinished drafts.

The version job temporarily pins Meta while [Mail issue #174](https://github.com/reazndev/otus-mail/issues/174)
migrates Mail to Meta's new public packages. Changes on Meta main do not trigger builds while the
pin is active. Once Mail main builds against those packages on Meta main, restore the version job's
Meta checkout `ref` to `main` and remove this paragraph and the pin's workflow comments.

The version job resolves both commits. The build checks out those exact revisions and publishes
them in `provenance.json` beside the APK. Notes include Mail and Meta commits since the preceding
recorded revisions. When the preceding release has no recorded revision, the notes list the commits
since it was published instead. Only a published release can make an unchanged pair skip scheduled and manual runs.
The version check reads every release page, so unfinished drafts cannot hide the last publication.
A rerun after a failed build uses the next unpublished version code; a rerun after publication skips an unchanged pair.

Meta source is used because package publishing is paused. The first release after this workflow
change rebuilds even if Mail is unchanged, since older releases have no Meta provenance. Its code
still increases from the previous release, so Android can update it. API or download failures stop
the workflow instead of treating an unavailable release as an empty history.

## The nightly app

The `nightly` build type is the release build with these changes:

- The app id is `com.otus.mail.nightly` and the name is "Otus Mail Nightly", so it installs beside
  a debug build.
- The version code is the nightly number. The version name is the build's date as d.m.yy, such as
  `18.10.26`, and the sidebar footer shows it.
- It is signed with the nightly key. Android installs an update only when its key matches the
  installed app's key, so keep the same key for every nightly.

At each launch the nightly app reads the recent releases of the nightly repository. When one is
newer than the installed build, a circled arrow leads the version in the sidebar footer. Tapping
the version opens a sheet with the changes of every newer build, grouped by commit scope. Scopes spelled with and without hyphens share a group, and a change
listed by both Mail and Meta appears once. Only
`feat`, `fix`, and `perf` commits appear there. Changelog, beside Install update, lists every
published nightly by date, with the installed ones on a lighter panel under an "Installed"
divider. A failed check shows nothing. Other builds never check.

The sheet's Install update button downloads the APK inside the app, with a ring around the
arrow for progress, and hands it to Android's `PackageInstaller`. Android asks once to let Otus
install apps and to confirm the first update. After Otus has installed itself, later updates need no confirmation on
Android 12 and newer. Android closes the app when the update lands. Only the nightly manifest
declares `REQUEST_INSTALL_PACKAGES`.

## One-time setup

1. Create the public repository with a README, because a release needs a commit to tag:

   ```sh
   gh repo create reazndev/otus-mail-nightly --public --add-readme
   ```

2. Create the signing key and keep the file and password outside the repository, for example in a
   password manager. Losing it means every phone must uninstall the nightly app once.

   ```sh
   keytool -genkeypair -keystore otus-nightly.p12 -storetype PKCS12 -alias otus-nightly \
     -keyalg RSA -keysize 4096 -validity 10000 -dname "CN=Otus Mail Nightly"
   ```

3. Create a fine-grained token with Contents read and write access to `otus-mail-nightly` only.

4. Create a read-only deploy key so the workflow can check out the private otus-meta:

   ```sh
   ssh-keygen -t ed25519 -N "" -C "otus-mail nightly" -f otus-meta-deploy
   gh repo deploy-key add otus-meta-deploy.pub -R reazndev/otus-meta --title "otus-mail nightly"
   gh secret set OTUS_META_DEPLOY_KEY -R reazndev/otus-mail < otus-meta-deploy
   rm otus-meta-deploy otus-meta-deploy.pub
   ```

5. Add the other secrets to otus-mail:

   ```sh
   base64 -w0 otus-nightly.p12 | gh secret set OTUS_NIGHTLY_KEYSTORE -R reazndev/otus-mail
   gh secret set OTUS_NIGHTLY_KEYSTORE_PASSWORD -R reazndev/otus-mail
   gh secret set OTUS_NIGHTLY_TOKEN -R reazndev/otus-mail
   ```

The repository name appears in the workflow and in `GitHubNightlyFeed`. Change both together.

## Local build

```sh
OTUS_NIGHTLY_KEYSTORE=otus-nightly.p12 OTUS_NIGHTLY_KEYSTORE_PASSWORD=... \
  ./gradlew :androidApp:assembleNightly -Potus.versionCode=1 -Potus.versionName=18.10.26
```

Without `OTUS_NIGHTLY_KEYSTORE`, the build is unsigned and Android will not install it.
