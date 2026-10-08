# JMAP

Mail uses the `com.otus:jmap` library from otus-meta: a provider-independent client for RFC 8620
(core) plus protected login storage. Its API, security rules, protocol coverage, and tests are
documented in
[otus-meta's JMAP guide](https://github.com/reazndev/otus-meta/blob/main/docs/jmap.md). This
guide covers what Mail builds on top of it.

## Mail on top of it

`core/mail` defines `MailboxType` and `EmailType` (RFC 8621). Connecting fetches the session, checks
the mail account with a `Mailbox/get`, and saves the login; reconnecting the same user replaces it.
The inbox finds the mailbox with role `inbox`, then runs `Email/query` and `Email/get` in one request
(newest 50, text bodies up to 20 000 bytes per part). Each login loads in parallel and fails alone,
with a reason. `OtusMailApplication` owns one `MailServices` per process. Mail and the first release
use the same storage names, so earlier accounts keep working.

Folders use `Mailbox/get` and `Mailbox/set`; rules manage one Sieve script with `SieveScript/get`
and `SieveScript/set` (RFC 9661, `JmapCapability.SIEVE`) after uploading it as a blob. Messages move
with `Email/set` patches. See [Folders and rules](folders.md).

Mail passes its own storage names to the library's login stores: `otus_accounts` and
`otus.account.credentials` on Android, `otus.accounts` and `otus.mail.account` on iOS. Changing them
would lose saved accounts.

## Checks

Mail's tests use Ktor's mock engine for the inbox, folder, rule, and account flows, including
storage-error recovery and same-named accounts. The library's server integration test lives in
otus-meta.
