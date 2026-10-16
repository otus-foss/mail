# Folders and rules

Implemented in `feature/folders` (GitHub issues #5 and #7). Folders are JMAP mailboxes, so every
mail app sees them. Rules move arriving mail into folders. They run on the mail server as one Sieve
script, so they keep working while the app is closed.

## Folders

- Loaded per account with `Mailbox/get` (RFC 8621, section 2): hierarchy, role, sort order,
  counts, and `myRights`. Accounts load in parallel and fail alone.
- A new load, such as the refresh after a move or a read mark, replaces the one running. The
  replaced load shows nothing and leaves the loading state to the newer one, so a slow load
  cannot bring back older folders or counts. A change finishes with the newest load, so a page
  that waits for a new folder sees it in the list.
- Identified by login and mailbox id, never by name, because names repeat under different parents.
- Ordering (`folderTree`): parents before children; system folders first (inbox, drafts, sent,
  archive, junk, trash), then other special uses, then the user's folders by `sortOrder` and name.
  While Read later is on, its folder sorts right after the inbox. A folder whose parent is missing
  becomes a root. A parent cycle cannot repeat or lose folders.
- The sidebar lists the built-in mailboxes once for every account, then the user's own folders,
  [shared by every account](#shared-folders), indented, with counts. Tapping a folder opens it as
  a page that reuses the inbox screen with "Search <folder>" and all of the folder's messages.
- **Settings › Folders** lists the user's own folders, without system folders, as trees. See
  [Settings › Folders](#settings--folders). Changes go to the server with `Mailbox/set`. Only the
  user's own folders (no role) can be renamed, moved, or deleted, from the
  [folder page](#the-folder-page) and by dragging. Actions follow `myRights`,
  `mayCreateTopLevelMailbox`, `maxMailboxDepth`, and `maxSizeMailboxName`.
- Otus nests folders at most five levels deep, a top-level folder being level 1, or less when the
  server's `maxMailboxDepth` is lower (`FolderLimits.levels`). Creating, moving, and nesting a
  folder, including the subfolders it brings along, stay within that. Folders another app nested
  deeper still show and open; their indent stops growing after the fifth level so the name keeps
  room.
- New folders are created with `isSubscribed: true` so IMAP clients that list only subscribed
  folders show them.
- Names are trimmed and must be unique among siblings without regard to case, as Stalwart checks.
- Deleting sends `onDestroyRemoveEmails: false`: a folder with mail or subfolders is refused and
  nothing is lost. The user must empty it first.
- An open message offers **Move**, filing it with one `Email/set` patch of `mailboxIds` that
  leaves the folder it is shown in and joins the target. **Archive** and **Trash** use the folder
  with the `archive` or `trash` role. Archive also leaves the inbox, and Trash leaves every folder,
  so trashed mail does not stay in Read later. Other folders the message is in stay as they are.
  All three need `mayRemoveItems` on the current folder and `mayAddItems` on the target.
- **Move**, in the open message's bar or menu and in the selection bar, opens the Move to sheet.
  It shows the message, or a selection's count and senders with a small avatar each, a folder search, pills for Inbox, Read
  later, Archive, Spam, and Trash where every message can go, a suggested folder, and the user's
  own folders as a tree. A folder with nested folders has a chevron that collapses them for as
  long as the sheet is open; a search shows every match. Tapping any folder or pill moves the
  messages at once and closes the sheet. The suggestion
  comes from one `Email/query` filtered by `from` (newest 50 of the sender's messages) and one
  `Email/get` of their `mailboxIds`: the own folder holding most of them, never the current one.
  Nothing is stored, and the section stays hidden when the query fails or no folder qualifies.
  A selection from several senders gets no suggestion and no sort switch.
- Where the account runs rules, the sheet's **Also sort future mail** switch also adds a rule for
  the tapped folder. The rule matches the sender, the sender's domain, the first To address, or a
  word from the subject, chosen in **Sort mail that…**. For a selection, the first message supplies
  them. The switch hides while the search field has focus and the keyboard is open, so the
  folders keep the room. A refused save is reported on the page. Rules edited in another app are left alone, with a
  notice to change them on the folder's page first.
  Read later never gets a rule, because it is a reading list rather than a destination.

## Settings › Folders

The header has the back chevron and "Folders" only. With several accounts, a card under it picks
whose folders show: "All accounts", with a subtitle such as "2 accounts · 11 folders", or one
account, such as "6 folders". It opens a sheet with All accounts first, then each account. The hint
"Tap a folder to edit it, drag to reorder." follows.

- In All accounts, each account's folders sit under a row with its avatar, name, and folder count.
  Tapping the row folds them away. With one account, or one account chosen, there are no account
  rows.
- A folder row has the folder icon in the folder's color, the name, and its first rule as a
  compact chip, with "+1" and so on for the others. Rows show no message or folder counts. A
  parent's icon is a toggle: it shows `folder_open` while its subfolders show and `folder` once
  they fold. The folded rows are kept in saved state.
- Tapping a folder opens its page (`MailPage.FolderDetail`), which holds its name, color, rules,
  and delete. A plus button, labelled New folder for screen readers, creates a folder at the top
  level: in All accounts it sits on each account's row, and with one account it follows the hint.
  It does nothing while another change saves.
- Thin `bgLight` lines run down under each parent's folder icon beside its subfolders, so the
  nesting reads as a tree.
- Nothing hides a folder: every folder always shows in the sidebar.

### Reorder and nest

Dragging the handle at a row's end drags it. Holding a row instead opens a `QuickActionBar` at
the finger with **Rename**, **Move into**, **Color**, and **Delete**, each as far as the server
and the folder allow: Delete waits until the folder is empty, and a shared mailbox has none. Each
opens the same popup as the folder's page. The dragged row
follows the finger with a `bgLight` fill, and the other rows stay where they are. An accent line
shows where the folder will land. The page uses the design system's `ReorderableTree`, which
judges the target by the dragged row's center.

- Over the upper or lower quarter of a row, the line sits in the gap above or below that row,
  indented to the depth of the row below the gap. Releasing puts the folder in that row's parent,
  right before it. In the gap after the last row, the line takes the last row's depth, and the
  folder goes right after that row in the same parent.
- Over the middle half of a row, the line sits under that row, one level deeper. Releasing puts
  the folder inside it as its first subfolder. This works on a collapsed folder too, and on a
  folder without subfolders.
- No line shows over the dragged row itself or the gaps right beside it. No line shows where the
  server would refuse the drop, so releasing there does nothing. A haptic marks each new line.
- Screen readers get Move up and Move down on each row. They move the folder into the gap above
  the previous row or below the next one, and only appear where that drop is allowed.

- `FolderPlacement` turns a drop into one `Mailbox/set` (RFC 8621, section 2). It sets the new
  `parentId` when the parent changes, and the `sortOrder` of each folder that needs a new number.
  The moved folder gets a number between its new neighbors while a whole number fits there.
  Otherwise the siblings are numbered again in steps of 10, and only the numbers that change are
  sent. `sortOrder` is an UnsignedInt, so numbers stay between 0 and 2^32 - 1. Each folder's
  number is always strictly between its neighbors', so names never decide the order.
- System folders and Read later never move. A folder needs `mayRename`, which RFC 8621 also
  requires to change its parent. Nesting checks the same rules as **Move**: the new parent needs
  `mayCreateChild`, the result must fit `FolderLimits.levels`, a folder cannot go inside itself, and no
  sibling may have the same name in any case. A drop the server would refuse does nothing.
- The list shows the new order at once. A load that started before the drop cannot bring the old
  order back. If the server refuses, the list goes back and the page says why. The next load
  replaces both.
- Drops stay inside one account. A shared mailbox moves only among the mailboxes of the account
  it comes from.

### Shared mailboxes

JMAP shares a mailbox by giving the login another account in its session with
`isPersonal: false` (RFC 8620, section 2). Each login loads its own mailboxes, then the mailboxes
of every such account that has mail. Their `myRights` say what the login may do. A shared account
that fails to load is left out. The code calls these `SharedMailboxes`, to keep them apart from
[shared folders](#shared-folders), which merge the user's own folders by path.

- They stay out of `AccountFolders.folders`, so the sidebar, folder pages, move sheet, and pills
  don't list them yet. Settings › Folders lists them after the login's own folders.
- A shared mailbox's row reads "Shared by <owner> · can read", or "· can edit" when it has
  `mayAddItems`. The owner is the name the server gives the shared account, usually an address.
- In All accounts, a shared mailbox shows once. When its owner is another connected account (by
  the server's account name or the login's name) and that account has a mailbox with the same id,
  it shows there as the owner's own folder, with the same "Shared by <owner> · can read" line. It
  reads "can edit" when any login it is shared with has `mayAddItems`. Otherwise it shows under the
  login that sees it.
- Changes to a shared mailbox go to the account it comes from. It has no color, because colors
  are keyed by login and mailbox id, and a shared mailbox's id can repeat one of the login's own.
  It has no rules either, because Sieve files mail only into the login's own mailboxes.

### The folder page

Tapping a folder in Settings › Folders opens its page (`MailPage.FolderDetail`,
`FolderDetailScreen`). The header has the back chevron and the folder's name. A line in `text2`
follows, such as "45 messages · 3 unread · 2 folders inside". Parts that are zero are left out,
and an empty folder reads "No messages". Then come these rows:

- **Name** shows the name in a value button and opens the rename popup. It does nothing for a
  folder without `mayRename`.
- **Move into** shows the parent folder, or "Top level", and opens a `ChoiceSheet` of the places
  the folder can go: the top level and each folder by its path, such as "Clients / Acme". It
  leaves out system folders and Read later, the folder itself, its subfolders, folders that don't
  take subfolders, places deeper than five levels, and folders that already hold one with its name. Choosing one moves the
  folder at once with the same `Mailbox/set` as dragging. This is how a folder goes into one that
  has no subfolders yet.
- **Color**, "Only changes the folder icon", opens the color sheet. The color stays on this device
  (see [Folder colors](#folder-colors)).
- **Rules**, "New mail that matches any of these goes here", lists the folder's rules as
  full-width chips and ends with a dashed **Add rule** chip. See [Rules](#rules). Below them,
  each folder above it that has rules follows, top level first, under "Checked first, in
  Clients. Mail that matches goes there." Those chips are faded, and tapping one opens that
  folder's page.
- **Delete folder** asks first and then deletes the folder and its rules. It stays off, with
  "Empty its messages and subfolders first", until the folder has no messages and no subfolders.
  Once the folder is gone, the page leads back to Settings › Folders.

A mailbox shared with the login shows no Color and no Rules row. The Rules row also hides when the
account's server has no Sieve. System folders don't open this page, and the page shows them no
Color or Rules row either.

## Combined folders

The sidebar's Mailboxes section always lists Drafts, Sent, Archive, Spam, and Trash once for all
accounts, after Inbox and Read later. Each appears once any account has a folder with its role.
The **Combined folders** switch that turned this on is gone; a saved value is ignored.

- Each entry stands for one role (RFC 8621, section 2): `drafts`, `sent`, `archive`, `junk`, and
  `trash`. The app names them itself, because servers name them differently. Stalwart, for one,
  calls three of them "Sent Items", "Junk Mail", and "Deleted Items".
- Spam and Trash are separate folders. The `junk` role marks mail the server or the user judged to
  be spam, and `trash` marks deleted mail (RFC 6154, section 2). The app shows `junk` as Spam so
  it is not mistaken for deleted mail. The sidebar spec calls it Junk; the app keeps Spam.
- Tapping an entry opens a folder page that loads every account's folder with that role
  (`MailScope.Role`) and files its messages under the app's name, so search treats them as one
  folder. Moves, marks, and Read later act on each message's own account, as on any folder page.
- Settings › Folders still lists every account's folders in full, and the move picker still names
  the server's folders, because a move stays inside one account.

## Shared folders

JMAP has no folder that spans accounts: each mailbox belongs to one account (RFC 8621, section 2).
The sidebar merges the user's own folders by path instead, without a new stored model.
`sharedFolders` treats folders whose names match from the top level down, ignoring case, as one
folder. Clients › Acme in two accounts is one row, and tapping it loads both accounts' folders
through `MailScope.Folders`. Its count adds up the accounts' folders, or counts one account's
while that account is chosen in the rail.

- Folders keep the order of the first account that has them; another account's extra folders
  follow.
- System folders and Read later are left out, and their subfolders move up a level. Folders inside
  Trash or Junk are left out entirely.
- Creating, renaming, moving, and deleting still act on one account's folder in Settings › Folders.
  Renaming a folder in one account splits it from the others until their names match again.
- `MailPage.Shared` saves the lower-case path. If no account has that path once every account's
  folders load, the page returns to the Inbox. While an account fails to load, the page stays.

## Folder colors

The user can give any of their own folders one of eight colors: Red, Orange, Yellow, Green, Teal,
Blue, Purple, or Pink. The color tints only the `folder` and `folder_open` icon. The name, count,
row background, and selected state keep their usual look. Default keeps the plain icon.

- Long-pressing a folder in the sidebar offers **Color**, which opens a `ChoiceSheet` with Default
  first in its own group, then the eight colors, each with a `FolderColorSwatch`. A choice applies
  at once. The folder's page in Settings › Folders offers the same sheet.
- Only folders without a role can have a color. Inbox, Drafts, Sent, Archive, Spam, Trash, and
  Read later never offer **Color** and never show a tint.
- `FolderColors` keys each color by login and mailbox id (`FolderKey`), never by name or path.
  A rename or move keeps the color. A folder deleted and made again gets a new id and starts
  without one.
- RFC 8621, section 2 gives a Mailbox no color property, so the app does not write one to the
  server. Colors stay on the device in `AndroidFolderColorStore` (SharedPreferences) or
  `IosFolderColorStore` (NSUserDefaults) and do not sync. The store keeps the color's name, such
  as `Teal`. The design system's `FolderColor.named` turns it into a tint for the current theme,
  and an unknown or missing name reads as Default.
- A [shared folder](#shared-folders) row has one color. Setting it writes the same name for every
  account's folder in the group. If the accounts disagree, the row shows the first account's
  color and keeps the others stored, so a rename that splits the group gives each folder back its
  own color.
- Deleting a folder forgets its color once the server confirms the delete. Removing an account
  forgets all of that account's colors.
- The **Folder colors** switch on the Sidebar settings page, on by default, hides every tint. It
  does not delete the stored colors, and **Color** still shows the saved choice while it is off.
- Tints show in the sidebar's folder rows, Settings › Folders, the Move to sheet's folder list and
  suggestion, and the action bar's **Move to** picker and its folder sheet. A disabled row keeps its disabled icon color.
- The color is decoration. It never appears in a row's content description, and the color sheet
  reads each option by its name.

## Read later

Off by default. The **Read later** switch in Appearance & behavior gives each account a folder named
"Read later", adds a **Read later** button to an open message and to the swipe choices, and adds a
Read later pill to the inbox row that shows every account's saved mail. See
[inbox pills](inbox.md#inbox-pills).

- **Read later** adds the folder to the message's `mailboxIds` and leaves its other folders as
  they are, so a saved message stays in the inbox and its row shows a Read later label. Archiving
  it later keeps it in Read later. A saved message offers **Remove from Read later** instead,
  which takes only that folder away. On the Read later page, the message then leaves the list.
- A message must stay in at least one folder (RFC 8621, section 4.1.1), so **Remove from Read
  later** is offered only while the message is in another folder too. Otherwise Archive, Move,
  or Trash files it.
- When the account's `maxMailboxesPerEmail` (RFC 8621, section 1.3.1) leaves no room for another
  folder, **Read later** moves the message there instead, like Move.

### The folder

- The folder has no role. RFC 8621 allows only roles from the IANA "IMAP Mailbox Name Attributes"
  registry, which has none for it. The nearest one, `snoozed` (RFC 9979), is for mail the server
  brings back at a set time. Other mail apps show the folder as one of the user's own.
- Otus saves the folder's id per login in app-private storage (`ReadLaterStore`:
  `SharedPreferences` on Android, `NSUserDefaults` on iOS), so renaming or moving the folder
  keeps it. The saved folder stops counting once it has a role or sits inside Trash or Junk,
  where its mail can be purged. Thunderbird, for one, deletes a folder by moving it into Trash.
- Without a usable saved id, as on a new device, Otus uses a top-level folder named "Read later"
  in any case, or creates one at the top level with the same `Mailbox/set` as **New folder**. A
  device without the saved id cannot recognize a renamed folder, so it creates a new one.
- `ReadLaterFolders` runs on every folder load while the switch is on. Turning it on, adding an
  account, and a folder deleted elsewhere all go through the same step. `MailServices` keeps one
  per process, and its lock keeps overlapping loads, even those of a recreated activity, from
  creating two folders.
- A create the server may already have applied always finishes, and its id is saved at once, even
  when a newer load replaces the one that sent it. The replaced load shows nothing. A load still
  running when the switch turns off shows no Read later folder.
- Otus creates no folder for accounts that fail to load or whose server refuses top-level folders
  (`mayCreateTopLevelMailbox`). Such an account still uses a Read later folder it already has.
- The folder sorts right after the inbox and has the bookmark symbol. The sidebar shows it as the
  Read later mailbox, after Inbox, which loads every account's Read later folder. Once every
  account's folders load without one, as after the switch turns off, its page returns to the Inbox. Its subfolders
  join the user's [shared folders](#shared-folders).
- Saving needs `mayAddItems` on Read later; removing needs `mayRemoveItems` on it. A move also
  needs `mayRemoveItems` on the folder the message is shown in. `readLaterStep` decides which
  of these a message gets, and IMAP apps see a saved message in both folders.
- Turning the switch off hides the action, the swipe choice, and the pill, and returns a swipe set
  to Read later to its default. The folder and its mail stay on the server as an ordinary folder. While
  the switch is on, a deleted Read later folder is created again on the next load.

## Rules

Rules move arriving mail into folders. They run on the mail server as Sieve (RFC 5228), managed
over JMAP for Sieve (RFC 9661), so they work while the app is closed. JMAP has no rules object.

### The model

A folder's rules are "any of": new mail that matches one of them goes into the folder. Each rule
is one chip, either a single condition or an "All of" group of several. A condition tests:

| Kind | Chip | Needs |
| --- | --- | --- |
| From, To, Cc | One address, a whole domain ("@example.org"), or the part before the @ at any domain ("billing@ any domain") | `fileinto` |
| Subject | Contains, starts with, or matches a wildcard pattern (`*`, `?`) | `fileinto` |
| Header | Any header, "is" or "contains" | `fileinto` |
| List | The `List-Id` of a mailing list (RFC 2919) | `fileinto` |
| Body | Contains | `body` |
| Size | Over or under a size | `fileinto` |
| When | Days, and a time range or outside it | `date` and `relational` |
| Spam | The server's spam score from a number on | `spamtest` or `spamtestplus`, and `relational` |

Address, text, header, and list conditions can be negated ("isn't from", "doesn't contain").
Chips follow the "Every kind of rule" board: dim words say how a condition matches, bright text
what it matches, and plain "contains" or "is" adds no word.

### Capability gating

- The account needs `urn:ietf:params:jmap:sieve` with `fileinto` in its `sieveExtensions`.
  Without them, the folder page hides its Rules row and an open message offers no
  **Rule for sender**.
- Body, When, and Spam conditions need the extensions in the table. The Add rule sheet offers only
  From, To, Subject, and Mailing list, which every Sieve server with `fileinto` runs.
- With `mailboxid` (RFC 9042), a rule files by mailbox id, so renaming or moving the folder keeps
  it working. Stalwart also needs `mailbox` (RFC 5490) declared for `:mailboxid`, so the block
  requires both.

### The Otus block

Otus owns only a marked block in the account's active script and never changes a byte outside it:

```sieve
# BEGIN otus-rules
# Otus Mail writes this block. Change these rules in Otus Mail; edits here are replaced.
# otus-rules-v2 {...}
require ["fileinto", "mailbox", "mailboxid"];
if address :is "from" "jana@website-agency.example" {
    fileinto :mailboxid "M7" "Projects";
    stop;
}
# END otus-rules
```

- Without a block, the app puts one right after the script's leading `require` commands, because
  RFC 5228 (section 3.2) allows a `require` only before every other command. A script without
  them gets the block at the top. The block's own `require` then still counts as leading.
- The block uses CRLF line ends. The app finds it by its begin and end lines in any line ending.
- Each folder's rules become one `if anyof (...)` that files the message and stops, in the
  account's folder order, so the first folder that matches wins. A parent comes before its
  subfolders, so mail its rules match lands in the parent and never reaches a subfolder. Rules
  are not inherited: a parent's rules file only into the parent.
- Filing uses `fileinto :mailboxid "<id>" "<path>"`. The path is the folder's names from the top
  joined with "/", which a server uses when the id no longer exists. Without `mailboxid`, the
  rule files into the path alone. Whether Stalwart resolves a "/" path to a nested folder is not
  verified.
- With no script at all, saving creates "Otus Mail" holding just the block and activates it.
- A rule for a folder the account no longer has falls back to the inbox. Deleting a folder from
  its page also deletes its rules.

### Reading back and hand edits

- The `# otus-rules-v2` comment holds the rules as JSON. On load the app reads the JSON,
  regenerates the block, and compares it with the script's block, ignoring line ends and trailing
  spaces.
- A difference means someone edited the block in another app. The folder page then asks
  "Replace edits made elsewhere?" before any change saves. A block without a readable JSON line
  counts as edited too.
- The move sheet's **Also sort future mail** never replaces such edits. It reports that the rules
  changed elsewhere and asks the user to change them on the folder's page.

### Saving

- A save reads the scripts again, splices the new block into the target script, and checks the
  size against `maxSizeScript`.
- It uploads the script as a blob (RFC 8620, section 6.1) and calls `SieveScript/validate` first.
  When the server refuses the script, the page shows its explanation and the old script keeps
  running.
- `SieveScript/set` then updates the script with `ifInState` from the last load, so a change made
  on another device is refused (`stateMismatch`) instead of overwritten. The app reloads and the
  user saves again. A script that is not running yet is activated in the same call with
  `onSuccessActivateScript`.
- Rules load like folders: only the newest load shows its rules. A save stays busy until the next
  load ends, so the next change starts from the saved rules and their state.

### The vacation reply

Stalwart builds its vacation reply into a reserved script named `vacation`, which
`SieveScript/set` cannot edit. While that script is the active one, the folder page says "Your
vacation reply is on. Turn it off to use rules" and saves nothing.

### Migration from the old Rules page

Accounts that still run the old separate "Otus Mail" script, with its `# otus-rules-v1 {...}`
line, get those rules converted on the first load. The first save writes them into the block.

- When the old script kept another script running with `include`, the block goes to the top of
  that kept script, after its `require` lines, and that script becomes the active one. Otherwise
  "Otus Mail" becomes just the block.
- An old script that was not running held rules that were off, so it stays as it is.
- The new model has no place for these, so the migration drops them: "mark as read", paused rules,
  regular expressions, attachment type and name conditions, and rule names. A condition that cannot
  be converted drops its whole rule when every condition had to match, and only itself when any
  was enough.
- "To or Cc" becomes one rule for To and one for Cc. "Is" keeps the address, "ends with @domain"
  becomes the domain, and "starts with name@" the part before the @. Other address comparisons
  become header conditions.

### Add rule

**Add rule** on the folder page opens the "Sort into <folder>" sheet (`AddRuleSheet`).

- Kind chips choose From, To, Subject, or Mailing list. For From and To, the search field suggests
  addresses from the account's loaded mail, each with its avatar, name, and message count, and
  "Anyone at @domain" for their domains, most messages first. A typed address, "@domain", or
  "name@" is offered too. Subject takes the typed text as "contains", and Mailing list takes it as
  the list's id; loaded mail carries no `List-Id` yet.
- A preview reads "New mail that matches goes to <folder>" over the chip the rule adds. **Add**
  stays off until there is a choice.
- Tapping a chip on the folder page opens the same sheet with **Save** and **Delete rule**. Chips
  the sheet can't show whole (Cc, Header, Body, Size, When, Spam, negated or non-"contains"
  subjects, and groups) open a popup with **Delete rule** only.
- An open message's **Rule for sender** lists the account's own folders, then opens that folder's
  sheet with a From rule for the sender selected.

### Move the mail already here

Sieve sees mail only as it arrives. Once typing pauses, the sheet asks how much mail in the Inbox
and Archive the rule matches, with `Email/query` and `calculateTotal`. When there is some, the
switch "Move the N that are already here", "From Inbox and Archive", appears. It starts on, as in
the design. While it is on, a saved rule moves that mail with `Email/set` patches of `mailboxIds`, in batches within
`maxObjectsInSet`.

The query maps conditions to `Email/query` filters (RFC 8621, section 4.4.1): addresses to `from`,
`to`, or `cc` text, subject and body "contains" to `subject` and `body`, headers "contains" and
lists to `header`, sizes to `minSize` and `maxSize`, and negation to `NOT`. JMAP searches text, so
"jana@x" also finds "ojana@x". The switch stays hidden when the rule has a condition without a
filter (the part before the @, "starts with", wildcards, header "is", When, Spam), when the server
can't be read, or when nothing matches. The move sheet's **Also sort future mail** offers the same
move for older mail after it saves its rule.

## Stalwart notes

Checked in Stalwart's source (`main`, October 2026), not yet against a live server:

- Deleting an active script fails with `scriptIsActive`, not RFC 9661's `sieveIsActive`. The app
  never deletes its script; it only deactivates it.
- The name `vacation` is reserved; that script cannot be edited through `SieveScript/set`.
- `:mailboxid` takes the JMAP mailbox id. An unknown id falls back to the name, then the inbox.
- Mailbox names are unique among siblings regardless of case (`alreadyExists`).

## Boundaries

Folders and rules share one feature because rules target folders. `domain` holds the folder tree,
name and move checks, and the rule model (`FolderRule`, `RuleCondition`, `RuleSupport`). `data`
holds the JMAP repositories, the block writer and reader, the v1 migration, and the `Email/query`
filters. Presentation state lives in `FoldersState`, `FolderColorsState`, and `FolderRulesState`;
chip text is plain data in `RuleChipText`. The inbox feature does not import folders: app wiring
supplies move targets and the rule shortcut as `MessageActions`, and `SenderRules` hosts the
Rule for sender flow. Chips, the Add rule chip, and the sheet come from the design system.

## Checks

- Domain: `FolderTreeTest`, `FolderChangesTest`, `RuleSendersTest`, `SortConditionsTest`,
  `ReadLaterFoldersTest` (saved, adopted, and created folders; saved folders moved into Trash or
  given a role; overlapping and replaced loads; the switch turning off during a load),
  `MessageFoldersTest` (saving, removing, or moving into Read later; which folders label a row),
  `CombinedFolderTest` (one entry per role any account has, with every account's folder, in sidebar
  order), `FolderPlacementTest` (numbers between neighbors, renumbering only what changes, nesting,
  refused drops), `SharedMailboxesTest` (a shared mailbox shows once, under a connected owner, as
  shared by that owner), `SharedFolderTest` (folders merged by path in any case, system folders
  and Read later lifted, Trash left out).
- Data: `JmapMailSourceTest` (a role loads that folder of every account under one name),
  `JmapFolderRepositoryTest`, `JmapSharedMailboxTest` (shared accounts load beside the login's
  own folders, and a drop sends one `Mailbox/set`), `RuleBlockTest` (the block's Sieve and JSON,
  read-back, hand edits), `BlockSpliceTest` (placement after leading `require` lines, other bytes
  kept, CRLF), `LegacyRulesTest` (what the v1 migration converts and drops),
  `JmapFolderRuleRepositoryTest` (validate before save, `ifInState`, activation, the vacation
  script, migration), `ExistingMailTest` (the `Email/query` filters and batched moves),
  `JmapMessageMoverTest`.
- Folder colors: `FolderColorsTest` (keys, shared-folder groups, defaults for unknown names),
  `FolderColorsStateTest` (the switch hides colors without forgetting them, role folders and Read
  later stay plain, deletes and account removal forget colors), `FolderColorRemovalTest`,
  `FolderColorStoreTest` (Android storage round trip), `MoveTargetPathTest` (move targets carry
  their color), and the UI test `FolderColorsUiTest` (from the sidebar, the sheet lists Default
  first and reads names, a choice is saved at once, system folders offer no **Color**, the switch
  keeps colors).
- Presentation: `FolderRulesStateTest` (saving, edits made elsewhere, refusals),
  `RuleChipTextTest` (every example on the "Every kind of rule" board), `RuleSuggestionsTest`
  (suggestion order and counts, typed addresses, which rules the sheet can edit),
  `FolderPlacementStateTest` (a drop shows at once, a refusal puts the list back, an older load
  cannot undo it).
- UI: `FolderListUiTest` (the account picker, folding a parent, a shared mailbox's line shown
  once, dragging and nesting sending `Mailbox/set`).
- UI: `FolderDetailUiTest` (the rows and summary, Delete off while the folder has mail, deleting an
  empty folder and leading back, choosing a color, Rules hidden without Sieve and for a shared
  mailbox). `FolderRulesUiTest` (Add rule's kind chips, suggestions, preview, a saved block the
  fake server receives, the chip after the reload, Move the N sending `Email/set`; editing and
  deleting a chip; a chip the sheet cannot show; Rule for sender).
- UI: `FoldersAndRulesTest` drives the app against a stateful fake server: sidebar folders and
  folder pages, creating a folder, a migrated rule shown on its folder row, and moving a message.
  It also turns on Read later and checks that its mailbox follows Inbox and that its subfolder
  joins the user's folders, and that rows name a message's other folders. It checks that Spam and
  Trash replace the server's folders above the user's own, that Spam opens its folder, and that a
  collapsed folder counts its subfolders' mail. `MessageActionsTest` and `SwipeActionsTest` save a
  message in place, remove it, and trash it out of every folder.
- `ServerIntegrationTest` creates and deletes a mailbox and uploads a blob on a real server when
  configured. It has not been run against Stalwart yet.

Not verified yet: delivery through a live Stalwart (Sieve runs on delivery, so this needs a real
message, not `Email/import`), filing by path on a server without `mailboxid`, Pixel device
checks, and iOS runtime. The folder page and the Add rule sheet follow the supplied screenshots,
not Figma frames.

## Open

- Saved filters and views (#4, #5): turning `InboxSearch` into a reusable filter model.
- Creating a rule from a whole search, not only from a sender.
- Editing Cc, Header, Body, Size, When, and Spam conditions, and "All of" groups, in the app.
- ManageSieve (RFC 5804) for IMAP servers; the Sieve writer is protocol-independent.
- Creating, renaming, or deleting a shared folder in every account at once.
