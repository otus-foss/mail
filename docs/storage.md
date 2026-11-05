# Mail storage

Mail keeps a copy of each login's mail on the device, so lists and opened messages show before the
server answers and stay readable offline. `MailCache` in `core/cache` is the contract. It stores
server objects as JSON text by login, kind, and id, plus named text values such as JMAP state
strings. It knows nothing of JMAP; the callers encode and decode the objects.

## What it holds

- Mailboxes, with their counts.
- Emails with the properties a list row shows, and the smaller member emails a conversation counts.
- Threads, as lists of email ids.
- Bodies of opened messages: their text and HTML body values.
- Address books and contact cards (RFC 9610), under `<account>/<id>`, since one login may reach
  several contacts accounts. Each account's entry keeps its name, whether it is shared, and the
  `AddressBook` and `ContactCard` states the cached objects match.
- Sync values per login, such as the `Email` and `Thread` states and the loaded listings.

## Where it lives

`SqlMailCache` keeps the cache in one SQLite database through SQLDelight. The schema is in
`shared/src/commonMain/sqldelight/com/otus/mail/core/cache/`. Every read and write runs on
`Dispatchers.IO`, and each `write {}` is one transaction, so a failed write saves nothing.

- Android opens `otus_mail_cache.db` in the app's private databases folder. The manifest sets
  `allowBackup="false"` and its backup and data extraction rules exclude every domain, so neither
  cloud backup nor device transfer copies the file.
- iOS opens `otus_mail_cache.db` in the app's Caches directory. iOS leaves that directory out of
  backups and may empty it when the device runs low on space; Mail then loads from the server again.
- Tests, previews, and any platform without a driver use `InMemoryMailCache`.

The file is not encrypted. Only the app-private storage of each platform protects it. Login
secrets never go in it; they stay in the platform's protected login store.

## Attachments on the device

Attachments are files on disk, not cache rows. `AttachmentCache` in `feature/inbox/data` streams
each one from the account's `downloadUrl` (RFC 8620, section 6.2) through Meta's
`JmapConnection.downloadStream`, which hands over 64 KiB pieces as they arrive. No piece outlives
its write, so a file of any size downloads without the JMAP client's 16 MiB response limit. A
server that sends more than the size the message gives for the part, its decoded size (RFC 8621,
section 4.1.4), is stopped, so it cannot fill the disk.

- `core/files/DownloadFolder` is the platform contract. A file lives in `<group>/<id>/` under its
  own name, so the app that opens it sees that name. The group is the SHA-256 of the login id and
  the id the SHA-256 of the blob id and file name, so names from the sender never become paths.
- While a file downloads, its bytes go to a `.partial` file that nothing else reads. A failure,
  a cancel, or a server that sends too much deletes it and keeps any earlier copy. One file
  downloads once at a time, so a thumbnail and an Open of one file share one download.
- The Read view shows a sticky toast with the share received, such as "Downloading report.pdf ·
  42%", once bytes arrive. Its Cancel stops the download. After a file opens, its toast offers
  **Keep offline**, or **Remove** for a kept file, and a kept file's row says "Offline" after its
  size.
- Android keeps the folder in `files/attachments`, in the app's private files, which the backup
  and data extraction rules leave out like the rest. Open lends the file itself, read-only,
  through the `FileProvider` path `attachments/`; Save streams a copy into Downloads.
- iOS keeps it in `Application Support/attachments`, which iOS does not empty when space runs low.
  The folder is marked to stay out of backups, and its files take the protection class
  `NSFileProtectionCompleteUntilFirstUserAuthentication`, the same as the login Keychain items.
- Tests, previews, and any platform without a folder use `InMemoryDownloadFolder`.

The files are not encrypted beyond the platform's own storage protection.

## Following the server

Each refresh brings the cache up to date with as little as the server allows:

- `Email/changes` and `Thread/changes` name the emails and threads to read again.
- `Mailbox/changes` patches the cached mailboxes from the saved `Mailbox` state, and a `Mailbox/get`
  of only the created and updated ones fills them in. Without a saved state, or when the server
  cannot follow the changes, one `Mailbox/get` reads them all.
- A list whose `Email/query` said `canCalculateChanges` keeps its query state. The next refresh
  asks `Email/queryChanges` (RFC 8620, section 5.6) up to the list's last row and splices the
  removed and added ids into it. A list without a state, one whose changes the server refuses, or
  one left with less than a page while the server has more, runs `Email/query` again.
- Contacts follow `AddressBook/changes` and `ContactCard/changes` in one request, with a `/get`
  of the created and updated objects by result reference. A type without a saved state, whose
  changes the server refuses (such as `cannotCalculateChanges`), or with more changes than one
  call returns, is read anew: every book, or every card a page at a time. When the server cannot
  be reached, the cached contacts show and the login counts as failed.
- A full refresh saves its time under `synced:at`. When an account cannot load, the inbox notice
  adds when its mail was last updated, such as "Last updated 14:32".

## Changes waiting for the server

Marks and moves go through `EmailChangeQueue`. Each change is saved by login and email before it
is sent, shows in the cache at once, and counts as made. A later change of the same email merges
into the waiting patch. The patches only set or remove keys, so sending one twice changes nothing
more.

- The waiting changes go to the server before each refresh, and after it they show again on top
  of what the server sent.
- A change the server refuses is dropped, and its email leaves the cache, so the next refresh
  reads it again. A server that is down for now (`serverUnavailable`, `serverFail`, or
  `serverPartialFail`, RFC 8620, section 3.6.2) refuses nothing: the change waits. So does an
  email the server will not change for now (`rateLimit`, RFC 8620, section 5.3).
- Every login's changes share one stored value, so one lock covers each read and rewrite of it,
  and changes made on two accounts at once both stay.
- Android keeps the queue in the `otus_mail_pending_changes` shared preferences file, written at
  once, and `OutboxWorker` sends it once the network is back. iOS keeps it in user defaults.
- Removing an account drops its changes, before its login is deleted and again after, so a change
  made in between does not wait for the same account to be added again. Deleting, muting, and
  newsletter cleanup still need the server at once.

Contact changes wait in `ContactSync` the same way. A new card is kept as sent before it goes.
When that request gets no answer, the server may have stored the card anyway, so the next try
first looks in the book for a card with its uid (`ContactCard/query`, RFC 9610, section 3.3),
leaving out cards that other waiting changes are about. When it finds one, it updates that card
with any change made since instead of creating a second card.

Only a missing connection or sign-in, or a server that asks to be tried later (HTTP 408, 429,
502 to 504), makes the contact queue wait. A method error, a `SetError` such as a
`ContactCard/set` `notCreated`, or another 4xx request problem refuses that change alone: it is
set aside as not saved and the queue goes on. An answer that says neither, such as HTTP 500 or a
reply the app cannot read, passes that card over for the rest of the flush, together with the
changes that need it, and sets it aside after three tries. Two such failures in a row end the
flush, since then the server looks at fault. Tries and refusals live in memory, so a restart sends
set-aside changes again.

An import keeps each card's file `UID` unless the target account already holds it in any book,
or an earlier card of the same file has it; then, or when the card has none, the card gets a new
`urn:uuid:` uid. A server may allow a uid once per account, and RFC 9610 lets one card belong to
several books, so a second card with the same uid is refused. A file card with the uid of a card
already in the target book counts as that card, like a matching address.

## Retention and removal

At each start the app removes cached bodies of mail received more than 90 days ago
(`trimCache` in `app/CacheUpkeep.kt`). Opening such a message fetches its body again. The other kinds stay
until sync replaces or destroys them.

The same start-up trim, `AttachmentRetention.trim`, deletes `.partial` files that an earlier process left, and downloaded
attachments that are not kept offline and were last used more than 7 days ago. Opening or
finding a file counts as a use. After each download, unkept files also go, least recently used
first, while together they take more than 256 MB; the file just downloaded always stays. Files the
user keeps offline are never trimmed: they stay through restarts until the user removes them or
removes the account.

Removing an account calls `MailCache.forget`, which deletes every object and value of that login,
and `AttachmentRetention.forget`, which deletes every downloaded file of that login, kept or not,
before the login itself goes. Removal also cancels in-flight cache work for that account.
Each operation uses an account generation, so an old request cannot repopulate the cache after
removal or write into a later connection with the same login ID. Reconnecting a login only
starts a new generation and keeps its cached mail.

If credential deletion or its preceding cleanup fails, the retained login can start new cache
operations again. Requests from before the attempted removal remain canceled and cannot write
into the restored account generation. A completed removal stays inactive.

## Storage failures

SQLite disk-full, I/O, corruption, permission, read-only, and lock errors become
`CacheStorageException`. Cancellation and programming errors propagate unchanged. App wiring
switches a disk cache with a permanent failure to an empty memory cache for the rest of the
process. Reads then act as cache misses, and server sync fills the memory cache. The database is
retried after restart.

`SQLITE_BUSY` and `SQLITE_LOCKED` mean another connection holds the database, so they are marked
transient. A transient error fails only that operation, and callers handle it like any other
cache error. The next operation uses the disk cache again.

A download folder that cannot write, such as on a full disk, throws `DownloadFolderException`. The
download then fails like a server failure: the file shows "Couldn't download", nothing partial
stays, and the next tap tries again. Account removal reports an account-storage error when it
cannot delete the login's files.

A failed inbox write raises the existing recoverable storage error so the loaded list stays on
screen until retry. Fresh message bodies and folder lists still open when saving their local copy
fails. Confirmed server mutations remain successful even if their cache patch fails. Account
removal always attempts disk cleanup and reports an account-storage error if that cleanup fails.
