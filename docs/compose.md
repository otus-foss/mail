# Compose

The compose page writes a new message, a reply, a reply to all, or a forward. It saves drafts to
the account's Drafts folder and sends them through JMAP `EmailSubmission/set`.

## Implemented

### Starting a draft

`ComposeRequest` names what the draft starts from, and `startDraft` builds it:

- **New message** starts blank, in the account the request names or else the first account.
- **Reply** goes to the original's sender. The subject gets "Re: " unless it already starts with
  "Re:" in any case. `inReplyTo` holds the original's Message-ID, and `references` holds its
  References followed by its Message-ID, so the reply stays in the thread. The body starts
  empty. The quote, kept apart from it in `Draft.quote`, is "On <date>, <name> wrote:" and the
  original with each line quoted by "> ". When only the start of the original was loaded, the
  quote ends with "> [The rest of the original message is not included.]".
- **Resume** opens a draft again as it was, such as after Undo on a discarded one, and counts as
  written, so it saves.
- **Reply all** also adds the original's To recipients to To and its Cc recipients to Cc. It
  leaves out the account's sign-in address and the sending addresses from `Identity/get`, and
  lists each address once. A reply to mail the account sent goes to that mail's recipients.
- **Forward** gets "Fwd: " unless the subject already starts with "Fwd:" or "Fw:". The quote
  holds a "Forwarded message" block with From, Date, Subject, To, and Cc when present, then the
  original text, which ends with the same note when only its start was loaded.

### Sending from

The From pill offers the identities of every account (RFC 8621, `Identity/get`). A reply or
forward is sent from the address the original went to, preferring the original's account, and
a follow-up from the one that sent it. A new message from one account's inbox comes from that
account. A new message from all accounts comes from the address used last, which Mail keeps in
its behavior settings as `recentFrom`. Otherwise the account's first identity is used. Choosing
an identity of another account moves the draft: the next save stores it in that account's
Drafts, and the copy in the old account is deleted.

Delivered-To is not read yet, so a reply to mail that reached the account through an alias that
is not in To or Cc falls back to the account's first identity.

### The page

`ComposeScreen` follows the "Write" boards. It opens over the page it came from, and Back or the
chevron returns there. The floating panel shows the title, **New message**, **Reply**,
**Reply all**, **Follow up**, or **Forward**. Below it are three pills: the Send-from pill, **+ Cc** until the Cc line shows, and ⋯.

- The Send-from pill opens a dropdown with **Recent**, the last two addresses used, and then one
  section per account. It has a search field once there are eight or more addresses. The current
  address has a `bgRegular` fill without a checkmark.
- **To**, then **Cc** and **Bcc** while shown, hold recipient chips. A comma, a semicolon, Next,
  or leaving the line turns typed addresses into chips; text without an `@` stays to fix.
  Tapping a chip puts it back into the text to edit, and Backspace in an empty line removes the
  last chip. Typing two or more characters suggests people from JMAP contacts and from the From
  and To of the account's newest matching mail (`JmapAddressBook`).
- **Subject** shows a "Subject" placeholder.
- The body is formatted text (`RichTextBody`), with "Write your message" while it is empty.
- A forward ends with the original behind a "Forwarded message" chip. Tapping it shows the
  quoted text.
- A reply, follow-up, or reply all shows its thread above the bar while the keyboard is down.
  `JmapThreadHistory` loads it in one request: the original's `threadId` (`Email/get`), the
  thread's mail (`Thread/get`), and each message's sender, date, and preview (`Email/get`), all
  by result reference. Drafts and anything newer than the original are left out, newest first.
  Up to three messages show as rows with avatar, name, and date, such as "11 Oct". A longer
  thread folds into one row: stacked avatars, "5 earlier messages", and "Latest: Grace Hopper,
  11 Oct". Until the thread loads, or if it fails, the original alone shows.
- Tapping the thread opens **Earlier in this thread**: each message with its sender, date, and
  preview, then **Quote in your reply** with **Whole thread**, **Latest only**, and **None**.
  Whole thread, the default, quotes the original as it arrived. Latest only quotes its text up
  to the first quoted line or "On … wrote:" or "Original message" marker. None sends no quote.
- A To line with more than three people shows the first two and "+N more" until tapped; the
  field for the next address returns once it opens.

The ⋯ menu holds **Add Bcc**, which turns into **Remove Bcc** and then clears Bcc, and
**Discard draft**. A new message or forward starts with the cursor in To; a reply starts with
nothing focused.

### Bottom bar

With the keyboard down, the bar uses the **Compose** layout: Attach and Discard, with Send as the
main button. In Fixed mode it shows the shared layout, and every action except Send and Discard
is disabled there. Attach picks files; it is disabled where the platform has no picker, which
is iOS for now. Send is enabled once the draft has a From address, at least one recipient, and
every attached file uploaded to its account. The main button then reads "Send to" above
the first recipient's first name with their avatar, or a count for several. Accessibility
services read it as "Send, to Grace Hopper".

With the keyboard up, the formatting row replaces the bar: Attach, then Bold, Italic, Bulleted
list, Numbered list, Link, and Clear formatting, with a compact Send pinned at the end. Link asks
for a web address. `BarMorph` turns one into the other as the keyboard starts to move: the parts
take the other bar's height, then its width, with the content blurred while the width changes.

Send without a subject asks first: **Send** or **Add subject**.

**Discard** stops saving and closes the page with a "Draft discarded" toast. Undo opens the
draft again as it was. The saved copy is deleted once the toast leaves without Undo.

### Attachments

Attach opens the system document picker for any number of files of any type. Each file uploads
at once to the draft's account (RFC 8620, section 6.1) and shows as a chip under the body with
its name and "Uploading…", then its size. A file larger than the session's `maxSizeUpload`
says "Too large for this server"; any other failure says "Couldn't upload", and tapping the chip
offers Retry. ✕ removes a file. Choosing an identity of another account uploads every file again there,
because a blob belongs to one account.

A save or send puts each file uploaded to the draft's account after the body in
`multipart/mixed`, by its `blobId`, with its name and `disposition: attachment`. A file still
uploading waits for a later save. The body no longer fills the page while files follow it, so
the chips stay in view.

A picked image starts inline: it goes into the body at the cursor as `<img src="cid:…">` and
shows there as a preview, as wide as the text, without a chip. "Uploading…" or the upload
failure shows over its corner. Tapping it gives it an outline and a corner handle, and the
bottom bar morphs into the image bar: Small, Medium, Large, and Full, ⋯, and ✓ to finish.
Dragging the handle shows dashed guides at each size and snaps to them with a haptic tick. ⋯
grows the bar's group upward into its options: **Send as file**, which takes the image out of the body and gives it a chip,
**Description**, its `alt` text, **Replace**, which picks another image for the same spot and
size, **Remove image**, and Retry after a failed upload. A file image's chip offers **Show in
message**, which puts it back at the cursor. The width and description go into the `<img>`
tag. Deleting the image with Backspace right after it removes the file too.

The signature editor uses the same image bar, with Small, Medium, and Large and Remove image. A save or send puts each inline image beside the HTML in
`multipart/related` with its `cid` and `disposition: inline`, so the mail shows it where it
sits; the plain-text part leaves it out.

### Signatures

Each identity has one signature, stored on the server identity as `htmlSignature` and
`textSignature` (RFC 8621, section 6). A new message always gets the signature of its From
address; replies, reply-all, follow-ups, and forwards get it only while **Add to replies** is
on. Choosing another From address switches to that address's signature. The ⋯ menu's
**Signature** switch removes it from this draft only. The page does not show the signature; the
sent message has it after the body: in the text part after a `-- ` line, in the HTML part as its
own block.

Settings › **Signatures** lists every address by account with the start of its signature, or
"No signature". **Edit signature** opens the editor: the address pill switches to another
address in place, **Add image** (Android only), **Copy**, and **Remove signature**, which asks
first. Edits save a second after the last one and when the page closes, with `Identity/set`. A
failed save puts the old signature back and says so.

An added image is sampled during decoding, then shrunk to at most 600 px on either side. Its
encoded size must fit 50 KB: PNG when small enough, JPEG at falling quality otherwise. Mail
rejects an image that cannot fit the limit. It is stored in the signature as a `data:` URI. A selected
image has a `text2` outline, a ✕, and one corner handle that keeps its aspect ratio between 48
and 300 px and snaps to Small (96), Medium (132), or Large (200) within 6 px. The size pill under
it names the preset or the pixel size. When a draft is saved or sent, each image is uploaded and
the HTML points at it by `cid:`, inside `multipart/related`, so the message carries it.

**Copy signature** copies from one other address, which needs a signature, to this one, or from
this one to several others, and shows "Signature copied".

### Saving

`ComposeState` saves the draft two seconds after the last change, and again when the page
closes. A draft nobody changed is never saved. Each save replaces the previous copy in Drafts and
remembers the new Email id. Saves run one at a time, so each save knows the copy it replaces. A change,
closing the page, or a discard during a save lets that save finish and keep its new id, so the
next save replaces that copy and a discard deletes it. A failed save keeps the previous copy and
its id, so the next save replaces it, and shows a notice under the header until a later save
succeeds. The notice says why: no Drafts folder, no permission, or an unreachable server.

### Sending

Send closes the page at once and hands the draft to `OutgoingMail`. A "Sending…" toast with Undo
holds it on the device for the **Undo send** delay in Appearance & behavior: 5, 10, 15, or 30
seconds, 15 by default, with the toast's ring draining over that time. Undo opens the draft again
as it was. With Undo send off, the message goes at once. Then the send
waits for any save in progress, so it knows the draft's saved copy, and calls `JmapDraftSender`.
The toast turns into "Sent", or into "Couldn't send" with Retry, which stays until it is handled.
The hold is on the device, not the server's `HOLDFOR`, because `onSuccessUpdateEmail` files the
copy in Sent as soon as the submission is created, and a cancelled one would leave it there. The sender makes three requests (RFC 8621, section 7.5):

1. `Identity/get` and two `Mailbox/query` calls find the identity for the From address and the
   Drafts and Sent folders. An identity matches by exact address, or by a wildcard such as
   `*@example.org` for any address in its domain.
2. One request stores the draft as written with `Email/set` and submits that copy with
   `EmailSubmission/set`. It sends no envelope, so the server takes the recipients from To, Cc,
   and Bcc, and removes the Bcc header. `onSuccessUpdateEmail` moves the copy from Drafts to Sent
   and clears `$draft`. Without a Sent folder the copy stays where it was stored, without `$draft`.
3. Once the server accepts the message, one `Email/set` destroys the draft's earlier copy and sets
   `$answered` on the original of a reply or `$forwarded` on the original of a forward. A failure
   here is ignored, because the message is already sent.

The stored Email has a `text/plain` part: the body, a blank line, and the quote. Once the body
has text it also has a `text/html` part, so the server builds `multipart/alternative`: the
formatted body, then the quote with its quoted lines in a `blockquote`.

Nothing is saved after Send, and the From address goes first in `recentFrom`. A refused
submission destroys the copy the request stored and keeps the earlier one in Drafts.

### Schedule send

The ⋯ menu offers **Schedule send** only where the account's submission capability has a
`maxDelayedSend` above 0 (RFC 8621, section 7). The **Send later** sheet offers This evening at
18:00 before 17:00, Tomorrow morning at 8:00, Monday morning at 8:00 from Friday to Sunday, and
Pick date and time, within the server's limit. Choosing one only picks the time: the sheet closes,
the page stays open, and a notice above the bar says "Scheduled for Mon 19 Oct, 8:00" with Change,
which opens the sheet again; once a time is set the sheet also offers Send now. The main button
then reads "Sends to" with the schedule-send symbol. Send sends with an explicit envelope whose `mailFrom`
carries `HOLDUNTIL` (RFC 4865), closes the page, and shows "Scheduled for Mon 19 Oct, 8:00" with Undo.
The scheduled copy stays in Drafts without `$draft` until the server sends it. Undo sets the
submission's `undoStatus` to `canceled`, marks the copy `$draft` again, and opens it.

## Limits

- Attaching files works on Android only; iOS has no file picker yet.
- A file that cannot upload, such as one too large, keeps Send disabled until it is removed.
- The total size is not checked against the server's `maxSizeAttachmentsPerEmail`; the server
  refuses such a message when it is sent.
- A forward does not carry the original's attachments, and received attachments cannot be
  opened yet ([Mail #25](https://github.com/reazndev/otus-mail/issues/25)).
- Crop and rotate, shown on the image options board, is not built.
- Some keyboards send no Backspace key at the start of a line, so deleting an image that way may
  not work; its chip's ✕ always does.
- Photos larger than 4096 px on a side show no preview, though they still send.
- Picked files are read again for each upload while the app runs; a draft reopened after the app
  closed cannot upload its files to another account.
- There is no outbox that survives the app closing: a message in its Undo window or waiting to
  retry is lost with the process, though its saved copy stays in Drafts
  ([Mail #26](https://github.com/reazndev/otus-mail/issues/26)).
- Scheduled messages show only in Drafts. A Scheduled view with Send now and Cancel is not built;
  it waits for a decision.
- Rotating the device or the system ending the app returns to the page that opened the draft.
  The saved copy is kept in Drafts, but the page does not reopen it, and edits made in the last
  two seconds can be lost.
- Opening a saved draft from the Drafts folder is not built.
- iOS has no image picker for signatures yet, so Add image is hidden there.
- The signature list's account sections and the copy sheet's rows are flat, without the boards'
  grouped `bgRegular` background.
- Mark as important, shown on the "⋯ more options" board, is out of scope.
- The disabled Send keeps the bar's layout, its label then the icon, in `text3`; the board puts
  the icon first.
- A failed discard is not reported, because the page has already closed.
- When a save stores the new copy but deleting the old one fails, the old one stays in Drafts as
  a duplicate.

## Validation

`DraftStartTest` and `RecipientTextTest` cover starting drafts and the recipient text format.
`JmapDraftSenderTest` checks the send requests against a mock server: the stored copy and its
submission in one request, filing in Sent, wildcard identities, the marks on the original, and
cleanup after a refusal. `ComposeSendTest` covers when Send is enabled, waiting for a save in
progress, committing a typed address on Send, and failure notices. `ComposeStateTest` covers
delayed saves, saving on close, discard and its later delete, failure notices, keeping the stored
copy after a failed save, a change or discard during a save, the sending identity, resumed
drafts, and moving a draft to another account.
`DraftEmailTest` checks the text and HTML parts with the quote, and uploaded files in `multipart/mixed`.
`ComposeAttachmentsTest` covers uploading a picked file, retrying a failed upload, uploading again
after a move to another account, and removing a file. `ComposeAttachmentScreenTest` renders the
chips under the body. `ComposeRecipientsTest` covers
chips, editing a chip, and suggestions. `ComposeScreenTest` types a draft into chips, checks that
Send names its recipients, sends a draft, asks about a missing subject, saves on Back, chooses an
identity of another account from the dropdown, adds Bcc and discards from the ⋯ menu, and opens
a forward's quote. `ReplyThreadScreenTest` covers the thread rows, the folded thread, the folded
To line, and the quote choice in the sheet; `JmapThreadHistoryTest` the thread request and
`ReplyQuoteTest` the quote modes. `SignatureTest`, `SignaturesStateTest`, and `SignatureScreensTest` cover the
signature HTML and images, copying and failed saves, `SendLaterTest` the Send later times,
`OutgoingMailTest` the Undo window and turning it off, `UndoSendSettingTest` the delay setting, Retry, and undoing a scheduled message, the list, the editor with its size list,
and the copy sheet. Their screenshots of each state are written to `androidApp/build/reports/compose/`.
