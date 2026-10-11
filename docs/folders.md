# Folders and rules

Implemented in `feature/folders` (GitHub issues #5 and #7). Folders are JMAP mailboxes, so every
mail app sees them. Rules move arriving mail into folders. They run on the mail server as one Sieve
script, so they keep working while the app is closed.

## Folders

- Loaded per account with `Mailbox/get` (RFC 8621, section 2): hierarchy, role, sort order,
  counts, and `myRights`. Accounts load in parallel and fail alone.
- Identified by login and mailbox id, never by name, because names repeat under different parents.
- Ordering (`folderTree`): parents before children; system folders first (inbox, drafts, sent,
  archive, junk, trash), then other special uses, then the user's folders by `sortOrder` and name.
  A folder whose parent is missing becomes a root. A parent cycle cannot repeat or lose folders.
- The sidebar lists each account's folders under its name, indented, with unread counts. With one
  account, its inbox is the unified Inbox and is not listed twice. Tapping a folder opens it as a
  page that reuses the inbox screen with "Search <folder>" and all of the folder's messages.
- **Settings › Folders** creates, renames, moves, and deletes folders with `Mailbox/set`. Only the
  user's own folders (no role) can be renamed, moved, or deleted. Actions follow `myRights`,
  `mayCreateTopLevelMailbox`, `maxMailboxDepth`, and `maxSizeMailboxName`.
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

## Read later

Off by default. The **Read later** switch in Appearance & behavior gives each account a folder named
"Read later", and adds a **Read later** button to an open message and to the swipe choices.

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
  `SharedPreferences` on Android, `NSUserDefaults` on iOS), so renaming the folder keeps it.
  Without a saved id, as on a new device, Otus uses a top-level folder named "Read later" in any
  case, or creates one at the top level with the same `Mailbox/set` as **New folder**. A device
  without the saved id cannot recognize a renamed folder, so it creates a new one.
- `ReadLaterFolders` runs on every folder load while the switch is on. Turning it on, adding an
  account, and a folder deleted elsewhere all go through the same step. A lock keeps overlapping
  loads from creating two folders. A creation already sent finishes even when a newer load
  replaces the one that sent it.
- Accounts that fail to load, or whose server refuses top-level folders
  (`mayCreateTopLevelMailbox`), get no folder. Their mail does not offer the action.
- The folder sorts right after the inbox and has the bookmark symbol. With one account, the sidebar
  lists it right under the unified Inbox.
- Saving needs `mayAddItems` on Read later; removing needs `mayRemoveItems` on it. A move also
  needs `mayRemoveItems` on the folder the message is shown in. `readLaterStep` decides which
  of these a message gets, and IMAP apps see a saved message in both folders.
- Turning the switch off hides the action and the swipe choice, and returns a swipe set to Read
  later to its default. The folder and its mail stay on the server as an ordinary folder. While
  the switch is on, a deleted Read later folder is created again on the next load.

## Rules

- Available when the account offers `urn:ietf:params:jmap:sieve` (RFC 9661) and its
  `sieveExtensions` include `fileinto` and `mailboxid` (RFC 9042). Other extensions unlock more:
  `regex` (regular expressions), `mime` (attachment type and name), `body` (body text),
  `imap4flags` (mark as read), and `include` (keep another script running).
- **Settings › Rules** lists each account's rules in order, with whether they run. A rule has
  conditions (all or any), a target folder, and optionally "mark as read". The first matching rule
  moves the message and later rules are skipped. Rules can be reordered, paused, and deleted.
- An open message offers **Rule for sender**: a new rule with "From is <address>".
- Saving can also move matching mail already in the inbox. Sieve runs only on delivery, so the app
  checks the newest 500 inbox messages, matching headers and attachment details locally, and moves
  matches in batches within `maxObjectsInSet`. Rules that check the body cannot do this.

### Conditions

| Field | Compared with |
| --- | --- |
| From, To, Cc, To or Cc | "is", "starts with", "ends with": the address. "contains" and regex: the whole header, display names included. |
| Subject | The subject header. |
| Body | Body text (`body :text`); contains, doesn't contain, or regex. |
| Attachment type | Each MIME part's `type/subtype`, such as `text/calendar`. |
| Attachment name | Each part's `filename` or `name` parameter, such as `*.ics`. |

Comparisons ignore case with the server's default comparator. "Starts with" and "ends with" use
`:matches` with `*`, `?`, and `\` escaped. Blind copies leave no header, so "Bcc" is not offered;
"To or Cc" covers explicit copies.

### The script

One script named **Otus Mail** per account. It starts with a comment line holding the rules as
JSON (`# otus-rules-v1 {…}`), then the generated Sieve with CRLF line ends:

```sieve
# Otus Mail rules. Edit them in Otus Mail; changes made elsewhere are detected.
# otus-rules-v1 {"rules":[…]}
require ["fileinto", "mailboxid"];

# Receipts
if header :contains "from" "amazon" {
    fileinto :mailboxid "M7" "INBOX";
    stop;
}
```

- Mail is filed by folder id, so renaming or moving a folder keeps its rules working. If the
  folder is deleted, Stalwart files into the inbox, the fallback name, so no mail is lost.
- On load the app rebuilds the Sieve from the JSON and compares. A difference means the script
  was edited in another app: the rules page says so, and saving asks before replacing it.
  Conditions written by a newer app version count as a difference too, so they are never dropped.
- Saving uploads the script as a blob (RFC 8620, section 6.1), then creates or updates it with
  `SieveScript/set`. The request carries `ifInState` from the last load, so a change from another
  device is refused (`stateMismatch`); the app reloads and the user saves again.

### One active script

A server runs one Sieve script at a time. Decisions taken for now:

- The first rule turns the script on only when no other script runs. Otherwise the rules page
  shows what runs instead and asks before replacing it.
- With the `include` extension, the user can keep the other script running after the rules
  (`include :personal :optional "<name>"`), for mail no rule moved.
- A script named `vacation` is never kept that way. Stalwart builds its `VacationResponse` into
  that script and keeps it while the reply is off, so including it would send replies the user
  turned off. Turning rules on switches the vacation reply off, and the app says so first.
- Turning the vacation reply on in another app stops the rules. The rules page then shows that
  they are off because of it. Owning vacation inside the rules script is a possible later change.
- No raw Sieve editing, and no rules run by the app itself: on servers without Sieve, folders and
  inbox search still work, but nothing moves automatically.

## Stalwart notes

Checked in Stalwart's source (`main`, October 2026), not yet against a live server:

- Deleting an active script fails with `scriptIsActive`, not RFC 9661's `sieveIsActive`. The app
  never deletes its script; it only deactivates it.
- The name `vacation` is reserved; that script cannot be edited through `SieveScript/set`.
- `:mailboxid` takes the JMAP mailbox id. An unknown id falls back to the name, then the inbox.
- Mailbox names are unique among siblings regardless of case (`alreadyExists`).

## Boundaries

Folders and rules share one feature because rules target folders. `domain` holds the folder tree,
name and move checks, the rule model, and local rule matching. `data` holds the JMAP repositories,
the Sieve writer, and the script codec. Presentation state lives in `FoldersState`, `RulesState`,
and `RuleEditorState`. The inbox feature does not import folders: app wiring supplies move
targets and the rule shortcut as `MessageActions`. Popups reuse the design-system `DialogFrame`,
`ActionDialog`, `InputDialog`, and `ChoiceDialog`.

## Checks

- Domain: `FolderTreeTest`, `FolderChangesTest`, `RuleMatchingTest`, `RuleListsTest`,
  `ReadLaterFoldersTest` (saved, adopted, and created folders; overlapping and replaced loads),
  `MessageFoldersTest` (saving, removing, or moving into Read later; which folders label a row).
- Data: `JmapFolderRepositoryTest`, `JmapRuleRepositoryTest`, `RuleScriptCodecTest` (golden
  Sieve, escaping, read-back, edits made elsewhere), `JmapMessageMoverTest`.
- Presentation: `RulesStateTest` (activation choices, vacation never kept, failed saves keep the
  draft).
- UI: `FoldersAndRulesTest` drives the app against a stateful fake server: sidebar folders and
  folder pages, creating a folder, saving a rule as an active script, moving a message, and
  starting a rule from its sender. It also turns on Read later and checks the created folder's
  place in the sidebar, and that rows name a message's other folders. `MessageActionsTest` and
  `SwipeActionsTest` save a message in place, remove it, and trash it out of every folder.
- `ServerIntegrationTest` creates and deletes a mailbox and uploads a blob on a real server when
  configured. It has not been run against Stalwart yet.

Not verified yet: delivery through a live Stalwart (Sieve runs on delivery, so this needs a real
message, not `Email/import`), Pixel device checks, and iOS runtime. The folder and rule screens
have no Figma frames; their layout reuses existing settings components.

## Open

- Saved filters and views (#4, #5): turning `InboxSearch` into a reusable filter model.
- Creating a rule from a whole search, not only from a sender.
- ManageSieve (RFC 5804) for IMAP servers; the Sieve writer is protocol-independent.
- Collapsing nested folders in the sidebar.
