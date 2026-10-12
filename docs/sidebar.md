# Sidebar

Implemented in common Compose code for Mail. `Sidebar` and `SidebarRail` live in the shared design
system's `components` (`com.otus.designsystem`, from otus-meta). `SidebarEntry`, `SidebarHeading`,
and the rail items are elements. None of them knows about Mail: the caller supplies the title,
subtitle, rail, sections, and a `SidebarState`. App wiring owns the current page, the
chosen account, and the `SidebarState`.
`SidebarSwipeArea` wraps the page and handles swipe-to-open; `ContextPanel` marks the bottom of
that zone, so pages need no extra wiring.

## Behavior

- Tapping the context panel's leading chevron opens it. In search mode that chevron still means
  "Back to inbox".
- On a top-level page, a swipe toward the trailing side that starts on the context panel or above
  it also opens the sidebar, following the finger. Swipes on controls that scroll sideways, such as
  the chip rail, stay with those controls. Subpages do not swipe it open, because their chevron
  means "Back". On Android gesture navigation, a swipe starting at the very screen edge is the
  system back gesture.
- It opens from the leading edge to 336 dp: a 64 dp account rail and a 272 dp panel. On a narrower
  screen the panel shrinks so at least 56 dp of the page stays visible. It is
  flush with the top, leading, and bottom screen edges, with square corners and a `strokeBgDark`
  stroke on the trailing edge only; its content is padded clear of the system bars and cutouts. The page stays visible beside it
  under a scrim. RTL mirrors the slide.
- The panel's header has the context panel's header height, 64 dp, so it lines up with the page
  title. It shows a 16 sp SemiBold title over a 14 sp `text2` subtitle, and the sections scroll
  below it. A 1 dp `strokeBgDark` line separates the rail from the panel. Nothing has a shadow.
- Each section starts with a `SidebarHeading`: 14 sp `text2` text and a chevron that turns 90
  degrees over 220 ms with the standard curve. Tapping the heading collapses the section. The
  heading row is 48 dp tall, the touch target, rather than the spec's 40 dp.
- A folder row (`SidebarEntry`) is 44 dp tall in a 48 dp target, with a 12 dp padding, a 20 dp
  icon, a 16 sp name, and a 14 sp `text2` count. Each nesting level indents it 16 dp. A row with
  subfolders ends in a 36 dp chevron button; Compose extends its touch area to 48 dp. The current page
  is highlighted with `accentDark`.
- The rail lists 40 dp circles in 48 dp targets, 4 dp apart, and scrolls when the accounts
  overflow. The Settings button stays pinned to its bottom in `text2`. The selected circle has an
  `accentDark` fill and a 2 dp `accentLight` outline 2 dp outside it; the others have a
  `bgRegular` fill and a 1 dp `bgLight` border. A badge is a 20 dp `accentDark` pill with 12 sp
  SemiBold text and a 2 dp `bgDark` ring. Add account is a dashed `bgLight` circle with a `text2`
  plus. Accounts have no colors or images of their own, and the account initials are 13.3 sp, the
  `Avatar` scale at 40 dp, rather than the spec's 14 sp.
- Choosing an entry, tapping the scrim, the system back gesture, or swiping it back toward the
  leading edge closes it. A released swipe settles on the nearer side unless it was flung faster
  than 400 dp/s. Its surface is `bgDark`.
- Motion reuses the suite curves: open with `MotionTokens.enter` over 300 ms, close with
  `MotionTokens.exit` over 220 ms; a sidebar released part-way animates only the remaining share
  of that time. One progress value drives slide and scrim, so swipes and reversals stay continuous. Values live in `SidebarTokens`.

## Mail destinations

`app/MailPage` lists every page; pages without a parent are sidebar destinations. `MailSidebar`
fills the sidebar for every account at once.

- The rail starts with **All**, then a short line, then each account by its initials, then Add
  account. Each badge shows the account's inbox count, unread or total as **Counts** chooses, and
  none while counts are off; All's badge adds them up, and a badge over 999 reads "999+". All
  is selected at launch. Choosing an account changes only the header and the counts: the header
  shows the account's name and address, and every count in the panel counts that account alone.
  The page and the lists stay the same. With All, the header reads "All accounts" over
  "6 accounts · 28 unread", or "28 total" or no count, as **Counts** chooses. Add account opens Email accounts, and the gear opens Settings.
- **Mailboxes** lists the built-in mailboxes in fixed order: Inbox, then
  [Read later](folders.md#read-later) while it is on, then Drafts, Sent, Archive, Spam, and Trash.
  A mailbox appears once any account has it; Inbox always does. Each one opens every account's
  folder of that kind: Inbox the unified Inbox, Read later `MailPage.ReadLater`, and the rest
  `MailPage.Combined`. They show as `SidebarPill` chips with a `bgRegular` fill, 6 dp apart, with 12 dp
  between rows. The current mailbox has an `accentDark` fill and an `accentLight` outline.
  Spam keeps the app's name, although the spec calls it Junk; see
  [combined folders](folders.md#combined-folders).
- **Folders** lists the user's own folders, [shared by every account](folders.md#shared-folders).
  Its heading ends with a New folder button, which opens Settings › Folders, because each account
  still creates its own folders. A collapsed folder hides its subfolders and shows their count
  added to its own. Collapsed sections and folders last until the app process ends.
- Settings › Appearance & behavior › Sidebar arranges the sidebar for every account. Dragging a
  section's handle moves it, and its switch hides it, except for the last section shown. **Mailboxes** chooses Chips or Rows, and
  **Counts** chooses Unread, Total, or Off. Each change is saved at once in `MailBehavior.sidebar`
  (`SidebarLayout`). The sidebar leaves out the spec's Edit sidebar footer row.

Only one item is selected across pills and rows. Each page, including each folder, keeps its saved
state, such as a search, while another page is shown. Settings subpages stay under Settings; see
[Settings](settings.md) and [Folders and rules](folders.md).

## Calendar

The component is app-independent, but it lives in the Mail repository. Calendar does not yet have
`Symbol`, the icon set, Figtree, or `PanelTokens`, and cross-repository library distribution is
undecided. Calendar adoption therefore needs either that decision or a deliberate copy.

## Checks

`SidebarNavigationTest` covers opening from the chevron, the 336 dp width, the rail's pinned
Settings, navigating to Settings and back with preserved inbox state, scrim
dismissal, back dismissal, swiping open on and above the context panel (but not on the message
list), and swiping closed. `SidebarEditTest` covers choosing an account in the rail and the Sidebar
settings page. `FoldersAndRulesTest` covers mailbox pills, collapsing a folder, and opening one.
`SidebarCounterTest`, `SharedFolderTest`, and `SidebarLayoutTest` cover the counts, the merged
folders, and the saved layout. The rail and the 336 dp width have not been checked on a device.
iOS runtime validation requires macOS/Xcode.
