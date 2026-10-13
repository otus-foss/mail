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

`core/mail` also holds what the features' data layers share. `MailAccounts` resolves a saved login
to the JMAP account that holds its mail. `NEWEST_FIRST` and `inMailbox` are the `Email/query` sort
and mailbox filter, and `pointer` escapes ids in `Email/set` patch paths (RFC 6901).

Folders use `Mailbox/get` and `Mailbox/set`; rules manage one Sieve script with `SieveScript/get`
and `SieveScript/set` (RFC 9661, `JmapCapability.SIEVE`) after uploading it as a blob. Messages move
with `Email/set` patches. See [Folders and rules](folders.md).

Messages also load `to`, `cc`, `messageId`, and `references`, so a reply can address everyone and
stay in its thread.

The body is the `textBody` parts' values (RFC 8621, section 4.1.4), with each part's `type`. When a
message has no plain alternative, that part is `text/html`; the app reads it as plain text and never
renders it. It drops comments and the content of `script`, `style`, `title`, and `template`, breaks
lines at blocks and `<br>`, removes other tags, and decodes character references, in one pass that
tolerates broken markup. A part longer than the limit comes back cut off with `isTruncated`. When
another part's text follows the cut, the body says "[Part of the message is missing here.]" where
the part stops. When the cut is at the end, the message has `bodyTruncated`: the open message says
only its start was loaded, and a reply or forward ends the quoted text with "[The rest of the
original message is not included.]".

Drafts (`JmapDraftRepository`, RFC 8621 sections 2, 4.6, and 6) live in the mail account:

- Saving finds the mailbox with role `drafts` through `Mailbox/query`, then sends an `Email/set`.
  It creates the Email with keywords `$draft` and `$seen`, the addresses, the subject,
  `inReplyTo` and `references` when set, and a single `text/plain` body (`bodyValues` and
  `textBody`). Email content cannot change, so a second `Email/set` destroys the previous copy.
  Each operation in one call succeeds or fails alone (RFC 8620, section 5.3), so that destroy
  waits until the server has created the new Email; a rejected save keeps the old copy. When the
  destroy fails, the save still succeeds and the old copy stays in Drafts as a duplicate.
- Discarding destroys the Email. A copy that is already gone counts as discarded.
- Failures come back as `DraftException`: no drafts mailbox, forbidden, unreachable, or another
  server problem.
- Sender addresses come from `Identity/get`, only when the mail account and the session advertise
  `urn:ietf:params:jmap:submission`. Without it, or when the call fails, the list is empty.
  Sending (`EmailSubmission/set`) is not implemented.

Mail passes its own storage names to the library's login stores: `otus_accounts` and
`otus.account.credentials` on Android, `otus.accounts` and `otus.mail.account` on iOS. Changing them
would lose saved accounts.

## Checks

Mail's tests use Ktor's mock engine for the inbox, draft, folder, rule, and account flows, including
storage-error recovery and same-named accounts. The library's server integration test lives in
otus-meta.
