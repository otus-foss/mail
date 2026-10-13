# Nightly builds

`.github/workflows/nightly.yml` runs at 02:00 UTC and can also be started by hand. When main has
new commits since the last nightly, it builds a signed APK of main and publishes it as a release of
the public [otus-mail-nightly](https://github.com/reazndev/otus-mail-nightly) repository. The
source stays private. Each release is tagged `nightly-<number>`, where the number is main's commit
count, and its notes list the commits since the previous nightly.

## The nightly app

The `nightly` build type is the release build with these changes:

- The app id is `com.otus.mail.nightly` and the name is "Otus Mail Nightly", so it installs beside
  a debug build.
- The version code is the nightly number and the version name is `nightly-<number>`.
- It is signed with the nightly key. Android installs an update only when its key matches the
  installed app's key, so keep the same key for every nightly.

At each launch the nightly app reads the latest release of the nightly repository. When that
build is newer than the installed one, the sidebar shows "Get nightly <number>" above the
mailboxes. Tapping it opens the APK link in the browser, and Android's installer takes over once
the download finishes. A failed check shows nothing. Other builds never check.

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

4. Add the secrets to otus-mail:

   ```sh
   base64 -w0 otus-nightly.p12 | gh secret set OTUS_NIGHTLY_KEYSTORE -R reazndev/otus-mail
   gh secret set OTUS_NIGHTLY_KEYSTORE_PASSWORD -R reazndev/otus-mail
   gh secret set OTUS_NIGHTLY_TOKEN -R reazndev/otus-mail
   ```

The repository name appears in the workflow and in `GitHubNightlyFeed`. Change both together.

## Local build

```sh
OTUS_NIGHTLY_KEYSTORE=otus-nightly.p12 OTUS_NIGHTLY_KEYSTORE_PASSWORD=... \
  ./gradlew :androidApp:assembleNightly -Potus.versionCode=1 -Potus.versionName=nightly-1
```

Without `OTUS_NIGHTLY_KEYSTORE`, the build is unsigned and Android will not install it.
