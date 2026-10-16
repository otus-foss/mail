# Compose

The compose page writes a new message, a reply, a reply to all, or a forward. It saves drafts to
the account's Drafts folder and sends them through JMAP `EmailSubmission/set`.

## Implemented

### Starting a draft

`ComposeRequest` names what the draft starts from, and `startDraft` builds it:

- **New message** starts blank, in the account the request names or else the first account.
- **Reply** goes to the original's sender. The subject gets "Re: " unless it already starts with
  "Re:" in any case. `inReplyTo` holds the original's Message-ID, and `references` holds its
  References followed by its Message-ID, so the reply stays in the thread. The body starts with
  two blank lines, then "On <date>, <name> wrote:" and the original with each line quoted by
  "> ". When only the start of the original was loaded, the quote ends with
  "> [The rest of the original message is not included.]".
- **Reply all** also adds the original's To recipients to To and its Cc recipients to Cc. It
  leaves out the account's sign-in address and the sending addresses from `Identity/get`, and
  lists each address once. A reply to mail the account sent goes to that mail's recipients.
- **Forward** gets "Fwd: " unless the subject already starts with "Fwd:" or "Fw:". The body
  holds a "Forwarded message" block with From, Date, Subject, To, and Cc when present, then the
  original text, which ends with the same note when only its start was loaded.

A reply or forward is sent from the identity the original went to, or else the first identity.

### The page

`ComposeScreen` opens over the page it came from, and Back or the chevron returns there. The
header shows the page's title: **New message**, **Reply**, **Reply all**, or **Forward**. Below
it are the header lines, built from Meta's `ComposeField` and `ComposeValueField`:

- **From** shows the sending identity. With several identities, tapping it opens a
  **Send from** sheet.
- **To** takes addresses or `Name <address>`, separated by commas or semicolons. A button at its
  end shows **Cc** and **Bcc**, which stay hidden until needed unless the draft already has
  copies.
- **Subject**, then the body (`ComposeBody`), which fills the rest of the page. The lines and the
  body scroll together.

A new message or forward starts with the cursor in To; a reply starts with nothing focused.

### Action bar

The bar uses the **Compose** layout: Attach and Discard, with Send as the main button. In Fixed
mode it shows the shared layout, and every action except Send and Discard is disabled there.
Attach stays disabled until Mail can attach files. Send is enabled once the draft has a From
address and at least one recipient in To, Cc, or Bcc. Once the draft has recipients, the main
button names them: one person's avatar and "to Ada", or the first avatar, a count, and
"3 people". Accessibility services read it as "Send, to Ada".

**Discard** asks first when the user has written anything, then deletes the saved copy and
closes the page. A draft the user has not changed closes at once.

### Saving

`ComposeState` saves the draft two seconds after the last change, and again when the page
closes. A draft nobody changed is never saved. Each save replaces the previous copy in Drafts and
remembers the new Email id. Saves run one at a time, so each save knows the copy it replaces. A change,
closing the page, or a discard during a save lets that save finish and keep its new id, so the
next save replaces that copy and a discard deletes it. A failed save keeps the previous copy and
its id, so the next save replaces it, and shows a notice under the header until a later save
succeeds. The notice says why: no Drafts folder, no permission, or an unreachable server.

### Sending

Send waits for any save in progress, so it knows the draft's saved copy, and then calls
`JmapDraftSender`. The sender makes three requests (RFC 8621, section 7.5):

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

On success the page closes, and nothing is saved after that. A refused submission destroys the
copy the request stored and keeps the earlier one, and the page shows why under the header:
no identity for the From address, an invalid recipient, too many recipients, a message too large,
no permission, or a lost connection. After a lost connection the outcome is unknown, so the
notice asks the user to check Sent before trying again.

## Limits

- Attachments are not built.
- There is no outbox, retry, or undo-send window yet ([Mail #26](https://github.com/reazndev/otus-mail/issues/26)).
  Send needs a connection, and the page stays open while it runs.
- Nothing confirms a sent message once the page closes.
- Rotating the device or the system ending the app returns to the page that opened the draft.
  The saved copy is kept in Drafts, but the page does not reopen it, and edits made in the last
  two seconds can be lost.
- Opening a saved draft from the Drafts folder is not built.
- Recipient lines are plain text. There is no address completion from contacts yet.
- A failed discard is not reported, because the page has already closed.
- When a save stores the new copy but deleting the old one fails, the old one stays in Drafts as
  a duplicate.

## Validation

`DraftStartTest` and `RecipientTextTest` cover starting drafts and the recipient text format.
`JmapDraftSenderTest` checks the send requests against a mock server: the stored copy and its
submission in one request, filing in Sent, wildcard identities, the marks on the original, and
cleanup after a refusal. `ComposeSendTest` covers when Send is enabled, waiting for a save in
progress, and failure notices. `ComposeStateTest` covers delayed saves, saving on close, discard, failure notices, keeping the
stored copy after a failed save, a change or discard during a save, and the sending identity.
`ComposeScreenTest` types a draft, checks that Send names its recipients, sends a draft, saves on Back,
chooses an identity, and confirms a discard. Its screenshot is written to
`androidApp/build/reports/compose/`.
