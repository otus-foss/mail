# Apple push delivery

Mail can receive encrypted JMAP change notices through APNs without installing another app.
This implementation is on `feat/apple-push`, stacked on the Google delivery branch. It needs
both a deployed bridge and a signed iOS build configured for that bridge. Neither is provisioned
by the pull request.

## Delivery

1. The user chooses Apple Push in Settings, Notifications and grants the iOS permission.
2. Mail registers its APNs device token with the bridge and creates a random route for each login.
   The bridge receives no login name, server password, Web Push private key or authentication secret.
3. Mail subscribes to `EmailDelivery` using the existing JMAP subscription library and Web Push
   encryption keys. It leaves out `Email`, which Android adds to clear alerts for mail read
   elsewhere, because the notification extension shows an alert for every notice. The server posts encrypted notices to the route's HTTPS endpoint.
4. During subscription verification, the bridge buffers notices without sending an alert. Mail
   polls the encrypted notices, decrypts the verification code and returns it to the JMAP server.
   The shared library pauses alerts before every new subscription, including replacements.
5. Once verified, the bridge stores each encrypted notice briefly and sends APNs a generic alert
   with an opaque message identifier. The notification extension downloads and decrypts the notice.
   It shares only push keys with the app through a dedicated Keychain group.
6. The alert says "New mail" and opens Otus. This version does not show sender or subject previews,
   fetch mail inside the extension, or navigate to a particular email. Alerts follow the server
   delivery state; they do not apply the Android checker's unread-inbox or muted-thread filters.
   Mail fetches through its
   normal account connection when the user opens or refreshes the inbox.

This is a native APNs integration. It does not use Firebase, a UnifiedPush distributor or Safari
Web Push. The bridge accepts RFC 8291 encrypted Web Push; VAPID is not required for this route.
The random push URL is a bearer capability and must stay private.

## Bridge deployment

The Python service in `push_bridge/` is owned by Mail until another app needs it. It runs behind
an HTTPS reverse proxy. Use a separate deployment for each app bundle ID and APNs environment.
The SQLite database must be on persistent local storage, writable only by the service account.

Install the pinned dependencies and run the service:

```sh
python3 -m venv .venv
.venv/bin/pip install -r push_bridge/requirements.txt
export APNS_KEY_FILE=/run/secrets/otus-apns.p8
export APNS_KEY_ID=YOUR_APPLE_KEY_ID
export APNS_TEAM_ID=YOUR_APPLE_TEAM_ID
export APNS_TOPIC=com.otus.mail
export APNS_ENVIRONMENT=sandbox
export OTUS_PUSH_DATABASE=/var/lib/otus-push/push.sqlite
.venv/bin/gunicorn --bind 127.0.0.1:8080 --workers 2 --threads 4 push_bridge.server:application
```

The `.p8` key comes from the Apple Developer account that signs the app. Keep it on the server;
never add it to the app, repository or image. `APNS_ENVIRONMENT=sandbox` is for development-signed
builds. TestFlight and App Store builds use `production`. The service signs ES256 provider tokens,
reuses HTTP/2 connections and refreshes the signing token every 30 minutes.

A Dockerfile is included. Build it from the repository root with
`docker build -f push_bridge/Dockerfile -t otus-push .`. Mount the key read-only and a persistent
volume at `/data`. The container runs as UID 10001. Publish its port on localhost behind TLS.

The reverse proxy is part of the deployment. It must limit registration requests, cap request
bodies at 4096 bytes and disable access logs containing capability URLs. Limit per IPv4 address
and per IPv6 /64, since one host can usually use every address in its /64. The service limits each
APNs token to 32 routes, the database to 10,000 routes, each active route to 32 pending messages,
and each inactive route to 4. An example Nginx configuration is in `push_bridge/nginx.conf`; it
keys both limits on the IPv4 address or the IPv6 /64. Supply your certificate paths and domain.
Do not expose the backend port directly. Public registration has no account login or Apple App
Attest check; configure ingress limits before making it publicly reachable.

A route that is not active, such as a new one waiting for its verification push, expires one hour
after its last update, so registrations nobody completes free their quota slot quickly. Active
routes expire after eight days without upkeep. Ciphertext expires after at most one hour, or
sooner when acknowledged. Deleting a route deletes its pending messages. APNs rejects an invalid
device token with a permanent error; the bridge revokes that route. Transient APNs errors, and
APNs errors whose body cannot be read, return 503 so the JMAP sender can retry. SQLite state
survives restarts. Expired routes and messages are never served; deleting them uses an index on
the expiry time and runs at most once a minute, when a route is registered or a push is stored.
No email content is decrypted by the bridge. It can see device tokens, opaque route identifiers,
message sizes, delivery timing and source IP addresses.

## Apple build setup

In the Apple Developer account, enable Push Notifications for the app ID and create an APNs
signing key. Register the notification extension ID and allow both targets to use the same
Keychain access group. Set `TEAM_ID` and your owned `BUNDLE_ID` in
`iosApp/Configuration/Config.xcconfig`.

Set the bridge origin in that config. Xcode treats `//` as a comment, so use this spelling:

```text
PUSH_BRIDGE_URL = https:/$()/push.example.org
```

The app and extension read the same origin from their Info.plists. An empty value leaves Apple
Push unavailable in the settings page. No server is selected by default. Debug sets the APNs
entitlement to `development`; Release sets it to `production`. Match the bridge deployment to the
signing environment. The extension is an embedded Xcode target with its own provisioning profile.

The app stores route capabilities, device tokens and push keys in a shared Keychain group using
`AfterFirstUnlockThisDeviceOnly`. The extension has no access to the mail credential service.
Normal mail credentials keep their existing storage and names.

## Lifecycle and limits

Mail registers the device token again on foreground activation, renews its routes, and replaces
routes after a token change. While foregrounded it polls the bridge every 15 seconds only while an
account waits for verification or the last attempt failed; once every account is settled it only
watches for added or removed accounts. It requests background refresh roughly twice daily for
renewal. iOS decides whether and when background refresh runs; this is not a
15-minute polling promise. Open Mail regularly. If refresh is denied for longer than the server's
subscription lifetime, delivery stops until Mail opens and renews or replaces the subscription.

Turning delivery off unregisters from APNs immediately and tries to revoke bridge routes. Offline
revocations remain saved for retry. Removing one account requires bridge revocation before its
local capability is discarded; an unreachable bridge can therefore delay account removal.
The next foreground check also cleans up routes for logins no longer present. Failure to read the
login store does not count as removing every account.

The extension has a time limit and may not run before the first device unlock or when alerts are
disabled. Its fallback remains a generic alert. APNs may delay, coalesce or drop pushes; notifications
are hints, not the mail store. A generic alert can remain when a notice cannot be downloaded or
validated. This version does not promise exactly-once notification presentation.

## Validation

`./gradlew checkAll` includes bridge unit tests, portable registration/verification tests and
Android rendering tests for the shared Apple settings page. Install `requirements-quality.txt`
first, as for the other quality tools. CryptoKit interoperability tests use the RFC 8291 published
vector, plus corruption and wrong-key cases:

```sh
swift test --package-path iosApp
mint run swiftformat --lint iosApp
mint run swiftlint lint --strict
xcodebuild -project iosApp/iosApp.xcodeproj -scheme iosApp -configuration Debug \
  -sdk iphonesimulator -destination 'generic/platform=iOS Simulator' \
  ARCHS=arm64 CODE_SIGNING_ALLOWED=NO build
```

The Swift tests and Xcode build need macOS and Xcode. Linux Kotlin iOS compilation does not
validate the Swift bridge, app signing or extension embedding. The macOS CI job compiles the
simulator app but runs no Swift tests, so run these commands on a Mac before marking the PR ready.

On a signed device build, test permission denial and re-enabling, initial verification without
an alert, new mail while locked, notification taps, bridge outages, token changes, expired
subscriptions, account removal and turning delivery off while offline. Use both sandbox and
production APNs deployments before release.
