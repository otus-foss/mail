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
2. `PushSyncWorker` saves them and asks the account's server to push `EmailDelivery` and `Email`
   changes there, through `PushSubscriptions` from `com.otus:jmap-push`. A subscription made with
   other types is replaced, which needs a new verification.
3. The server sends a verification push; the worker sends its code back, and the account shows
   "Instant".
4. Each push tells the open app, through `MailServices.serverChanges`, to reload the folders and,
   while it shows, the inbox, so new mail and read changes from elsewhere show without a pull.
   While Mail is off screen, pushes reload nothing; one reload follows when Mail shows again.
   Each push also requests expedited `NewMailWorker` work. Android can still delay it when
   expedited-work quotas are exhausted. The check below then finds and posts the new mail.
   Pushes that arrive during a check queue one more check after it, not one each; a check covers
   every push that arrived before it started.

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
- A message whose notification is still showing and that `Email/changes` lists as updated or
  destroyed is fetched again. Its notification goes once the message is read, leaves every inbox,
  or is deleted, on this device or another, and the account's summary goes with its last message.
  A push wakes the check on every Email change, so this takes seconds with instant push and up to
  one period with the background check alone.
- The new state is saved after the notifications are posted. A login that fails keeps its old
  state, so the next run reports its mail. When the server answers `cannotCalculateChanges`, the
  login starts again from the current state without notifying, and every showing notification of
  that login is checked again.

`MailStateStore` keeps the states in app-private preferences, keyed by login id.

## What the user sees

`MailNotifications` posts one notification per message on the "New mail" channel: the sender as
the title, the subject as the text, and the subject and preview when expanded. Messages are
grouped by account with a summary. Settings › Notifications › **Previews** › **All mail** decides
what they show, in a `ChoiceSheet` that applies a choice at once, saved by
`AndroidNotificationPreviewStore`:

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
On iOS a tap opens the app; opening the message itself is not built there yet.

### Opening the message

Tapping a message's alert opens that message; tapping an account's summary opens Mail. The alert
carries its login id and Email id to `MainActivity`, which hands them to `MailRequests`. Mail runs
as one activity (`singleTask`), so an open Mail comes to the front and takes the request in place,
and a closed one takes it once it starts. A recreated activity, or an intent the recent apps list
gives back, does not open the message again.

The Inbox then shows its own mail, without a folder pill's, and loads again. The row that lists
the message, alone or in its conversation, opens as a tap would, so Mark read on open applies.
When "Show mail from" hides the message's account, the pages show that account. A message that
left the inbox or was deleted since the alert, on this device or another, is not listed any more,
so a toast says so and the Inbox stays.

### Buttons

An alert that shows its message offers up to three buttons. A "New mail only" alert and the
locked version of an alert offer none, because the user cannot see which message they would
change. A device set to show full alerts on its lock screen shows the buttons there too, so every
button that changes mail asks the user to unlock first (`setAuthenticationRequired`, Android 12
and later). Settings › Notifications › **Buttons** › **Alert buttons** picks them and their order
in the same `OrderedChoiceSheet` the Quick views use; see [settings](settings.md#kinds-of-setting). The
choice is `MailBehavior.notificationActions`, saved with the other behavior settings under
`notification_actions`. The default is **Delete**, **Archive**, **Mark read**, in that order.
Taking every button out leaves alerts without buttons.

The sheet offers every action that can run from an alert without seeing the message:

| Button | `MailAction` | What it does | The account needs |
| --- | --- | --- | --- |
| Delete | `Trash` | Moves the email out of every mailbox into Trash, as the list's Delete does | A `trash` mailbox |
| Archive | `Archive` | Moves the email out of its inboxes into Archive, keeping its other mailboxes | An `archive` mailbox |
| Mark read | `Read` | Adds `$seen` | Nothing |
| Flag | `Flag` | Adds `$flagged` | Nothing |
| Spam | `Spam` | Moves the email out of every mailbox into Spam, as the list's Spam does | A `junk` mailbox |
| Reply | `Reply` | Opens Mail on a reply to the message | Submission (RFC 8621, section 7) |

Mark unread is left out, since alerts are about unread mail. Reply all, Forward, Move, and
Read later need the message or a folder choice on screen, so they are left out too.

`alertButtons` in the inbox's shared action model (`presentation/actions/AlertButtons.kt`) resolves
the choice for each alert: it keeps the chosen
actions an alert can run, drops those the message's account cannot run, keeps the user's order,
and stops at three. It does not fill a dropped button's place with another. `JmapNewMailChecker`
already reads the account's mailbox roles to find its inboxes, so each `NewMail` carries those
roles and whether the account may use submission. An account without an Archive folder never shows
Archive, rather than a button that fails.

Every button but Reply acts without opening Mail: `AlertActionReceiver` removes the alert at
once, and the account's summary with its last alert, then queues expedited `AlertActionWorker`
work for when the device has a network. The worker runs `runAlertButton` from the same shared file,
which calls the by-id changes of `EmailWriter` (`Email/set`, RFC 8621, section 4.6). A button's
label is its action's own label, the one the bar shows; Android keeps only the intents, the worker,
and the semantic actions. Delete, Archive, and Spam first read the account's mailbox roles with
`Mailbox/get`. Like the list's own marks and moves, each change goes into the device's copy at
once and waits in the change queue when the server cannot be reached. A refused change tries again,
up to five runs in all, unless the account was removed, which ends the change at once and leaves
no alert. When the last run fails too, the alert comes back with the same buttons, alerting again,
and says which button failed, such as "Archive didn't work", in place of the account name. Each button carries its alert's contents for that. Delete and Spam only move the
email, never destroy it, so it can be restored from Trash or Spam; alerts have no Undo button.

Reply opens Mail as tapping the alert does, with `MailRequests.reply` instead of `openMessage`.
The Inbox finds the message's row, loads its full body, and opens a reply to it on the compose
page. The alert goes once Mail takes the request, since Android keeps an alert after a button that
opens an app.

The buttons also name their meaning to Android, for watches and cars: `SEMANTIC_ACTION_DELETE`,
`SEMANTIC_ACTION_ARCHIVE`, and `SEMANTIC_ACTION_MARK_AS_READ`. Reply names none, because
`SEMANTIC_ACTION_REPLY` promises an inline reply and this one opens the composer.

Removing an account clears its alerts once its credentials are gone, and stops its pushes while
they still exist; see `DeviceAccountData`.

On Android 13 and later, the app asks for the notification permission at launch when an account
is saved. Without it, the check still runs and saves state but posts nothing.

## Not built yet

- Removing a notification when the message is read elsewhere.
- Opening the tapped message and the alert buttons on iOS. Apple alerts would need the ids in the
  APNs payload, a notification category per set of chosen buttons, built with the same
  `alertButtons`, and the app's `UNUserNotificationCenter` delegate handing them to `MailRequests`
  and `EmailWriter`.
- Undo for Delete and Spam from an alert. The email can be restored from Trash or Spam.
- Telling the user why a button's change failed. The alert that comes back names the button only.
- A device test of Google delivery against a server advertising RFC 9749, including Doze, key
  rotation, and measured delivery delay.
- A device test of instant delivery with ntfy and Stalwart, with measured delay and battery use.
- User-configured gateways for servers without VAPID. Google delivery does not support those
  servers in this implementation.
- Showing when the last push arrived, and a button that sends a test notification.
- A notification icon from the design system; the system mail icon stands in.
- iOS background checks.

## Validation

`NotificationActionsTest` checks the default buttons, the limit of three, that actions an account
cannot run are dropped and the rest keep their order, that no choice gives no buttons, and saving
the choice. `AlertActionsTest` checks that tapping an alert names its message and a summary names
none, that only alerts that show the message have buttons, that they are the chosen ones the
account can run, that Reply opens Mail on a reply, that every button but Reply asks to unlock,
that a button clears its alert and queues the change, that a failed change brings the alert back
saying so, and that a change for a removed account stops without one. `EmailShortcutsTest` checks
the `Email/set` patches against a mock server, an account without an Archive, Trash, or Spam
folder, and a removed login. `MessageListingTest` checks finding the row of a
message inside a conversation. `AlertRemovalTest` checks that removing an account clears only its
alerts and forgets its push registration before its credentials go. Opening the message in the
running app, on a cold start, and after the message was deleted needs a device check, and so does
unlocking before a button runs.

`GooglePushRegistrationTest` checks device discovery, VAPID registration, unsupported servers,
key rotation, persisted failures, retry behavior, cancellation, and external push apps.
`NotificationSettingsTest` checks choosing Google, switching back to background checks, and
showing why an account cannot use Google delivery. `GoogleNotificationsScreenTest` records and
compares settings in Otus, Light, and Black themes, plus the delivery choice sheet. These tests
use fake accounts and do not prove delivery through Google's servers.
