# Action bar

The message list, an open message, a selection of messages, and the compose page each show an
action bar at the bottom of the screen. The bar is Meta's `ActionBar`: up to three icon-only
buttons in a group, and a main button in a wider shell beside them. The user chooses the buttons
in Settings, either one bar per screen or one bar for every screen. Swipes keep their own
choices; see [swipe actions](inbox.md#swipe-actions).

## Implemented

### Modes

`ActionBarMode` decides which bar a screen shows.

- **Per screen** (`Adaptive`, the default) shows each screen's own bar: **Message list**,
  **Open message**, **Selected messages**, and **Writing**.
- **Same everywhere** (`Fixed`) shows the **Every screen** bar on all of them. A button that does
  not apply to the screen stays in its slot, disabled. Search, for example, is disabled on an open
  message, and Archive is disabled on the message list.

Switching modes keeps every saved bar, so switching back restores each screen's own bar.

### Message list

With nothing selected, the inbox and folder pages show the **Message list** bar
(`InboxBar`). The selection bar replaces it while rows are selected, and it hides while the
search field is open.

- **New mail** starts a blank draft. When the list is filtered to one account, the draft sends
  from it.
- **Search** opens the search field, as tapping the search prompt does.
- **Filters** grows the control panel to every filter, as the header's filter button does.
- **Mark all read** marks every unread message in the current results read, where the account
  allows it, in as few `Email/set` calls as each server allows. It is disabled when there is
  nothing to mark.

Actions that need a message, such as Archive in **Same everywhere** mode, are disabled here.

### Open message

Tapping a row opens the message as a page over the list (`MessageScreen`). The header is a
`ContextPanel` without pills: a back chevron, the subject, and a **More actions** button. The
chevron and system Back close the page. `MessageBody` shows the sender, the body, a note when only
its start was loaded, and the attachment names, and stays clear of the bar. The text body shows at
once. When the message has an HTML body, it replaces the text in the shared `HtmlView`, which
scrolls on its own. Remote images stay hidden until the user taps **Show images**, and a tapped
link opens in the browser or mail app. While the page is open, accessibility services skip the list under it.

The bar shows the **Open message** layout. **More actions** opens a menu in sections: what acts on
the page's own message (the other reply, Forward, Mark read or unread, Flag, View details, Print),
the sender (Add to contacts, Block sender, Spam), and the whole conversation (Move, Mute, Delete).
Its last section lists every other action the bar leaves out, such as Read later, Archive, or Rule
for sender, so each action of the open message is on the bar or in the menu. Mail's actions in the
menu carry the same labels and symbols as on the bar, and one that asks to be confirmed, such as
Delete forever, asks there too. An action that takes the message out of the list, such as
Archive, closes the page first. Marks keep the page open and show their new state on the button.
**Move** opens the Move to sheet (see [Folders and rules](folders.md)), and choosing a folder
closes the page and moves the message.

**Reply**, **Reply all**, and **Forward** open the compose page with a `ComposeRequest` built from
the message. **New mail** starts a blank draft from the message's account. When the main button
is a reply, its shell names who the reply goes to:

- **Reply** shows the sender's avatar (`BarToken.Person`) and name, or address when there is no
  name. Accessibility services read it as, for example, "Reply, to Grace Hopper".
- **Reply all** shows the sender's avatar and a count of the others (`BarToken.People`), and
  "3 people" beside them. The count covers the sender, To, and Cc, each address once, without the
  account's own address. With one person, it shows that person as Reply does.

This page replaced the Material `AlertDialog` preview and its `MessageActionRow`.

### Selection

Long-pressing a row selects it. While any row is selected, tapping a row adds or removes it
instead of opening it, and long-pressing toggles it too. Dragging up or down after a long press,
without lifting the finger, gives every row passed the pressed row's new state: drag from a newly
selected row to select a run of messages, or from a deselected row to clear them. Dragging back
restores the rows the finger leaves, and holding near the list's top or bottom edge scrolls it.
A selected row is tinted `accentDark` and
shows a check in place of the sender's avatar. System Back clears the selection. The selection
survives configuration changes, and a message that leaves the list leaves the selection.

While rows are selected, the list shows the **Selected messages** bar. Its main button shows a
count token and "message" or "messages" beside the main action, and accessibility services read
it as, for example, "Move, 3 messages". Each action ends the selection. **Move** ends it once the
user picks a folder.

### Actions

`MailAction` lists everything a bar can hold, and its `ActionTarget` decides which screens can
offer it:

| Target | Actions | Offered on |
| --- | --- | --- |
| Messages | Read, Flag, Read later, Archive, Trash, Spam, Not spam, Restore, Delete forever, Move, Suggested move, Move to | Open message, selected messages |
| One message | Rule for sender, Reply, Reply all, Forward, Follow up | Open message |
| List | Search, Filters, Mark all read, Empty spam or trash | Message list |
| Draft | Send, Attach, Discard | Writing |
| Anywhere | New mail | Message list, open message |

The **Every screen** bar can hold any of them. For messages, `offer` turns an action into a button
for one message or a selection, with a label for their current state, or returns nothing when
none of them can take it.

- **Read** and **Flag** turn the mark on for every message while any of them lacks it, and off
  when all have it. The label says which, such as **Mark read** or **Mark unread**.
- **Read later** saves the messages not saved yet. Once all are saved, it offers to remove them.
  It is a choice only while Read later is on; see [Read later](folders.md#read-later).
- **Archive**, **Delete**, and **Spam** move each message that can take the action to its
  account's folder with the `archive`, `trash`, or `junk` role. Delete moves the message to Trash
  rather than deleting it for good. Spam is new and works like Delete:
  the message leaves every folder it is in, Read later included. It moves the message and sets no
  keyword. Messages already in the folder, or whose account has none, are skipped.
- **Move** needs messages from one account, because folders belong to an account. Its Move to
  sheet offers the folders every selected message can move to, whether the rows were selected by
  long press, tap, or drag.
- **Rule for sender** needs exactly one message, so the selection bar never offers it.
- **Not spam**, **Restore**, **Delete forever**, **Empty spam or trash**, and **Follow up** work
  only in some folders; see [folder bars](#folder-bars).

### Move to a folder

A **Move to** button moves the messages to one folder the user picked, without the Move to
sheet. A bar can hold several, one per folder, such as Receipts, Travel, and Clients. The button
saves the folder's path, the folder names from the top level down, and finds the folder in each
message's own account by that path, ignoring case, as shared folder pages do. One button therefore
works in every account that has the folder.

- As a secondary button it shows the folder symbol. Its tooltip and accessibility label read
  "Move to Travel".
- As the main button it shows the folder's name and symbol.
- Tapping it moves the message, or each selected message, through the same move as the Move to
  sheet: the folder rights decide, the row leaves at once, and it comes back with a notice if the
  server refuses. A message whose account has no folder at that path, or that is already there, is
  skipped. When none of the messages can move there, the button is disabled.
- System folders and folders under Trash and Junk have no shared path, so a Move to button never
  targets them.

**Suggested move** moves the messages to the user's own folder where most of the sender's recent
mail is, the same suggestion the Move to sheet shows. Its label is that folder's name, and it is
disabled while there is no suggestion or the messages come from more than one sender. Mail asks
the server for the suggestion only while the bar shows Suggested move, so the **More actions**
menu does not offer it otherwise.

Actions follow the same folder rights as before; see
[folder pages and message actions](inbox.md#folder-pages-and-message-actions). An action the
current messages cannot take stays in its slot, disabled with a `text3` icon, so the buttons never
move. A swipe can be any action except Spam, Move, Rule for sender, and the folder actions.

**All actions**, under **Anywhere** in the picker, fits every bar. It shows a ⋯ icon, and tapping it
opens a Meta `ContextMenu` above the button with every action the screen offers, including those
already on the bar; actions the current messages cannot take are left out. It suits a bar kept to
a few buttons. A minimized bar hides it like any secondary button and keeps its own ⋯ button.

### Settings

The Mail behavior page's **Actions** section has an **Action bar** row, which opens
**Settings › Mail behavior › Action bar** (`ActionBarSettingsScreen`). The page's **Minimize**
switch, below **Layout**, shows only the main button, as a 62 dp round button at the bar's end, on every screen. The
other buttons then move into the page's menu, such as an open message's **More actions**.

The action bar page starts with a **Layout** row that shows the mode and opens a `ChoiceSheet`
with **Per screen** and **Same everywhere**. **Per screen** shows a card for **Message list**,
**Selected messages**, **Open message**, and **Writing**. **Same everywhere** shows one **Every
screen** card instead. Each card is a Meta `PreviewCard`: a title, where the bar shows, a sketch of
the page, and the saved bar. Tapping the card or its bar opens the bar's editor.

The editor (`ActionBarEditSheet`) is a `SheetFrame` titled with the place. It shows the bar in a
`PreviewCard`. While the bar has room, its group ends in an empty slot, Meta's `BarAddButton`: a
dashed circle with a plus, so it reads as room for another button rather than as an action. Its
screen reader label is **Add action**. Tapping a button, or the empty slot, opens the picker for that slot (`ActionPickerSheet`), titled
**Main button** or **Button 1** to **Button 3**.

- A search field narrows the actions by name. The actions sit in groups by what they act on:
  **Messages**, **One message**, **Message list**, **Writing**, and **Anywhere**. The picker shows
  the headings only when more than one group is left.
- A check marks the slot's current action. An action already in another slot says so, such as
  "Swaps with Button 2", and picking it trades the two places. An empty slot lists only actions the
  bar lacks.
- Picking an action applies at once and keeps the picker open. **Remove from bar** empties a
  filled secondary slot and closes the picker; later buttons move up. The main button cannot be
  removed, only replaced.
- Where messages can move, the **Messages** group ends with **Move to folder…**. It opens a
  `ChoiceSheet` of the user's folders across all accounts, and the picked folder becomes a Move to
  button in the slot. Each slot can hold a Move to button for a different folder. An empty slot
  lists only folders without a button; a filled slot lists all of them, and a folder in another
  slot says it will swap.
- While the slot holds a Move to button, that row is **Move to** with the folder in Meta's
  `ValuePicker`, with the folder symbol and the folder's path as the value. Screen readers hear,
  for example, "Travel, change". Tapping it opens the same folder sheet, so the folder changes
  without removing the button. When no account has the folder any more, the row says "Folder not
  found. Pick another."
- Each change is saved at once.

The editor's copy button opens **Copy to** (`ActionBarCopySheet`), which lists the other places to
tick. Each row says what that place would get: only the actions it can offer, in the same order.
When it cannot offer the main button, it keeps its own. A place that would get none of the buttons
is named in a note instead. Done replaces the ticked places' bars; closing any other way changes
nothing.

### Model and storage

`ActionBarPlace` names the places: `Inbox`, `Selection`, `Message`, `Compose`, and `Everywhere`,
the bar **Same everywhere** uses. A slot holds a `BarButton`: a plain `MailAction`, or a
`BarButton.MoveTo` with a folder path. Two Move to buttons whose paths differ only in case are
the same button. `ActionBarLayout` holds up to three distinct buttons in slot order and one main
button that is none of them. `buttons` lists the main button, then the others. A `BarSlot` names
a place in the bar, `Primary` or `Secondary(index)`. `actionAt(slot)` and `slotOf(button)` look
up either way, and `with(slot, button)` puts a button in a slot, trading places with a button
already elsewhere in the bar. `withSlot` and `without` also return changed copies.
`MailBehavior.barLayout(place)` is the bar a screen shows in the current mode,
`savedLayout(place)` is the one settings edit, and `copiedBar(from, to)` is the bar **Copy to**
gives a place.

`MailBehavior.actionBar` holds a layout per place, and `MailBehaviorStore` saves each under its
own key, `action_bar_<place>` in `SharedPreferences` on Android and `otus.mail.action_bar_<place>`
in `NSUserDefaults` on iOS, where the place is `inbox`, `selection`, `message`, `compose`, or
`everywhere`. The value lists the buttons, a `|`, and the main action, using each action's storage
value:

```text
archive,trash,move|reply
archive,move_to:Receipts,move_to:Clients/Acme|move_to:Travel
```

A Move to button is `move_to:` and its path, joined with `/`. A `%`, `/`, `,`, or `|` in a folder
name is saved as `%` and its hex code, such as `%2F`. Bars saved before Move to buttons read the
same as before.

A missing or malformed value, an unknown action, a repeated action, or an action the place cannot
offer reads as the place's default. The message list and the compose page cannot offer Move to,
and **Copy to** leaves it out for them. Turning Read later off removes it from every bar. If it was
the main button, the place's default main action takes over.

The mode is saved as `adaptive` or `fixed` under `action_bar_mode` on Android and
`otus.mail.action_bar_mode` on iOS. A missing or unknown value reads as `adaptive`. **Minimize**
is saved as a boolean under `action_bar_minimized` on Android and `otus.mail.action_bar_minimized`
on iOS, and is off when missing.

One action model in `core/messages/presentation/actions` serves every place: `ActionSubject.offer`
is one exhaustive `when` over `MailAction` that gives each action's label, symbol, and run for what
the place holds (the swiped row, the selection, the open conversation and page, the list, or the
draft), or null where it does not apply. `ActionLooks.kt` holds the labels and symbols settings
show, `barFor` builds every bar from the saved layout and the offers, and `AlertButtons.kt` gives
the alert buttons. The swipes, the four bars, the Read view's menu and Move row, and the alert
buttons all draw from it, so a new `MailAction` fails to compile until each of them handles it.

`MailAction` replaced `MessageSwipe`. The swipe storage values are unchanged, and a saved
"none" still reads as no swipe, so existing swipe settings carry over.

### Defaults

| Place | Buttons | Main button |
| --- | --- | --- |
| Message list | Search, Filters, Mark all read | New mail |
| Selected messages | Read, Flag, Trash | Move |
| Open message | Archive, Trash, Move | Reply |
| Writing | Attach, Discard | Send |
| Every screen | Search, Archive, Trash | New mail |

Flag left the open message's bar for Reply; it is in **More actions**.

### Folder bars

Spam, Trash, and Sent pages show their own bars. The page's folders are found by role
(`MailboxRole`: `Junk`, `Trash`, or `Sent`), so a combined folder, one account's folder, and a
folder pill all count. The open message uses the role of the folder it is shown in.

| Folder | Message list | Selected messages | Open message |
| --- | --- | --- | --- |
| Spam | Search, Filters, Empty spam; main New mail | Delete forever; main Not spam | Delete forever; main Not spam |
| Trash | Search, Filters, Empty trash; main New mail | Delete forever; main Restore | Delete forever; main Restore |
| Sent | Unchanged | Unchanged | Forward; main Follow up |

These are defaults. `MailBehavior.barLayout(place, role)` shows a folder's bar only while the
place's saved bar is still the place's default and the mode is **Per screen**. A bar the user
changed, and the **Same everywhere** bar, look the same in every folder. Settings has no
per-folder bars: the slot picker lists the folder actions in their groups like any other, and
outside their folders they stay in their slots, disabled. This keeps one saved bar per place.
Drafts has no bar of its own, because reopening a draft is not built yet.

- **Not spam** moves a message from Spam to its account's inbox. Where the Spam folder allows
  marks (`maySetKeywords`), the same `Email/set` patch sets `$notjunk` and clears `$junk`.
- **Restore** moves a message from Trash to its account's inbox. The server keeps no record of
  where a message was before it was trashed, so it cannot go back there.
- **Delete forever** deletes the messages from the Spam or Trash folder they are shown in, after a
  confirm dialog. A message only in that folder is destroyed; one also in another folder only
  leaves this one, like an IMAP expunge. It needs `mayRemoveItems` on the folder. `mayDelete` is
  the right to delete the folder itself, so it does not apply.
- **Empty spam** and **Empty trash** delete every message in the list's Spam or Trash folders,
  after a confirm dialog that gives the folders' message count. They need `mayRemoveItems`.
- **Follow up** opens a reply to a message the account sent, to its original To and Cc, from the
  identity that sent it (`ComposeRequest.FollowUp`). Sending stays disabled.

Each change takes the messages off the list at once. Messages the server keeps come back, and the
list shows a notice. Deleting runs `Email/set` calls of at most `maxObjectsInSet` objects, capped
at 256. Emptying first pages through the folder with `Email/query` and `Email/get` (ids and
`mailboxIds` only), then deletes, so the pages do not shift while it reads them.

## Deferred

- The reply, reply all, forward, share, and today symbols the spec lists. Meta does not bundle
  them yet, so Reply, Reply all, Forward, and Follow up show the send symbol. Not spam and
  Restore show the inbox symbol, and Delete forever and emptying the delete symbol, until Meta
  bundles `report_off`, `restore_from_trash`, and `delete_forever`.
- **Block sender** in Spam. The rule editor only files mail into folders, so blocking would need a
  new rule action; **Rule for sender** remains the way to sort a sender's mail.
- Undo for deleting and emptying.
- Calendar's use of the bar, such as a `BarToken.Date` context.
- Hiding the bar while the page scrolls.
- A split main button.
- "Move to" in the main Move to button's accessibility label. Meta's `BarAction` has one label,
  which the shell both shows and reads, so the main button reads only the folder's name. It needs
  a separate description in Meta.

## Open decisions

- The bar's sizes, other than the height and the collapsed main button, were measured from the
  spec's image and are not verified Figma values; see
  [mail design foundations](https://github.com/otus-foss/otus-meta/blob/main/docs/suite/mail-design.md#action-bar).

## Validation

`ActionBarTest` renders the bar's states and
checks that the main button's icon stays put through the transition, that a disabled action keeps
every slot in place, that the shell is one button naming its context, and that large text grows
the shell but not the group. `ActionBarLayoutTest` and `MailBehaviorTest` cover the layout model,
each place's default, its storage format, mode resolution, and Read later's removal.
`BarButtonTest` and `ActionBarLayoutTest` cover Move to's storage, the
migration of older bars, and switching folders. `ActionReachTest` checks that every action of the open message is on its bar or in its menu, and
every selection action on the bar or in its menus, in some folder. `MoveToOffersTest` covers moving across accounts,
the disabled button, and Suggested move, and `MoveTargetPathTest` the folder paths move targets
carry. `MoveToButtonsTest` picks Move to buttons with different folders for two slots, switches
one slot's folder through its `ValuePicker`, and moves the open message with one. `FolderBarsTest`
covers each folder's default bars and when they apply, `EmailCleanupTest` the Not spam
patch, batching, and emptying, `FolderCleaningTest` the rights checks and the rollback of kept
messages, and `FolderActionsTest` Not spam in Spam and the Delete forever confirm in Trash.
`EmailMarksTest` covers batched marks per account, `MessageMarkingTest` the rollback of
refused marks and the single change of folder counts, and `MarkOffersTest` that Mark all read and a
selection mark their messages in one change.
`InboxActionBarTest` covers the message list's bar and the **Same everywhere** mode across
screens, `ComposeActionsTest` the draft requests and the reply context, and
`ActionBarSettingsTest` the settings page, the slot picker, swapping, removing, and switching modes. Run
`./gradlew checkAll` for results; this guide does not record a passing run.
