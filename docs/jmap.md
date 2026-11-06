# JMAP

Mail uses the `com.otus:jmap` library from otus-meta: a provider-independent client for RFC 8620
(core) plus protected login storage. Its API, security rules, protocol coverage, and tests are
documented in
[otus-meta's JMAP guide](https://github.com/reazndev/otus-meta/blob/main/docs/jmap.md). This
guide covers what Mail builds on top of it.

## Mail on top of it

`core/mail` defines `MailboxType` and `EmailType` (RFC 8621). Connecting fetches the session, checks
the mail account with a `Mailbox/get`, and saves the login; reconnecting the same user replaces it.
`OtusMailApplication` owns one `MailServices` per process. Mail and the first release use the same
storage names, so earlier accounts keep working.

`core/mail` also holds what the features' data layers share. `JmapConnections.mailAccount` resolves
a saved login to the JMAP account that holds its mail, through otus-meta's account lookup.
`NEWEST_FIRST` and `inMailbox` are the `Email/query` sort and mailbox filter; every other filter is
a `JmapFilter`. `MailFailure` and `toMailFailure` read a JMAP failure once for every feature; the
folder, rule, send, and draft failures start from it. Batches use the session's `getBatch`,
`setBatch`, and `callBatch`, and optional calls `jmapCallOr`.

Account removal follows otus-meta's `LoginLifecycle`: the cache, the waiting changes, downloaded
attachments, device settings and push (`DeviceAccountData`), and avatars each delete their part
while the login's credentials still exist, and the cache opens again if removal stops.

The inbox's filters run on the server. `emailConditions` turns a page's `InboxFilters` into one
list of `Email/query` conditions per login, joined with `AND` and with the page's mailboxes.
The Unread and Read status is `notKeyword` and `hasKeyword` with `$seen`; Flagged uses
`$flagged` and Forwarded `$forwarded`. Attachments is `hasAttachment`, Read later `inMailbox` with
that login's Read later folder, and Newsletters an `OR` of `header` conditions for `List-Id` and
`List-Unsubscribe` (RFC 2919, RFC 2369), which a server may not support. From is an `OR` of `from`
conditions over every address of the chosen people. The user's own addresses (Sent to) and other
recipients (To) become an `OR` of `to`, `cc`, and `bcc` conditions in the chosen Received in
fields, To and Cc by default; when only the fields differ from the default, the login's own
addresses stand in. Size is `minSize`, and dates are `after` and `before`, counted from local
midnight (Last 7 and Last 30 days from midnight six or 29 days before today, This year from
1 January) and sent as UTC. Every bound is a day's start, so a list's filter, and with it the key
of its saved listing, stays the same all day. Chosen folders, with their subfolders unless
"Only this folder" is on, replace the page's mailboxes; no filter leaves a folder out with `NOT`.
A login whose account, own addresses, folders, or Read later folder are not chosen is not asked at
all. Oldest first sorts `receivedAt` ascending.

Some choices depend on the whole thread, which no condition can express, so the device checks them
on the loaded conversations. Each row reads its thread's emails (`Thread/get` and `Email/get` with
the `List-Id` and `List-Unsubscribe` headers) and keeps who wrote the newest one, Sent included and
drafts, Trash, and Junk left out unless shown. Awaiting reply keeps threads whose newest email is
from one of the login's own addresses, and it also queries the Sent mailbox, because a thread the
user started may have no email elsewhere. Needs my reply keeps threads whose newest email is
someone else's, sent to the user directly or in a thread the user wrote in, and not from a list.
Conversations keeps threads with emails both in and outside Sent, and Calendar invites keeps mail
with a `text/calendar` or `application/ics` attachment or an `.ics` or `.ifb` file. With any of
these on, the panel shows no count, and since a page can then show only a few rows, the list loads
up to four more pages on its own after a new filter or scope; scrolling loads the rest. Lists the
server filters alone load only as they scroll. The same check gives a row the calendar mark: a
conversation with an invitation shows it, and the paperclip appears only for its other files.
Otherwise its count is the same query with `limit: 0`, `calculateTotal: true`, and
`collapseThreads`, one per login, summed; each panel session reads each login's mailboxes once.
The From and To lines also search beyond the loaded rows as the user types: `Email/query` with a `from`, or `to` and
`cc`, text condition and `collapseThreads`, the newest 20, then each distinct address, plus the
address book's contacts.

A newsletter's older mail (`JmapNewsletters`) is the newest 100 emails before it with a `header`
condition on its `List-Id`, or, without one, a `from` condition on its sender. A server may match
these loosely, by substring or word, and `from` matches names too (RFC 8621, section 4.4.1), so
the device reads each match's `List-Id` and `from` and keeps only mail with the same list id, or
from the same address, ignoring case. Archive older moves only those.

Folders use `Mailbox/get` and `Mailbox/set`; rules manage one Sieve script with `SieveScript/get`
and `SieveScript/set` (RFC 9661, `JmapCapability.SIEVE`) after uploading it as a blob. Messages move
with `Email/set` patches. See [Folders and rules](folders.md).

Messages also load `to`, `cc`, `messageId`, and `references`, so a reply can address everyone and
stay in its thread.

A row (`data/Conversations.kt`) is the thread's newest email in the shown mailbox, with a
`MailConversation`. Its count, participants, and newest email leave out drafts and Trash or Junk
mail unless the list shows that mailbox; Sent mail counts. The row is unread when any of its emails
in the mailbox is, flagged when any counted email is, and its `mailboxIds` cover the whole thread.
When several chosen mailboxes hold one thread, their rows merge into one. Marks, moves, Delete, and
Not spam apply to every email of the row in the shown mailboxes, and Delete decides for each email
whether it is only in that folder.

The list shows each email's `preview`. An open message loads its body with a second `Email/get`
(`JmapMessageBodies`): the `textBody` and `htmlBody` parts with `fetchAllBodyValues` and values up
to 1 MB per part. The text shows the preview at once and then the full text, which replies and
forwards quote. The text is the `textBody` parts' values (RFC 8621, section 4.1.4), with each
part's `type`. When a message has no plain alternative, that part is `text/html`, read as plain text. It drops comments and the content of `script`, `style`, `title`, and `template`, breaks
lines at blocks and `<br>`, removes other tags, and decodes character references, in one pass that
tolerates broken markup. A part longer than the limit comes back cut off with `isTruncated`. When
another part's text follows the cut, the body says "[Part of the message is missing here.]" where
the part stops. When the cut is at the end, the message has `bodyTruncated`: the open message says
only its start was loaded, and a reply or forward ends the quoted text with "[The rest of the
original message is not included.]".

When an `htmlBody` part is `text/html`, the reader shows the HTML body instead of the text; otherwise
it keeps the text body. Plain parts between HTML parts keep their line breaks, and parts without a
value, such as inline `cid:` images, are left out. A failed load also keeps the text body.

Drafts (`JmapComposeMail`, RFC 8621 sections 2, 4.6, and 6) live in the mail account:

- Saving finds the folder with role `drafts` in the kept folders (`Mailboxes`), then sends an
  `Email/set`. Saving and sending share the copies whose save lost its answer and the uploaded
  signature images.
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

### Mail lists and the device cache

`JmapMailSource` keeps each login's lists in a `MailCache` (`core/cache`) and builds every list from
it. The cache holds, per login, the mailboxes, the row emails with every list property, the other
thread emails with only their people, date, keywords, and mailboxes, the thread objects, the
`Email` and `Thread` states, and the listings. A listing is one mailbox's query (the mailbox with
the page's filter and sort): the row email ids loaded so far, in order, and whether the server has
more. Each login keeps its 40 most recently loaded listings.

A refresh (`messages`) takes these requests per login:

1. `Mailbox/get` with every folder property, empty `Email/get` and `Thread/get` calls for the
   current states, and `Email/changes` and `Thread/changes` since the saved states (RFC 8620,
   section 5.2). Further pages of changes take one request each, up to four. When there is no saved
   state, the server refuses a `/changes` call (such as with `cannotCalculateChanges`), or the
   changes run past four pages, the login's cached emails or threads are dropped and the state from
   the empty `/get` is saved.
2. One `Email/query` per mailbox of the page, with `collapseThreads` and `calculateTotal`, for as
   many rows as the listing had loaded and at least 50. The listing has more when `position` plus
   the returned ids is below `total`, or, without a total, when the page came back full.
3. Only when something is missing or changed: `Email/get` of the rows the cache lacks plus every
   cached email that changed, each with `Thread/get` of their threads and an `Email/get` of those
   threads' emails by back-reference (RFC 8621, sections 3 and 4.4); `Thread/get` of cached rows'
   threads that are missing or changed, with their emails; and `Email/get` of changed or missing
   thread emails. When the server refuses the back-referenced emails, such as with
   `requestTooLarge` because the threads hold more than `maxObjectsInGet`, the threads are kept and
   their emails follow in one more request, split at `maxObjectsInGet`. A refused `Thread/get`
   leaves each row standing for its own email.

An unchanged refresh therefore takes two requests and reads no email. Calls share requests up to the
session's `maxCallsInRequest` (16 when the server does not say), and `/get` ids are split at
`maxObjectsInGet` (500 when it does not say). Everything from one refresh, including the new
states, is saved in one cache write. Destroyed and changed objects leave the cache in that write,
and the changed ones that were read again replace them.

`more` asks each listing that has more for the 50 rows after its last row whose cached email is
still in the mailbox, with `anchor` and `anchorOffset: 1` (RFC 8620, section 5.5), so rows moved
or deleted since the listing loaded skip nothing. It adds the ids it does not have yet, reads the
objects the cache lacks, and saves them in one write. When none of the listing's last 50 rows is
left, or the server refuses the anchor (`anchorNotFound`, such as after the row left on another
device), that listing loads again from position 0 with 50 more rows than it had. A listing the
cache does not hold, such as one dropped for newer ones, loads its first page; `more` never
refreshes, except for a login whose mailboxes are not cached yet. `cached` builds the same list
from the cache alone, without a request, so the inbox shows the device's copy at once.

Rows are mapped the same way from the cache in all three: a row whose email has left the mailbox,
such as after a move, is left out. When several listings are loaded, the list hides rows past the
earliest point where a listing with more stops, in the chosen sort order, so a later page never
adds rows above ones already shown. `hasMore` is true while any listing has more. Logins load in
parallel and fail alone, with a reason; a login that fails still shows its cached rows. Marks and
moves go through `JmapEmailWriter`, the one writer of emails, which applies each `Email/set` patch to
the cached emails at once and keeps it in the change queue until the server answers, so lists match
before the next refresh reads the change back. Deleting for good asks the server first and then
takes the destroyed emails out of the cache.

## Checks

Mail's tests use Ktor's mock engine for the inbox, draft, folder, rule, and account flows, including
storage-error recovery and same-named accounts. List tests answer through `FakeMailServer`, an
in-memory mail account that resolves result references and reports changes. The library's server integration test lives in
otus-meta.
