# Notifications

Mail notifies about new mail within seconds through a UnifiedPush app the user chooses, such as
ntfy, and checks about every 15 minutes either way. Otus runs no push server; see Meta
[decision 0015](https://github.com/reazndev/otus-meta/blob/main/docs/decisions/0015-push-notifications.md).

## Instant delivery

Settings › Notifications lists the installed UnifiedPush apps. Choosing one registers each
account with it as its own instance, keyed by login id:

1. The push app hands `MailPushService` an endpoint URL and Web Push keys.
2. `PushSyncWorker` saves them and asks the account's server to push `EmailDelivery` changes
   there, through `PushSubscriptions` from `com.otus:push`.
3. The server sends a verification push; the worker sends its code back, and the account shows
   "Instant".
4. Each delivery push starts `NewMailWorker` at once as expedited work, which runs within seconds,
   even in Doze. The check below then finds and posts the new mail.

The UnifiedPush connector decrypts pushes. A push holds only account ids and state strings,
never mail, so the push app and its server see encrypted change notices only. A server that
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
grouped by account with a summary. The lock screen shows only "New mail" and the account name.
A tap opens the app; opening the message itself is not built yet.

On Android 13 and later, the app asks for the notification permission at launch when an account
is saved. Without it, the check still runs and saves state but posts nothing.

## Not built yet

- Opening the tapped message, and notification actions such as Archive or Mark as read.
- Removing a notification when the message is read elsewhere.
- A device test of instant delivery with ntfy and Stalwart, with measured delay and battery use.
- Showing when the last push arrived, and a button that sends a test notification.
- A notification icon from the design system; the system mail icon stands in.
- iOS background checks.
