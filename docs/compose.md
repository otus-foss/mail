# Compose

The compose page writes a new message, a reply, a reply to all, or a forward. It saves drafts to
the account's Drafts folder. Mail cannot send yet, so Send stays disabled.

## Implemented

### Starting a draft

`ComposeRequest` names what the draft starts from, and `startDraft` builds it:

- **New message** starts blank, in the account the request names or else the first account.
- **Reply** goes to the original's sender. The subject gets "Re: " unless it already starts with
  "Re:" in any case. `inReplyTo` holds the original's Message-ID, and `references` holds its
  References followed by its Message-ID, so the reply stays in the thread. The body starts with
  two blank lines, then "On <date>, <name> wrote:" and the original with each line quoted by
  "> ".
- **Reply all** also adds the original's To recipients to To and its Cc recipients to Cc. It
  leaves out the account's sign-in address and the sending addresses from `Identity/get`, and
  lists each address once. A reply to mail the account sent goes to that mail's recipients.
- **Forward** gets "Fwd: " unless the subject already starts with "Fwd:" or "Fw:". The body
  holds a "Forwarded message" block with From, Date, Subject, To, and Cc when present, then the
  original text.

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
mode it shows the shared layout, and every action except Discard is disabled there. Send and
Attach stay disabled until Mail can send and attach. Once the draft has recipients, the main
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

## Limits

- Sending and attachments are not built.
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
`ComposeStateTest` covers delayed saves, saving on close, discard, failure notices, keeping the
stored copy after a failed save, a change or discard during a save, and the sending identity.
`ComposeScreenTest` types a draft, checks the disabled Send names its recipients, saves on Back,
chooses an identity, and confirms a discard. Its screenshot is written to
`androidApp/build/reports/compose/`.
