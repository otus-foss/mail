# Settings

Implemented through the shared `settings` and `accounts` libraries from otus-meta, with
Mail-specific destination entries in `app/MailSettings.kt`. Mail pins both libraries to Meta
0.7.0. The Appearance page's title and the removal of its `content` slot are unreleased Meta
changes, so Mail builds against Meta's `feat/settings-layout` until the next release. Settings is
a sidebar destination with subpages.

Meta 0.7.0 includes the connection sheet and sending-address list described below.

## Pages

Each top-level page has one subject, named by what the user wants to change rather than by where
the code lives. Settings home lists **Email accounts**, **Notifications**, **Appearance**, **Mail
behavior**, **Privacy & security**, and **Folders & rules**, each a `SettingPageRow` with an icon,
a summary, and a chevron. Settings pages follow Notion's layout: title and description on the
left, the control on the right, and large section titles. Search covers every page; see
[Search](#search).

- **Email accounts** (`com.otus.accounts`) lists connected accounts as flat rows with a Remove
  button, under large section titles, and connects new ones with a JMAP server, username, and
  password or app password. It supports compatible JMAP mail servers with HTTP Basic
  authentication, such as Stalwart; no provider-specific behavior exists.
- **Notifications** chooses Google Play services or a UnifiedPush app for instant delivery. Under
  **Previews**, **All mail** shows what alerts show in a `ValueButton` and opens a `ChoiceSheet`,
  and **Accounts and addresses** sets it per account and address. Under **Buttons**, **Alert buttons**
  shows how many alert buttons are chosen and opens an `OrderedChoiceSheet` that picks up to three
  and their order (Android only, since iOS alerts show no message). **Not instant** lists only
  accounts whose push failed; see [notifications](notifications.md#buttons).
- **Appearance** (`AppearanceSettingsScreen` from `com.otus.settings`) holds only how Otus looks,
  in one **Look** section: **Theme** (its page), **App icon**, **Mail list** (its page),
  **Date format**, and **Animations**. Nothing on it changes what Mail does.
  - **Theme** previews the inbox in the current colors with `ThemeInboxPreview`, then offers the
    **Otus**, **Light**, **Graphite** (warm grey, after Notion), and **Black** (true black, after
    the X app) themes as cards and seven accent swatches. The plus swatch opens a **Custom color**
    sheet with hue and intensity sliders, a hex field, and the shades in each theme; Done applies
    it. **Reset to Otus** restores the default. Every choice applies to the whole app at once and
    is saved by `AndroidThemePreferenceStore` or `IosThemePreferenceStore`.
  - **Mail list** previews a row, then sets **Show sender**, **Order**, **Preview**, and **Folder
    icons** under **Rows**, and the date **Style** and **Show this year** under **Dates**. Order and
    Preview only apply while the sender shows, so they wait, disabled, below Show sender.
  - **Date format** shows the pattern in a `ValueButton`, with an example date below the title,
    and **Animations** chooses Instant, Slow, Regular, or Fast; both open a `ChoiceSheet`.
- **Mail behavior** (`MailBehaviorScreen`) holds how Mail acts, in the order the user meets it:
  - **Inbox**: **Inbox shows** opens a sheet with Archive, Your folders, Spam, and Trash switches
    (see [Inbox contents](inbox.md#inbox-contents)); **Pills** opens `InboxPillsSheet`, where the
    shown pills are reordered by their handles and others are added; the **Read later** switch
    gives each account a Read later folder (see [Read later](folders.md#read-later)).
  - **Panels**: **Sidebar** and **Filter panel** open their pages. The sidebar is a way around
    Mail, not a look, so it lives here rather than on Appearance.
  - **Actions**: **Swipe right** and **Swipe left** open a `ChoiceSheet` of message actions, and
    **Action bar** opens its page.
  - **Reading**: **Mark as read when opened**, **Show quoted text**, and the Read view's
    **Quick view**, which opens an ordered sheet.
  - **Writing**: **Undo send** opens a `ChoiceSheet`, and **Signatures** opens its page, which
    has **Add to replies** and one signature for each address the user sends from.
- **Sidebar** (`SidebarSettingsScreen`), under Mail behavior, arranges the sidebar for every
  account: drag a section's handle to move it, or switch it off to hide it. **Mailboxes and
  folders** opens the Pinned page; it waits, disabled, while the Pinned section is off, since
  nothing would show the pins. **Counts** and **Folder colors** follow. See
  [Mail destinations](sidebar.md#mail-destinations).
- **Pinned** (`PinnedSettingsScreen`) starts with **Show as**, Grid or Rows, and shows only the
  editor for that style. Grid edits like Control Center: hold a tile to move it, drag its corner
  to resize it, or tap its minus to unpin it. Rows have no sizes, so each pin is a row with a drag
  handle and a remove button. Below, each group of the other mailboxes and folders lists what can
  be pinned.
- **Filter panel** (`FilterPanelSettingsScreen`), under Mail behavior, picks the panel's
  **Sections** and their order, its **Quick filters**, and under **Pull down** the modules of its
  **Quick view**, each in an ordered sheet. Quick filters waits, disabled, until the panel shows
  its Quick filters section.
- **Action bar** (`ActionBarSettingsScreen`), under Mail behavior, starts with a **Layout** row
  whose `ChoiceSheet` picks **Per screen** or **Same everywhere**, then the **Minimize** switch.
  **Per screen** shows a card for **Message list**, **Selected messages**, **Open message**, and
  **Writing**; **Same everywhere** shows one **Every screen** card. Each card previews the saved
  bar and opens its editor. See [action bar](action-bar.md).
- **Privacy & security** (`PrivacyScreen`) decides what mail and avatars load from other servers
  and how the device protects Mail.
  - Under **Load remote images**, **Load images** opens a `ChoiceSheet`: **In your contacts**
    (the default: contacts, people the user wrote to, and allowed senders), **Always**, or
    **Never**. The section's info button says that images inside the mail itself always show and
    that mail in Spam never loads remote images. **Allowed senders** names how many there are and
    opens their page (`AllowedSendersScreen`); while Load images is not In your contacts, its
    summary says the list only counts with that choice. The page stays open so senders can still
    be removed.
  - Under **Photos you attach**, **Photo details** opens a `ChoiceSheet`: **Ask each time** (the
    default), **Always remove details**, or **Keep details**; see [attachments](compose.md).
  - Under **Avatars**, the **Website icons** switch, off by default, lets avatars load website
    icons and photos that contact cards link to. **Icons from** waits, disabled, while it is off;
    it opens one sheet with **Sender websites**, **DuckDuckGo**, and **Bulwark** as radio rows and
    Bulwark's address field under them. Done saves both, and stays off while the address is not a
    public HTTPS one, which is why this is the one settings choice saved on Done. See
    [website icons](avatars.md#website-icons).
  - On Android, **This device** adds **Screen security**, off by default, which sets
    `FLAG_SECURE`, and **Incognito keyboard**, on by default, which adds
    `IME_FLAG_NO_PERSONALIZED_LEARNING` to every text field through `IncognitoKeyboard`.
    `DevicePrivacy` names the protections a platform offers; iOS offers neither yet. The keyboard
    setting moved to a new key when this default returned; from the keys before it, only Never
    carries over.
- **Folders & rules** lists every account's folders, or one account's through the account picker,
  as trees. Dragging a folder's handle moves it, holding a folder opens quick actions (Rename,
  Move into, Color, Delete), and a plus button creates a folder. Tapping a folder opens its page:
  a summary line, then **Name**, **Move into**, **Color** (kept on this device), **Rules**, and
  **Delete folder**. See [Folders and rules](folders.md#the-folder-page).

## Kinds of setting

Every setting is one of a few kinds, so each kind reads and behaves the same on every page.

- **Switch**: `SettingToggleRow`. The whole row toggles, and the change saves at once.
- **Choice**: a `SettingRow` that shows the current value in a `ValueButton` and opens a
  `ChoiceSheet`. Choosing applies at once and moves the check; Done, the scrim, Back, or a swipe
  down closes it, and the default sits alone at the top. A choice is never a list of radio rows on
  the page itself. Only **Icons from** uses a `RadioChoiceSheet`, which saves on Done, because its
  Bulwark choice needs an address that has to be valid first.
- **Ordered list**: a `SettingRow` that shows how many are chosen, such as "4 shown", and opens
  `OrderedChoiceSheet`, a `SelectionSheet` where chosen items are dragged by their handles or
  tapped out and others are added; Done saves. Pills, the filter panel's Sections and Quick
  filters, both Quick views, and the alert buttons use it. A list with a limit, such as the three
  alert buttons, says "Take one out to add another." once it is full.
- **Page**: `SettingPageRow`, which ends in a chevron and names the page's value or contents
  under the title. A page holds a setting that needs room of its own, such as the theme cards,
  action bar previews, or the pinned tile editor.
- **Arranged in place**: the only settings edited on the page itself, because what they arrange
  is the page's content: the sidebar's sections (switches with drag handles) and the Pinned
  editors.

A setting that only applies while another one is set stays where it is inside Meta's
`DependentSettings`: dimmed, ignoring taps, with a description that says what it waits on, such
as "Turn on Show sender to change this". It is not hidden, so the page keeps its shape and search
still finds it. The same reason is passed to the index through `unavailable(reason) { ... }`, so
search results say it too. Settings that do this today: Order and Preview (wait on Show sender),
Quick filters (wait on the panel's Quick filters section), Mailboxes and folders (wait on the
Pinned section), and Icons from (waits on Website icons).

## Search

The settings home searches `mailSettingsIndex` in `app/MailSettings.kt`, which mirrors each page:
`mailBehaviorIndex`, `mailLookIndex`, and `privacyIndex` in the inbox feature list what their
screens show, in the same sections. A search finds a page, setting, option, or keyword, such as
"Dark mode" for Theme, "drawer" for Sidebar, or "screenshots" for Screen security, and a query of
several words finds a setting that holds each word somewhere, such as "swipe archive". Opening a
result opens its page with the row flashed, and Back returns to the results. When a setting moves
between pages, move its index entry with it; `MailSettingsIndexTest` checks where the moved ones
live.

## Reusable pieces

- `PageScaffold` (component): context-panel header with a leading chevron and a scrolling body.
- `Subpage` (component): a `PageScaffold` whose chevron and the system back gesture call `onBack`.
- `PageSection` (component): a captioned group of rows.
- `ChoiceSheet` (component): single-choice bottom sheet for settings; choosing applies at once.
  The default option, "Use device format" or a swipe's own default marked "Default", is listed
  first in a group of its own.
- `ChoiceDialog` (component): single-choice popup over a scrim, for one-shot picks.
- `DialogFrame` (component): the shared popup frame with a title and close button.
- `SheetFrame` (component): the shared bottom sheet with a title, Done, and dismissal gestures.
- `ConfirmDialog` (component): asks before a destructive change, with Cancel and the
  confirming action. Removing an account and deleting a folder use it.
- `ActionDialog` and `InputDialog` (components): a confirmation or menu of `DialogAction`s, and a
  popup with one validated text field.
- `ActionRow`, `InputField`, and `InputKind` (elements).

App wiring owns the page (`app/MailPage`): subpages name their parent and belong to the Settings
sidebar entry. Each page keeps its saved state while another is shown. The password is held in
memory only and is not written to saved instance state.

## JMAP server address

`parseJmapServer` resolves the typed server. A bare host uses the discovery path
`https://<host>/.well-known/jmap` (RFC 8620, section 2.2); a full `https://` URL with a path is used
as the session URL. Plain `http://` is refused because JMAP requires TLS. The resolved URL is shown
under the field.

Connect verifies the server and credentials, then saves the account; see [JMAP](jmap.md). If the
server hands sign-in to another host, the page names that host and continues only after the user
trusts it.
"Connect an account" opens the shared `SheetFrame` with the server, username, password, and
connection status. The form scrolls above the keyboard. Success closes the sheet. Dismissing it
discards the draft and cancels pending connection work, including credentials awaiting host consent.
Connected accounts show the sign-in address, server, and any distinct sending addresses the server
reports through `Identity/get`. Discovery requires submission support on the selected mail account;
receiving aliases without an identity cannot be listed. Address discovery errors keep the saved
account visible. The address list refreshes when accounts reload and is not persisted.
Each account has a Remove button, which asks in a `ConfirmDialog` before
the device forgets the account and its saved password; Cancel keeps it.
Local storage failures keep the page open with an error and a reload action. A failed removal can
be retried using the same account row. Inbox refresh preserves existing messages if account
metadata cannot be read.

## Checks

The account library's `JmapServerAddressTest` covers address resolution and that drafts never print passwords.
`SettingsPagesTest` covers the popup, dismissal, subpage back navigation, server hints, and password
semantics. `AccountConnectionTest` covers connecting, listing, and the filled inbox. Settings pages were checked on a Pixel 9a; iOS runtime
validation requires macOS/Xcode.
