# Network destinations

This page lists every host Otus Mail contacts and what makes it do so. Each request also shows the
host the device's IP address. Rows say what else goes out. Audited on 2026-11-05 against Mail
`main` and Meta `main`, since Mail pins Meta 0.10.0 and Meta has no tag for it in the local clone.
Anything added later belongs in this table.

Otus has no analytics, crash reporting, advertising, or account service of its own. The only
Otus-operated host is the optional Apple push bridge on iOS, and an Android build never contacts
it.

## Summary

| Destination | When | Turns off with |
| --- | --- | --- |
| Your JMAP server | Always, once an account exists | Remove the account |
| `api.github.com`, then the APK host | Nightly build only, at launch and on Install | Use a non-nightly build |
| Sender websites | Website icons on, source Sender websites | Website icons off, or pick another source |
| `icons.duckduckgo.com` | Website icons on, source DuckDuckGo | Website icons off, or pick another source |
| Your Bulwark server | Website icons on, source Bulwark | Website icons off, or pick another source |
| Contact photo hosts | Website icons on | Website icons off |
| Hosts in message HTML | A message shows remote images | Remote images: Never |
| Your push service, Google | Instant delivery chosen | Settings › Notifications › Off |
| Apple push bridge | iOS with Apple Push chosen | Settings › Notifications › Off |

## Your JMAP server

Mail contacts the server at the address the user typed when adding an account. Every request goes
to that origin, plus the `downloadUrl` and `uploadUrl` origins the server advertises. The
credentials go only to the session origin and to origins the user trusted at sign-in.

| Request | Trigger | Data sent |
| --- | --- | --- |
| `https://<host>/.well-known/jmap`, or the full session URL the user gave | Sign-in, and whenever the app needs a fresh session | Login name and password or token, as an `Authorization` header |
| The session's `apiUrl` | Opening the app, pull to refresh, every action on mail, folders, contacts and identities, and the push worker | Credentials and the JMAP method calls |
| The session's `downloadUrl` | Opening an attachment, an inline image, an HTML body that the list did not carry, or a contact photo stored as a blob | Credentials and a blob id |
| The session's `uploadUrl` | Attaching a file or sending a message | Credentials and the file |
| `PushSubscription` calls on `apiUrl` | Choosing instant delivery, and the periodic renewal | The push endpoint URL and its encryption keys |

Redirects are never followed with credentials. A redirect to another origin fails the request.
Mail only accepts HTTPS addresses.

Background work uses the same server. `NewMailWorker` runs about every 15 minutes while the device
has a network and asks for `Email/changes` since the saved state. It is scheduled at app start
in `OtusMailApplication` and has no switch. Removing every account stops the calls. The
[notification guide](notifications.md) describes the checks.

## Nightly update feed on GitHub

Only the nightly build contacts GitHub. The other builds have no installer and never check.

| Request | Trigger | Data sent |
| --- | --- | --- |
| `GET https://api.github.com/repos/reazndev/otus-mail-nightly/releases?per_page=30` | Each launch of the nightly app | Nothing beyond the request. No token, no device id, no installed version |
| The `browser_download_url` of the newest release's APK, on `github.com` | The user taps Install update | Nothing beyond the request |

GitHub sees the device's IP address and the time of each launch. The check is silent when it
fails. There is no setting for it, because the nightly build exists for testers who want updates.
See [nightly builds](nightly.md).

## Sender avatars

The inbox, Read view, account avatars, and folder rules resolve a picture for each sender. What
happens depends on Settings › Privacy & security › Avatars. With **Website icons** off, which is
the default, Mail contacts only the user's own JMAP server for contact photos and sends nothing
anywhere else. The sender gets initials.

Website icons turns on four things at once: website icons, linked contact photos, brand logos
through a Bulwark server, and website icons for contacts styled to show one. **Icons from** then
picks who is asked. A miss never falls back to another source.

Only a registrable domain goes out, such as `example.org` and never `news.mail.example.org` or an
address. The domain comes from the bundled Public Suffix List, so no lookup is needed to find it.
Domains that are not public, such as `.local`, `.internal` or a bare IP, are refused. Every request
uses HTTPS on port 443 without credentials, follows at most three redirects, and checks each one
against the same rules.

### Sender websites

This is the default source.

| Request | Trigger | Data sent |
| --- | --- | --- |
| `GET https://<domain>/` | A sender with no contact photo is shown and the domain has no cached icon | Nothing beyond the request. Mail reads the first 64 KiB for `<link rel="icon">` tags |
| The icon URLs the page names, up to 8 | The page answered | Nothing beyond the request |
| `https://<domain>/apple-touch-icon.png`, `favicon.png`, `favicon.ico` | The page named no usable icon | Nothing beyond the request |

The sender's website learns the device's IP address and that mail from its domain was listed. The
whole lookup times out after 8 seconds, and a miss is retried after a day. A found icon is cached in the app's private cache for up to a month.

### DuckDuckGo

`GET https://icons.duckduckgo.com/ip3/<domain>.ico`, when a sender with no contact photo is shown
and the domain has no cached icon. DuckDuckGo receives the sender's domain and the device's IP
address. Sender websites receive nothing. A miss is retried after 5 minutes, and a found icon stays cached for up to a month.

### Bulwark

Bulwark is a webmail server the user hosts and enters as an address. Mail asks it two routes:

| Request | Trigger | Data sent |
| --- | --- | --- |
| `GET <address>/api/favicon?domain=<domain>` | A sender with no contact photo is shown and the domain has no cached icon | The sender's domain. No credentials |
| `GET <address>/api/bimi?domain=<domain>` | The same, and the message passed DMARC for that domain | The sender's domain. No credentials |

Mail contacts only the Bulwark address. That server then fetches from DuckDuckGo and from the
sender's BIMI record, so the sender never sees the device. An address that is not HTTPS on the
default port at a public host, or that has a query or fragment, loads nothing.

The BIMI route is the only BIMI lookup, and the device never queries BIMI DNS records itself. The
device does not check DMARC either. It reads the `Authentication-Results` header that the user's
server wrote and asks for a logo only when the server reports `dmarc=pass` for the From domain.

### Contact photos

A contact card can link a photo by URL instead of embedding it. With Website icons on, Mail fetches
that URL as written, again over HTTPS on port 443 and without credentials. The host sees the
device's IP address and that the contact was shown. With it off, Mail reads only photos stored as
blobs on the JMAP server or embedded as `data:` URIs.

## Remote images in message bodies

Message HTML renders in a WebView with scripts, file access, and content access off. While the
view blocks network loads, it contacts nothing.

Settings › Privacy & security › **Remote images** decides when a message may load images from
other servers:

- **Never** keeps them blocked until the user taps the placeholder for that message.
- **In your contacts**, the default, loads them automatically for senders who are in the user's
  contacts or on the Allowed senders list, and asks for everyone else.
- **Always** loads them for every message.

A loaded image requests its URL from whatever host the sender chose. That host sees the device's IP
address, the time, the image's path and query, which senders can make unique to the recipient, and
a User-Agent that names neither the device model nor its build. Android's Safe Browsing is off, so
no image address goes to Google. Inline images that belong to the message come from the JMAP server
and are not remote.

Tapping a link hands the address to the system browser, and Mail sends nothing itself.

## Push notifications

Instant delivery is off until the user picks a service in Settings › Notifications. Without it, the
15-minute check above is all that runs.

The device does not call the push endpoint. The user's JMAP server posts to it, and the device
receives the result through the chosen service.

| Service | Hosts | What the host sees |
| --- | --- | --- |
| Google (embedded distributor) | Google's FCM, through Google Play services | The device, plus encrypted change notices |
| A UnifiedPush app such as ntfy | Whatever server that app is set to | The device or the app, plus encrypted change notices |
| Apple push, iOS only | The Apple push bridge named in `PUSH_BRIDGE_URL`, then APNs | See below |

Every push is encrypted end to end (RFC 8291). It holds account ids and state strings and never
mail, so a push service sees ciphertext, its size, and its timing. Choosing **Off** unregisters
each account and deletes its subscription on the server.

### Apple push bridge

`PUSH_BRIDGE_URL` in `iosApp/Configuration/Config.xcconfig` is empty in the repository. A build
without it never contacts a bridge. When set, it names an HTTPS origin the operator runs, and
Mail sends it these requests under `/v1/`:

| Request | Trigger | Data sent |
| --- | --- | --- |
| `POST routes` | Choosing Apple Push | The APNs device token and a key id |
| `PATCH routes/<id>` and `DELETE routes/<id>` | Pausing or resuming alerts, turning Off | The route id and its secret |
| `GET routes/<id>/messages` and `DELETE routes/<id>/messages/<id>` | Verifying a subscription, and acknowledging notices | The route id and its secret |
| `GET messages/<id>` | The notification extension receives an alert | An opaque message id |

The bridge sees the device token, the encrypted notices, and their timing. It receives no login
name, server password, or private key. See [Apple push delivery](apple-push.md).

## Not network calls

Several things look like network use and are not.

- The Public Suffix List ships inside the app and is never downloaded.
- Opening an attachment saves the blob from the JMAP server. Sharing or printing hands it to the
  system.
- The Gradle and Maven hosts, GitHub Actions, and `github.com/reazndev` links in the docs matter
  only to builds and developers.

## Keeping this page current

Add a row here in the same pull request as any new request to a host other than the user's JMAP
server. Say when it runs, what it sends, and which setting turns it off. A new setting that turns a
destination off belongs in the summary table.
