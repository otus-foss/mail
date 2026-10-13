# Notifications

Mail notifies about new mail with a background check. It runs no push service of its own; see
Meta [decision 0015](https://github.com/reazndev/otus-meta/blob/main/docs/decisions/0015-push-notifications.md)
for the planned push modes.

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
- A Notifications settings page with the push modes.
- A notification icon from the design system; the system mail icon stands in.
- iOS background checks.
