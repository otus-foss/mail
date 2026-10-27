# Notifications

On Android, Mail receives new-mail pushes through Google Play services built into the app, or a
separate UnifiedPush app such as ntfy. It also checks about every 15 minutes. Android needs no
Otus push server; see Meta
[decision 0015](https://github.com/reazndev/otus-meta/blob/main/docs/decisions/0015-push-notifications.md).

Apple delivery uses an APNs bridge and an embedded iOS notification extension. See
[Apple push delivery](apple-push.md) for configuration, limits and validation. The sections below
describe Android behavior.

## Instant delivery

Settings › Notifications lists Google Play services when it is installed on the device, followed
by installed UnifiedPush apps. Google delivery needs no extra app or Firebase project setup.
The user chooses the service; periodic checks remain the default until they do.

The embedded distributor is `org.unifiedpush.android:embedded-fcm-distributor:3.1.0`. It uses
Play services on the device and contains no proprietary Google SDK, so the same dependency is
included in every Android build. Devices without Play services retain external distributors
and background checks. No public gateway is configured.

For Google delivery, each login's JMAP session must advertise `urn:ietf:params:jmap:webpush-vapid`
and an `applicationServerKey`, as RFC 9749 specifies. Otus passes that key to the connector.
Servers without it show that Google delivery is unsupported and keep background checks.
A malformed key, failed registration, or unreachable server has its own account status. Settings › Notifications lists an account under **Not instant** only while its status is one of these problems; accounts that are instant or still connecting are not listed.
An account that fails does not prevent another account from registering.

The periodic worker checks signing keys again. A changed key replaces that login's registration
and server subscription. An endpoint callback does not retry a failed registration immediately;
the next scheduled check or explicit selection retries it. This prevents a failed Google
registration from creating a work loop. Choosing a service only saves the choice and schedules
work, so server discovery never holds the settings screen open.

Choosing a service registers each account as its own instance, keyed by login id:

1. The selected service hands `MailPushService` an endpoint URL and Web Push keys.
2. `PushSyncWorker` saves them and asks the account's server to push `EmailDelivery` changes
   there, through `PushSubscriptions` from `com.otus:jmap-push`.
3. The server sends a verification push; the worker sends its code back, and the account shows
   "Instant".
4. Each delivery push requests expedited `NewMailWorker` work. Android can still delay it when
   expedited-work quotas are exhausted. The check below then finds and posts the new mail.

The UnifiedPush connector decrypts pushes from either service. A push holds only account ids
and state strings, never mail, so the push app and its server see encrypted change notices only. A server that
refuses push subscriptions shows "The server can't push" and keeps the 15-minute check.
Choosing Off unregisters every account and deletes its server subscription. The periodic run
renews subscriptions before they expire and registers accounts added later.

## Background check

`NewMailWorker` runs about every 15 minutes while the device has a network. Android can stretch
the period in Doze and app standby, so this is not real time. The app schedules the work when it
starts and keeps an existing schedule. WorkManager starts on demand from `OtusMailApplication`.

Each run calls `JmapNewMailChecker`:

- The first run for a login only saves its current Email state, so old mail never notifies.
- Later runs read `Email/changes` since the saved state (RFC 8620, section 5.2), up to 5 pages of
  100 changes, and fetch at most the 50 newest created messages.
- A message notifies when it is in a mailbox with the `inbox` role and has neither `$seen` nor
  `$draft`.
- The new state is saved after the notifications are posted. A login that fails keeps its old
  state, so the next run reports its mail. When the server answers `cannotCalculateChanges`, the
  login starts again from the current state without notifying.

`AndroidMailStateStore` keeps the states in app-private preferences, keyed by login id.

## What the user sees

`MailNotifications` posts one notification per message on the "New mail" channel: the sender as
the title, the subject as the text, and the subject and preview when expanded. Messages are
grouped by account with a summary. Settings › Notifications › **Previews** decides what they show,
saved by `AndroidNotificationPreviewStore`:

- **Use device setting**, the default, posts them with `VISIBILITY_PRIVATE` and a public version
  that says only "New mail". Unlocked, the alert shows the sender and subject. On the lock screen,
  Android's own notification setting decides: it shows the alert in full, shows the public
  version, or shows nothing.
- **New mail only** shows only "New mail", locked or unlocked, without the sender, subject,
  preview, or account.

**Accounts and addresses** opens a page that overrides the choice for one login's mail, or for
that login's mail sent to one of its addresses. Mail counts as sent to an address when To or Cc
names it, or when the topmost `Delivered-To` or `X-Original-To` header does, which covers Bcc and
list mail. A wildcard identity such as `*@example.org` covers every address of its domain that has
no choice of its own. Its row on the Notifications page says how many have a setting of their own,
and says nothing while none do.

`PreviewChoices` keys an address's choice by login id and address, and only that login's mail
reads it. The sender writes To and Cc, so a choice for one account's address must not reach mail
another account receives, and one address under two accounts has a choice in each. Each recipient
of a message counts with its login's choice for that address, or else the login's choice, which
falls back to the choice for all mail. The more private one wins, so naming a "Use device setting"
address cannot reveal mail of an account set to "New mail only". An account's summary says only
"New mail" when any of its alerts does.

The earlier choices carry over: Always and When unlocked become Use device setting, Never becomes
New mail only. Address choices saved before they were kept per login stay under their old
`address:` keys. Until the app has loaded the accounts, they count only when they say New mail
only. Once the accounts load, each moves to every login that lists the address, and choices no
login lists are dropped.

Apple alerts never show message content, so iOS has no Previews setting.
A tap opens the app; opening the message itself is not built yet.

On Android 13 and later, the app asks for the notification permission at launch when an account
is saved. Without it, the check still runs and saves state but posts nothing.

## Not built yet

- Opening the tapped message, and notification actions such as Archive or Mark as read.
- Removing a notification when the message is read elsewhere.
- A device test of Google delivery against a server advertising RFC 9749, including Doze, key
  rotation, and measured delivery delay.
- A device test of instant delivery with ntfy and Stalwart, with measured delay and battery use.
- User-configured gateways for servers without VAPID. Google delivery does not support those
  servers in this implementation.
- Showing when the last push arrived, and a button that sends a test notification.
- A notification icon from the design system; the system mail icon stands in.
- iOS background checks.

## Validation

`GooglePushRegistrationTest` checks device discovery, VAPID registration, unsupported servers,
key rotation, persisted failures, retry behavior, cancellation, and external push apps.
`NotificationSettingsTest` checks choosing Google, switching back to background checks, and
showing why an account cannot use Google delivery. `GoogleNotificationsScreenTest` records and
compares settings in Otus, Light, and Black themes, plus the delivery choice sheet. These tests
use fake accounts and do not prove delivery through Google's servers.
