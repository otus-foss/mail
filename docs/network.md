# Network destinations

This page lists every host the installed app may contact, when it does, and what it sends. It
covers Mail's own code and the otus-meta libraries it links. Links to Meta point at its `main`
branch. Build-time hosts, such as Gradle and `maven.pkg.github.com`, are out of scope; so are
services the operating system runs on its own.

Android requests only the `INTERNET` permission for this
([manifest](../androidApp/src/main/AndroidManifest.xml)). The nightly build adds
`REQUEST_INSTALL_PACKAGES` for its updater.

| Destination | Contacted | Default | Can be turned off |
| --- | --- | --- | --- |
| The account's JMAP server | Sign-in and all mail work | On | Only by removing the account |
| Hosts the user approved at sign-in | API or blob calls the session points there | Off | Not approved means not contacted |
| Remote content in a message | Opening a message that may load it | Contacts only | Load images › Never |
| Sender websites | Inbox avatars | Off | Website icons switch |
| Contact card photo hosts | Avatars of contacts with linked photos | Off | Website icons switch |
| `icons.duckduckgo.com` | Inbox avatars with that source | Off | Icons from, or Website icons |
| The user's Bulwark server | Inbox avatars with that source | Off | Icons from, or Website icons |
| Google, through Play services | Android push with Google chosen | Off | Notifications › Off |
| A UnifiedPush app's server | Android push with that app chosen | Off | Notifications › Off |
| The APNs bridge and Apple | iOS push with Apple Push chosen | Off | Notifications › Off |
| `api.github.com` and GitHub downloads | Nightly app launch and update | Nightly only | No; other builds never check |
| Links the user taps | Only on a tap, in another app | — | Not tapping |

## The JMAP server

Every account talks to one JMAP server (RFC 8620), at the address typed on the sign-in page.

- **Host:** the server the user typed. A bare host such as `mail.example.org` becomes
  `https://mail.example.org/.well-known/jmap` (section 2.2); a full HTTPS URL is used as the
  session URL. Plain `http://` is refused. There is no DNS SRV or autoconfig lookup. Source:
  [`JmapServerAddress.kt`](https://github.com/reazndev/otus-meta/blob/main/accounts/src/commonMain/kotlin/com/otus/accounts/domain/JmapServerAddress.kt).
- **When:** at sign-in, on every inbox, folder, search, read, send, contact, and rule action, and
  in the background: the Android check about every 15 minutes
  ([`JmapNewMailChecker.kt`](../shared/src/commonMain/kotlin/com/otus/mail/feature/notifications/data/JmapNewMailChecker.kt)),
  [`OutboxWorker`](../androidApp/src/main/kotlin/com/otus/mail/OutboxWorker.kt) once the network
  is back, and push subscription upkeep. See [JMAP](jmap.md) and [notifications](notifications.md).
- **Sends:** the username and password as HTTP Basic on every request, or a stored bearer token
  ([`JmapAuth.kt`](https://github.com/reazndev/otus-meta/blob/main/jmap/src/commonMain/kotlin/com/otus/jmap/JmapAuth.kt)),
  plus everything the user does with their mail: reads, changes, sent messages, uploads,
  contacts, Sieve scripts, and push subscriptions with the push endpoint and its keys.
- **Off:** only by removing the account.

Mail has no OAuth sign-in flow. `JmapAuth.Bearer` accepts an OAuth access token, but no Mail
screen obtains one, so no OAuth endpoint is contacted.

### Other hosts the session names

The session's `apiUrl`, `downloadUrl`, and `uploadUrl` may sit on another origin, and the session
URL may redirect. `JmapClient` follows redirects itself and sends credentials only to the session
URL's origin and to origins the user approved on the sign-in page. A redirect or `apiUrl`
elsewhere stops before credentials are sent. At sign-in, a blob URL on an unapproved origin also
stops. Source:
[`JmapClient.kt`](https://github.com/reazndev/otus-meta/blob/main/jmap/src/commonMain/kotlin/com/otus/jmap/JmapClient.kt),
[`AccountsState.kt`](https://github.com/reazndev/otus-meta/blob/main/accounts/src/commonMain/kotlin/com/otus/accounts/presentation/AccountsState.kt).

Blob downloads from these hosts include attachments, inline `cid:` images
([`CidImages.kt`](../shared/src/commonMain/kotlin/com/otus/mail/feature/inbox/data/CidImages.kt)),
and contact photos stored as blobs
([`JmapContactAvatarSource.kt`](https://github.com/reazndev/otus-meta/blob/main/avatar/src/commonMain/kotlin/com/otus/avatar/JmapContactAvatarSource.kt)).

## Remote content in messages

An HTML message can refer to images, style sheets, and fonts on any host. The message view is a
WebView on Android and a WKWebView on iOS, with scripts, file access, and forms off.

- **Host:** whatever the message's HTML names, over `http:` or `https:`.
- **When:** when the message opens and Settings › Privacy & security › **Load images** allows it:
  **In your contacts** (the default) loads them for contacts, people the user wrote to, and
  allowed senders; **Always** for everyone; **Never** for no one. Mail in Spam never loads them.
  **Show** on the banner loads them once. Source:
  [`ReadRules.kt`](../shared/src/commonMain/kotlin/com/otus/mail/feature/inbox/domain/ReadRules.kt),
  [`ReadPages.kt`](../shared/src/commonMain/kotlin/com/otus/mail/feature/inbox/presentation/components/ReadPages.kt).
- **Sends:** the device's IP address, the URL (which can identify the recipient and act as a read
  receipt), and the time it was opened. Android sends a User-Agent without the device model or
  build, and turns Safe Browsing off so image addresses do not go to Google. Source:
  [`HtmlView.kt`](https://github.com/reazndev/otus-meta/blob/main/designsystem/src/commonMain/kotlin/com/otus/designsystem/mail/read/elements/HtmlView.kt),
  [`HtmlView.android.kt`](https://github.com/reazndev/otus-meta/blob/main/designsystem/src/androidMain/kotlin/com/otus/designsystem/mail/read/elements/HtmlView.android.kt),
  [`HtmlView.ios.kt`](https://github.com/reazndev/otus-meta/blob/main/designsystem/src/iosMain/kotlin/com/otus/designsystem/mail/read/elements/HtmlView.ios.kt).
- **Off:** Load images › **Never**. A blocked message's content-security policy allows only
  `data:` images, and Android's `blockNetworkLoads` and an iOS content rule list block every
  request beneath it.

Printing on Android renders in a separate WebView with network loads off
([`AndroidMessagePrinter.kt`](../androidApp/src/main/kotlin/com/otus/mail/AndroidMessagePrinter.kt)).

## Avatars

Avatars always try contact photos on the user's own JMAP server. Every other avatar source waits
for Settings › Privacy & security › **Website icons**, which is off by default
([`MailBehavior.kt`](../shared/src/commonMain/kotlin/com/otus/mail/feature/inbox/domain/MailBehavior.kt),
[`MailAvatarOptions.kt`](../shared/src/commonMain/kotlin/com/otus/mail/core/avatar/MailAvatarOptions.kt)).
These requests carry no credentials or cookies, accept only public HTTPS hosts on port 443, and
check every redirect
([`HttpAvatarImageLoader.kt`](https://github.com/reazndev/otus-meta/blob/main/avatar/src/commonMain/kotlin/com/otus/avatar/HttpAvatarImageLoader.kt)).
See [website icons](avatars.md#website-icons) and Meta's
[avatar guide](https://github.com/reazndev/otus-meta/blob/main/docs/avatar.md).

- **Contact card photos.** With Website icons on, a photo that a JMAP contact card links to by
  URL loads from that URL's host. It learns the device's IP address.
- **Sender websites**, the default icon source. Mail asks the sender's registrable domain, never
  the exact host: `https://{domain}/` for its page head, then `apple-touch-icon.png`,
  `favicon.png`, and `favicon.ico`
  ([`WebsiteIcons.kt`](https://github.com/reazndev/otus-meta/blob/main/avatar/src/commonMain/kotlin/com/otus/avatar/WebsiteIcons.kt),
  [`HttpWebsiteIconSource.kt`](https://github.com/reazndev/otus-meta/blob/main/avatar/src/commonMain/kotlin/com/otus/avatar/HttpWebsiteIconSource.kt)).
  The website learns the device's IP address and that its mail was listed. Icons are cached per
  domain, so it sees this at most once per cache period.
- **DuckDuckGo.** `https://icons.duckduckgo.com/ip3/{domain}.ico`. DuckDuckGo receives the domain
  and the device's IP address; sender websites receive nothing.
- **Bulwark.** The address saved in **Icons from**, at `/api/favicon?domain={domain}` and
  `/api/bimi?domain={domain}`. Only that server sees the device; it fetches from DuckDuckGo and
  the sender's BIMI record itself. The address must be public HTTPS on port 443, and Otus sends it
  no credentials.

**Off:** turn Website icons off, or choose another icon source.

## Push

Push is off until the user picks a service under Settings › Notifications; until then Android
only runs its background check against the JMAP server. With push on, the JMAP server, not the
app, sends encrypted change notices to the push endpoint. The notices hold account ids and state
strings, never mail. On Android, choosing Off unregisters every account and deletes its server
subscriptions; on iOS it unregisters from APNs and revokes the bridge routes. Source:
[`PushSubscriptions.kt`](https://github.com/reazndev/otus-meta/blob/main/jmap-push/src/commonMain/kotlin/com/otus/jmap/push/PushSubscriptions.kt),
[`PushSyncWorker.kt`](../androidApp/src/main/kotlin/com/otus/mail/PushSyncWorker.kt).

- **Google (Android).** The embedded FCM distributor registers through Google Play services on
  the device, with the server's VAPID key from its JMAP session. Google learns that the device
  uses Otus and how often pushes arrive; the JMAP server sends to the Web Push endpoint Google
  issued. Source:
  [`AndroidPushRegistration.kt`](../androidApp/src/main/kotlin/com/otus/mail/AndroidPushRegistration.kt),
  [`MailPushService.kt`](../androidApp/src/main/kotlin/com/otus/mail/MailPushService.kt).
- **UnifiedPush apps (Android).** Mail hands its registration to the chosen app, such as ntfy, on
  the device. That app's server, which the user configured there, receives the encrypted notices
  from the JMAP server. Mail itself contacts no new host.
- **Apple Push (iOS).** The app registers its APNs device token, a random route per login, and a
  key id with the bridge at `OtusPushBridgeURL` in the app's Info.plist. It polls the bridge for
  verification pushes, renews routes on foreground and background refresh, and revokes them on
  Off or account removal
  ([`ApplePushBridge.kt`](../shared/src/commonMain/kotlin/com/otus/mail/feature/notifications/data/ApplePushBridge.kt),
  [`ApplePushDelegate.swift`](../iosApp/iosApp/ApplePushDelegate.swift)). The notification
  extension downloads each encrypted notice from the same bridge and deletes it after reading
  ([`NotificationService.swift`](../iosApp/NotificationService/NotificationService.swift)). The
  bridge never receives a login name, password, or decryption key. No bridge is configured by
  default; an empty URL leaves Apple Push unavailable. iOS itself contacts Apple to register for
  APNs. See [Apple push](apple-push.md).
- **The bridge contacts Apple.** `push_bridge/` is a server, not part of the app. It sends a
  generic alert to `api.push.apple.com` or `api.sandbox.push.apple.com` with an opaque message id
  ([`apns.py`](../push_bridge/apns.py)).

## Nightly updates

Only the Android nightly build checks for updates. Other builds never do
([`App.kt`](../shared/src/commonMain/kotlin/com/otus/mail/app/App.kt)).

- **Host:** `api.github.com`, for
  `/repos/reazndev/otus-mail-nightly/releases?per_page=30`
  ([`GitHubNightlyFeed.kt`](../shared/src/commonMain/kotlin/com/otus/mail/feature/updates/data/GitHubNightlyFeed.kt)).
  The APK comes from the release asset's `browser_download_url` on `github.com`, which GitHub
  redirects to its download host
  ([`AndroidUpdateInstaller.kt`](../androidApp/src/main/kotlin/com/otus/mail/AndroidUpdateInstaller.kt)).
- **When:** the release list once per launch; the APK only after a tap on **Install update**.
- **Sends:** the device's IP address and the HTTP client's default headers. No token, account, or
  installed version leaves the device; the comparison runs locally.
- **Off:** not a setting. Install a non-nightly build. See [nightly builds](nightly.md).

## Links

Links in a message, a list's unsubscribe page, and other web addresses open outside the app on a
tap, in whatever app handles them
([`ReadPageBody.kt`](../shared/src/commonMain/kotlin/com/otus/mail/feature/inbox/presentation/components/ReadPageBody.kt)).
Mail sends nothing to these hosts itself. Unsubscribing by mail goes through the JMAP server; Mail
does not send RFC 8058 one-click POSTs
([`ListUnsubscriber.kt`](../shared/src/commonMain/kotlin/com/otus/mail/feature/inbox/data/ListUnsubscriber.kt)).

## Keeping this page current

A change that adds a network client, a host, or a new trigger for an existing one updates this
page in the same pull request.
