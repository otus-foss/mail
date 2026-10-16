# Sidebar

Implemented in common Compose code for Mail. `Sidebar`, `SidebarHeader`, `SidebarMailboxGrid`, and
`ScopeSheet` live in the shared design system's `components` (`com.otus.designsystem`, from
otus-meta). `SidebarInboxEntry`, `SidebarMailboxTile`, `SidebarEntry`, `SidebarFolderEntry`,
`SectionCaption`, and `SidebarDivider` are elements. None of them knows about Mail: the caller
supplies the header, the rows, and a `SidebarState`. App wiring owns the current page, the chosen
"Show mail from" scope, and the `SidebarState`.
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
- It opens from the leading edge to 336 dp. On a narrower screen it shrinks so at least 56 dp of
  the page stays visible. It is flush with the top, leading, and bottom screen edges, with square
  corners and a `strokeBgDark` stroke on the trailing edge only; its content is padded clear of
  the system bars and cutouts. The page stays visible beside it under a scrim. RTL mirrors the
  slide. There is no account rail; the scope in the header replaces it.
- The header has the context panel's header height, 64 dp, so it lines up with the page title. It
  shows an `AvatarStack` of 32 dp avatars, a 16 sp SemiBold title over an optional 14 sp `text2`
  subtitle, then an unfold button and a Settings gear. Tapping the title or the unfold button
  opens "Show mail from". The gear opens Settings. Nothing has a shadow.
- Opening it, by tap or swipe, clears focus from any field, such as search, and hides the keyboard.
- Choosing an entry, tapping the scrim, the system back gesture, or swiping it back toward the
  leading edge closes it. A released swipe settles on the nearer side unless it was flung faster
  than 400 dp/s. Its surface is `bgDark`.
- It opens at `MotionSpeed.Relaxed` (300 ms) with `MotionCurve.Enter` and closes at
  `MotionSpeed.Normal` (240 ms) with `MotionCurve.Exit`. A sidebar released part-way animates only
  the remaining share of that time. One progress value drives slide and scrim, so swipes and
  reversals stay continuous. These times are for the Regular speed; the **Animations** setting in
  Appearance & behavior scales them like every other animation, and Instant opens and closes the
  sidebar at once. Sizes live in `SidebarTokens`; see [motion](motion.md).

## Mail destinations

`app/MailPage` lists every page; pages without a parent are sidebar destinations. `MailSidebar`
fills the sidebar for the chosen scope.

- The header names the scope. All mail shows each account's initials, or each domain's when there
  is one account, and has no subtitle; without accounts the subtitle reads "No accounts yet". An
  account shows its initials and name over "3 addresses". An address shows its initials and the
  address over its account's name.
- In a nightly build, a "Get nightly <number>" row leads the sections when a newer nightly is
  published. See [nightly builds](nightly.md).
- **Mailboxes** starts with Inbox as a 52 dp row with a SemiBold label and the count at the end.
  The other built-in mailboxes follow in fixed order: [Read later](folders.md#read-later) while it
  is on, then Drafts, Sent, Archive, Spam, and Trash. A mailbox appears once any account has it;
  Inbox always does. Each one opens every account's folder of that kind: Inbox the unified Inbox,
  Read later `MailPage.ReadLater`, and the rest `MailPage.Combined`. In the Grid style they are
  `SidebarMailboxTile`s in three columns, an icon over a 12 sp label, with the count in a small
  `accentDark` badge on the icon's corner; a count over 999 reads "999+". In the Rows style they
  are `SidebarEntry` rows with the count at the end. The current mailbox has an `accentDark`
  fill. Spam keeps the app's name, although the design calls it Junk; see
  [combined folders](folders.md#combined-folders).
- **Folders** starts with a "Folders" caption in `text2` that ends with a New folder button, which
  opens Settings › Folders, because each account still creates its own folders. The user's own
  folders, [shared by every account](folders.md#shared-folders), follow as 36 dp
  `SidebarFolderEntry` rows, indented 16 dp per level, with the count at the end. A folder with
  subfolders shows an open folder icon while expanded and a closed one while collapsed. Tapping
  the icon folds the subfolders away, and the folder then counts their mail with its own. Tapping
  the row opens the folder. Collapsed folders last until the app process ends.
- A folder's icon shows the folder's color, both the closed `folder` icon and the open
  `folder_open` one. A long press on one of the user's own folders offers **Color**, which opens
  the same `FolderColorSheet` as Settings › Folders. A shared folder row has one color, and
  setting it writes that color to every account's folder in the group. The **Folder colors**
  switch on the Sidebar page hides every tint without deleting the stored colors. See
  [folder colors](folders.md#folder-colors).
- A hairline divider separates the two sections, in the order the user chose.
- Settings › Appearance & behavior › Sidebar arranges the sidebar for every account. Dragging a
  section's handle moves it, and its switch hides it, except for the last section shown.
  **Mailboxes** chooses Grid or Rows, and **Counts** chooses Unread, Total, or Off. Each change is
  saved at once in `MailBehavior.sidebar` (`SidebarLayout`). Grid keeps the stored value `chips`
  from the pills it replaced, so a saved choice still reads.

Only one item is selected across the inbox row, tiles, and rows. Each page, including each folder,
keeps its saved state, such as a search, while another page is shown. Settings subpages stay under
Settings; see [Settings](settings.md) and [Folders and rules](folders.md).

## Show mail from

The sheet titled "Show mail from" chooses whose mail every mail page shows: all mail, one account,
or one address of one account (`MailScopeChoice`). A choice applies at once and the sheet stays
open; Done, the scrim, Back, or dragging it down closes it. The choice lasts until the app process
ends, like the current page. If its account is removed, the scope returns to all mail.

- An account's addresses are its sign-in name, when it is an address, and the addresses of its
  JMAP identities (`Identity/get`), each once regardless of case. Wildcard identities such as
  `*@example.org` are left out.
- A search field, "Search 15 addresses…", appears from 8 addresses. While it holds text, the
  sheet lists only the matching addresses.
- **All mail** comes first. Its subtitle reads "15 addresses" for one account or
  "2 accounts · 5 addresses" for several.
- **Pinned** lists the pinned addresses with a pin icon. A long press on any address pins it or
  unpins it. Pins are kept on the device by account and address, through `AddressPinStore`
  (shared preferences on Android, `NSUserDefaults` on iOS). Removing an account forgets its pins.
- With one account, **By domain** groups the addresses by the part after the `@`. A domain row
  shows its address count and expands to its addresses; it is not a scope of its own.
- With several accounts, **Accounts** lists each account with its address count. Tapping an
  account chooses all its addresses, and its chevron expands to each address.
- The footnote reads "Choose an account to see all its addresses, or one address. Long-press to
  pin." With one account it reads "Choose an address to see only its mail. Long-press to pin."
- The chosen row has an `accentDark` fill. The group holding the chosen scope opens
  expanded.

Counts follow **Counts**. All mail and accounts count their inboxes on the server. An address
counts the loaded inbox messages that list it in To or Cc, and a domain adds up its addresses.

Choosing a scope filters the messages every mail page has loaded: an account shows its own mail,
and an address shows its account's mail that the address sent or that lists it in To or Cc, so
its Sent and Drafts show too. The pages load the
same mail as before. The sidebar's counts follow the scope's account; with an address chosen they
are its account's server counts.

## Calendar

The component is app-independent, but it lives in the Mail repository. Calendar does not yet have
`Symbol`, the icon set, Figtree, or `PanelTokens`, and cross-repository library distribution is
undecided. Calendar adoption therefore needs either that decision or a deliberate copy.

## Checks

`SidebarNavigationTest` covers opening from the chevron, the header with the scope and Settings,
hiding the keyboard when it opens from search, the 336 dp width, navigating to Settings and back
with preserved inbox state, scrim dismissal, back dismissal, swiping open on and above the context
panel (but not on the message list), and swiping closed. `SidebarEditTest` covers choosing an
account and an address in "Show mail from", the header and counts that follow, the address filter
on the inbox, pinning, and the Sidebar settings page. `FoldersAndRulesTest` covers the mailbox
tiles, collapsing a folder, and opening one. `MailScopeChoiceTest`, `AccountAddressesTest`,
`AddressPinsTest`, `AddressPinRemovalTest`, `AddressPinStoreTest`, `ScopeHeaderTest`,
`SidebarCounterTest`, `SharedFolderTest`, and `SidebarLayoutTest` cover the scope filter, the
address lists and domain groups, address counts, pins and their storage, the header text, the
counts, the merged folders, and the saved layout. The new sidebar and sheet have not been checked
on a device. iOS runtime validation requires macOS/Xcode.
