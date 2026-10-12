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
- **Filters** opens the filter sheet, as the header's filter button does.
- **Mark all read** marks every unread message in the current results read, where the account
  allows it. It is disabled when there is nothing to mark.

Actions that need a message, such as Archive in **Same everywhere** mode, are disabled here.

### Open message

Tapping a row opens the message as a page over the list (`MessageScreen`). The header is a
`ContextPanel` without pills: a back chevron, the subject, and a **More actions** button. The
chevron and system Back close the page. The body shows the sender, the plain-text body, and the
attachment names, and scrolls clear of the bar. While the page is open, accessibility services
skip the list under it.

The bar shows the **Open message** layout. **More actions** opens an `ActionDialog` with every
action the message can take that the bar leaves out, Reply, Reply all, and Forward included; the
button is hidden when there are none. An action that takes the message out of the list, such as
Archive, closes the page first. Marks keep the page open and show their new state on the button.
**Move** asks for a folder, and choosing one closes the page and moves the message.

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
| Messages | Read, Flag, Read later, Archive, Trash, Spam, Move | Open message, selected messages |
| One message | Rule for sender, Reply, Reply all, Forward | Open message |
| List | Search, Filters, Mark all read | Message list |
| Draft | Send, Attach, Discard | Writing |
| Anywhere | New mail | Message list, open message |

The **Every screen** bar can hold any of them. For messages, `offer` turns an action into a button
for one message or a selection, with a label for their current state, or returns nothing when
none of them can take it.

- **Read** and **Flag** turn the mark on for every message while any of them lacks it, and off
  when all have it. The label says which, such as **Mark read** or **Mark unread**.
- **Read later** saves the messages not saved yet. Once all are saved, it offers to remove them.
  It is a choice only while Read later is on; see [Read later](folders.md#read-later).
- **Archive**, **Trash**, and **Spam** move each message that can take the action to its
  account's folder with the `archive`, `trash`, or `junk` role. Spam is new and works like Trash:
  the message leaves every folder it is in, Read later included. It moves the message and sets no
  keyword. Messages already in the folder, or whose account has none, are skipped.
- **Move** needs messages from one account, because folders belong to an account. It offers the
  folders every selected message can move to.
- **Rule for sender** needs exactly one message, so the selection bar never offers it.

Actions follow the same folder rights as before; see
[folder pages and message actions](inbox.md#folder-pages-and-message-actions). An action the
current messages cannot take stays in its slot, disabled with a `text3` icon, so the buttons never
move. A swipe can be any action except Spam, Move, and Rule for sender.

### Settings

**Settings › Appearance & behavior › Action bar** (`ActionBarSettingsScreen`) starts with a
**Layout** row that shows the mode and opens a `ChoiceSheet` with **Per screen** and **Same
everywhere**. **Per screen** shows a section for **Message list**, **Selected messages**, **Open
message**, and **Writing**. **Same everywhere** shows one **Every screen** section instead. The
**Action bar** row on the appearance page shows the current mode too.

Each section shows a preview of the saved bar, then a **Main button** row and a row per button
slot. **Writing** has two slots, because it has only three actions. Tapping a row, or a button in
the preview, opens a `ChoiceSheet` for that slot. The place's default is listed first.

- Choosing an action that another slot or the main button holds swaps the two.
- A button slot can be **None**. Emptying a slot moves the later buttons up.
- An empty slot does not offer the main button's action, because the main button cannot be empty
  and so has nothing to swap in.
- Each change is saved at once.

### Model and storage

`ActionBarPlace` names the places: `Inbox`, `Selection`, `Message`, `Compose`, and `Everywhere`,
the bar **Same everywhere** uses. `ActionBarLayout` holds up to three distinct buttons in slot
order and one main action that is none of them. `withPrimary`, `withSlot`, and `without` return
changed copies. `MailBehavior.barLayout(place)` is the bar a screen shows in the current mode, and
`savedLayout(place)` is the one settings edit.

`MailBehavior.actionBar` holds a layout per place, and `MailBehaviorStore` saves each under its
own key, `action_bar_<place>` in `SharedPreferences` on Android and `otus.mail.action_bar_<place>`
in `NSUserDefaults` on iOS, where the place is `inbox`, `selection`, `message`, `compose`, or
`everywhere`. The value lists the buttons, a `|`, and the main action, using each action's storage
value:

```text
archive,trash,move|reply
```

A missing or malformed value, an unknown action, a repeated action, or an action the place cannot
offer reads as the place's default. Turning Read later off removes it from every bar. If it was
the main button, the place's default main action takes over.

The mode is saved as `adaptive` or `fixed` under `action_bar_mode` on Android and
`otus.mail.action_bar_mode` on iOS. A missing or unknown value reads as `adaptive`.

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

## Deferred

- The reply, reply all, forward, share, and today symbols the spec lists. Meta does not bundle
  them yet, so Reply, Reply all, and Forward show the send symbol.
- Calendar's use of the bar, such as a `BarToken.Date` context.
- Hiding the bar while the page scrolls.
- A split main button.

## Open decisions

- The bar's sizes, other than the height and the collapsed main button, were measured from the
  spec's image and are not verified Figma values; see
  [mail design foundations](https://github.com/reazndev/otus-meta/blob/main/docs/suite/mail-design.md#action-bar).

## Validation

The bar needs the Meta release that adds `ActionBar`, `ContentRow` selection, and
`symbol_more_horiz`; Mail still pins Meta 0.7.0. `ActionBarTest` renders the bar's states and
checks that the main button's icon stays put through the transition, that a disabled action keeps
every slot in place, that the shell is one button naming its context, and that large text grows
the shell but not the group. `ActionBarLayoutTest` and `MailBehaviorTest` cover the layout model,
each place's default, its storage format, mode resolution, and Read later's removal.
`InboxActionBarTest` covers the message list's bar and the **Same everywhere** mode across
screens, `ComposeActionsTest` the draft requests and the reply context, and
`ActionBarSettingsTest` the settings page and switching modes. Run
`./gradlew checkAll` for results; this guide does not record a passing run.
