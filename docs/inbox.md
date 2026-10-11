# Inbox and local search

Inbox is Mail's default start page. Without connected accounts it is empty; no sample messages
are shipped. Shared account setup verifies JMAP discovery and authentication. Mail loads up to
50 messages per connected account from the server on launch and refresh. Message persistence
and incremental synchronization remain separate work.

## Implemented

- The existing `ContextPanel` uses detached quick filters in Inbox and overlapping filter pills in search.
- Search focuses the input, updates results as text changes, supports clearing, and dismisses the
  keyboard on IME Search. The header back control and completed system back return to Inbox and
  reset search criteria.
- Portable domain matching requires every whitespace-separated query term to match somewhere in
  subject, sender name/address, or plain-text body, without case sensitivity. Punctuation is literal.
- Unread, attachment, multiple senders, inclusive date ranges, account-specific mailboxes, and
  multiple accounts combine with the query. Inbox is the default scope. Custom selection sheets
  have independent picker search and draft/Clear/Apply behavior. See [selection sheets](selection-sheets.md).
- Results sort newest first using ISO dates and 24-hour times normalized by the JMAP data
  adapter, which also supplies mailbox/account identifiers. This is
  local search over supplied messages, not an implementation of JMAP Email/query semantics.
- Empty inbox and no-result states are distinct. Clearing search and filters restores the default
  Inbox scope. Message rows can show a read-only plain-text preview; opening does not mark mail read.
- Search mode, query, and filters use saveable screen state. There is no saved message store.
- Pulling the message area down past a fixed 80 dp threshold reloads the inbox, in both Inbox and
  search modes. The design-system `PullToRefresh` component uses Compose Material's pull gesture
  with the `RefreshIndicator` element: a pill-styled `bgDark` circle with a `strokeBgDark` outline
  that slides down with the pull, with a `text1` arc that grows and turns with the distance and
  spins while the feed loads. A confirming haptic plays when a pull triggers the reload. Short
  pulls spring back without loading. The element draws its own arc because Material's indicator
  is an elevated surface, which the dark theme lightens and shades.

## Boundaries

`feature/inbox/domain` owns message values and matching. `presentation` owns screen input state.
Feature components receive values/callbacks; they reuse the design-system panel, pills, input,
icons, and avatar from Meta. App wiring connects `InboxFeedState` to the domain's `MailSource`
contract and the `JmapMailSource` data implementation, then supplies messages to `InboxScreen`.

## Design reference and adaptations

Inspected Figma `meta` nodes: `23:488` (inbox frame), `10:58` (message row), and the previously
inspected `19:386` / `19:322` panel variants. The frame does not contain a populated message list;
the separate row reference defines that layout. The empty-state copy/layout is new.

Material Symbols Rounded remains the approved icon family. The Figma row's black avatar and
`#2A5E96` label border are outside the approved palette: generated avatars use named dark background
tokens with `text1` initials, and metadata uses
named text tokens rather than introducing that border color. Text is allowed sufficient height at
large font scales instead of reproducing Figma's clipped 48 px row. Root Inbox now reserves a
left chevron for the planned sidebar navigation. The prototype's compose/bulk toolbar
and deletion toast are deferred with their corresponding product behaviors.

## Validation

`./gradlew checkAll` includes common domain tests via `:shared:testAndroidHostTest`, Android local
UI tests, lint, and debug assembly. UI tests supply fixtures only in test code, exercise actual
matching/filter callbacks, and produce screenshots under `androidApp/build/reports/inbox/`.
The existing panel playground remains available as a test fixture screen, not the app entry point.
Physical Android device and iOS runtime testing remain separate platform checks.

## Inbox header actions

The default header shows a tappable "Tap to search" prompt in a minimum 48 dp target. Tapping it
opens the search field, requests focus and the software keyboard, and reveals the search filter
pills. The trailing button opens the filter sheet in both inbox and search modes.
The default leading icon is a mirrored left chevron that opens the sidebar. In search mode, the
chevron still closes search.

## Page width

Content spans the context panel's width: the page has an 8 dp inset and full-width elements, such as
Settings rows, fields, and buttons, use it directly. Free-standing text, such as the message count,
section captions, and notes (`PageNote`), lines up with the text inside those rows. Inbox and folder
message rows are the exception: they reach the screen edges so a swipe's color does too, and pad
their content by the same 8 dp so it stays aligned with the context panel.

## Connected account identity

Account and mailbox filters use the stable saved login ID. Display names and sign-in addresses are
separate, so two accounts called the same name remain independently selectable. Mailbox rows show
the account avatar and address. Account rows show the avatar and include the address only when it
differs from the account name. Renaming an account does not change filter identity. Account avatars
use the same resolver as senders and stay visible during an inbox refresh.

Sender avatars now resolve contact photos after messages appear. The people picker uses the same
resolved sender values. Generated initials remain visible while loading or if image decoding fails.
See [avatars](avatars.md) for the capability checks, account isolation, and external-image options.

Selected-person pills reuse those avatars. One sender shows an avatar and first name; multiple
senders show up to four overlapping avatars and a count for additional senders. The accessibility
label retains the sender's full name or the selected count. Selected addresses that are no longer
in the supplied messages still have an initials fallback.

## Folder pages and message actions

A folder opened from the sidebar uses the same screen. `InboxContext` gives it its default scope
(every message of that folder, not the Inbox scope), its search prompt ("Search <folder>"), and its
empty-state title. Closing search and clearing filters return to that default; the mailbox pill
reads "All" for it. `MailSource.messages(MailScope)` loads either every account's inbox or one
folder of one account. Messages keep their server id and mailbox id so they can be moved.

An open message can offer **Mark read**/**Mark unread**, **Flag**/**Unflag**, **Read later**,
**Archive**, **Trash**, **Move**, and **Rule for sender** through `MessageActions`, which app wiring builds from
the loaded folders and rules. The actions wrap above the message body, so they stay in view while
it scrolls.

- Marks set or clear one keyword with an `Email/set` patch (RFC 8621, section 4.1.1): `$seen` for
  read and `$flagged` for the flag. Other keywords are untouched; turning a mark off removes the
  keyword. The open message shows the change at once. If the server refuses, the mark goes back
  and the inbox shows a notice. Changing the read mark refreshes the folder unread counts.
- Archive and Trash move the message to the account's folder with the `archive` or `trash` role,
  found by role because servers name these folders differently. Each is offered only when that
  folder exists and the message is not already in it. Archive also takes the message out of the
  inbox, and Trash out of every folder. Trash moves the message; nothing is permanently deleted.
- **Read later**, when it is on, adds the account's Read later folder and keeps the message where
  it is; a saved message offers **Remove from Read later**. See
  [Folders and rules](folders.md#read-later).
- Actions follow the rights of the folder the message is shown in (RFC 8621, section 2):
  `maySetSeen` for the read mark, `maySetKeywords` for the flag, and `mayRemoveItems` for
  Archive, Trash, and Move. The target must allow `mayAddItems`. The server still checks every
  other mailbox the message belongs to.
- Opening an unread message marks it read with the same `$seen` patch, unless **Mark as read when
  opened** is off in Appearance & behavior. Undo, bulk selection, and permanent deletion are not
  implemented yet; see issue #27.

A moved message leaves the list at once. If the server refuses, it comes back and the inbox shows
a notice. See [Folders and rules](folders.md).

## Message rows

Each row is Meta's `ContentRow`. The first line is the subject, bold while unread, with when the
message arrived: the time today, "Yesterday", the weekday within the last week, and the saved date
format before that (`receivedLabel`). The second line is the sender's name, or the address when
the message has none.

The last line shows only what the page doesn't already say:

- The message's other folders, as `LabelPill`s, from its `mailboxIds`. Rows name the inbox and the
  user's own folders, Read later highlighted with its bookmark; Sent, Trash, and other system
  folders are left out, and so is the folder the page shows (`labelFolders`). App wiring supplies
  them through `InboxScreen`'s `messageLabels`, because the inbox feature does not import folders.
- The account name, when the list mixes accounts.
- The number of attachments.

A row with none of these shows the start of the message instead. The line keeps a label's height,
so rows with and without labels line up.

## Swipe actions

Each inbox and folder row is wrapped in Meta's `SwipeActions`. Appearance & behavior chooses what
a swipe right and a swipe left do: **Read / unread**, **Flag / unflag**, **Read later** (while it
is on), **Archive**, **Trash**, or **Nothing**. The defaults are mark read or unread on a swipe right and archive on a
swipe left. Right-to-left layouts mirror the directions.

- A swipe runs the same action as the open message's button, with the same folder rights and role
  checks. A row whose message cannot take the chosen action does not move in that direction.
- Dragging uncovers a full-height accent area that grows out of the screen edge. Its concave
  corners at the edge cut 12 dp into the rows above and below, and its free end is rounded. The action's icon grows in beside its label, and the row
  stops once the action is fully revealed. Releasing past
  96 dp, marked by a haptic, flashes and runs the action. Archive and Trash leave the row to the
  list, which removes it at once. Read later saves the message in place, so the row returns to
  rest with its new label; it leaves only where the message leaves the page. Marks return the row to rest showing the new state.
- TalkBack and VoiceOver offer each available swipe as a custom action on the row.
- `MailBehavior` holds the two swipes, the open-to-read choice, and whether Read later is on. Turning
  Read later off returns a swipe set to it to that swipe's default. `MailBehaviorStore` saves them
  in app-private preferences: `SharedPreferences` on Android and `NSUserDefaults` on iOS. Unknown
  saved values read as the defaults.
