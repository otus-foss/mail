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

## Retention and removal

At each start the app removes cached bodies of mail received more than 90 days ago
(`MailServices.trimCache`). Opening such a message fetches its body again. The other kinds stay
until sync replaces or destroys them.

Removing an account calls `MailCache.forget`, which deletes every object and value of that login
before the login itself goes. Removal also cancels in-flight cache work for that account.
Each operation uses an account generation, so an old request cannot repopulate the cache after
removal or write into a later connection with the same login ID.

If credential deletion or its preceding cleanup fails, the retained login can start new cache
operations again. Requests from before the attempted removal remain canceled and cannot write
into the restored account generation. A completed removal stays inactive.
