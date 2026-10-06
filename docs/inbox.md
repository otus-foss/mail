# Inbox and local search

Inbox is Mail's default start page. The production app supplies an empty message list and contains
no sample messages. Account setup, JMAP discovery/authentication, synchronization, and persistence
remain separate work. No provider requests are made.

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
- Results sort newest first using source-provided ISO dates and 24-hour times. The eventual data
  adapter must supply these normalized values and explicit mailbox/account identifiers. This is
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
icons, and avatar. App wiring supplies messages to `InboxScreen`. A repository abstraction can be
introduced with a real data source rather than adding an unused implementation now.

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
message rows, Settings rows, fields, and buttons, use it directly. Free-standing text, such as the
message count, section captions, and notes (`PageNote`), lines up with the text inside those rows.

## Connected account identity

Account and mailbox filters use the stable saved login ID. Display names and account details are
separate, so two accounts called the same name remain independently selectable. The picker shows
the username and server below each account name. Renaming an account does not change filter identity.

Sender avatars now resolve contact photos after messages appear. The people picker uses the same
resolved sender values. Generated initials remain visible while loading or if image decoding fails.
See [avatars](avatars.md) for the capability checks, account isolation, and external-image options.

Selected-person pills reuse those avatars. One sender shows an avatar and first name; multiple
senders show up to four overlapping avatars and a count for additional senders. The accessibility
label retains the sender's full name or the selected count. Selected addresses that are no longer
in the supplied messages still have an initials fallback.
