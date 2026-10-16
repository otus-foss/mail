# Settings

Implemented through the shared `settings` and `accounts` libraries from otus-meta, with
Mail-specific destination entries in `app/MailSettings.kt`. Mail pins both libraries to Meta
0.7.0; the appearance page's content slot arrived in 0.5.0. Settings is a sidebar destination
with subpages.

Meta 0.7.0 includes the connection sheet and sending-address list described below.

## Pages

- **Settings** lists four subpages as flat `SettingRow`s, each with an icon, a summary, and a
  chevron, separated by hairlines. Settings pages follow Notion's layout: title and description on
  the left, the control on the right, and large section titles.
- **Email accounts** (`com.otus.accounts`) lists connected accounts as flat rows with a Remove
  button, under large section titles, and connects new ones with a JMAP
  server, username, and password or app password. It supports compatible JMAP mail servers with
  HTTP Basic authentication, such as Stalwart; no provider-specific behavior exists.
- **Appearance & behavior** shows the date format's pattern in a `ValueButton`, with an example
  date below the title. Tapping the row opens a `ChoiceSheet` with every format and its example;
  choosing one applies it at once and moves the check, and Done, the scrim, Back, or swiping
  down anywhere on the sheet closes it. A short, slow pull springs back.
  Every row has an icon. The **Look** section starts with **Theme**, which shows the theme's name
  and accent and opens the Theme page. Mail adds `MailLookSettings` through the screen's `look`
  slot: **Sidebar** shows **Edit** and opens the Sidebar page, which arranges the sidebar's
  sections, mailboxes, and counts (see [Mail destinations](sidebar.md#mail-destinations)). Its
  **Folder colors** switch, on by default, hides folder icon colors without deleting them, with the
  caption "Colors only change the folder icon. They stay on this device." **Pills** shows how
  many pills the inbox row shows and opens `InboxPillsSheet`, a
  `SelectionSheet` where the shown pills are reordered by their drag handles and others are added.
  Done saves the row. The date format closes the section. Below it, Mail adds
  `MailBehaviorSettings` through the content slot. Under **Inbox**, **Inbox shows** sums up what
  the Inbox lists, such as "All mail · Spam", and opens the Inbox page; see
  [Inbox contents](inbox.md#inbox-contents). Under **Actions**, **Swipe right** and **Swipe
  left** open a `ChoiceSheet` of message actions, shown in a `ValueButton`. **Action bar** shows its
  mode, **Per screen** or **Same everywhere**, and opens its own page, and **Minimize action bar**
  is a switch. Under **Reading**, **Mark as read when opened** is a
  `SettingToggleRow` switch. Under **Privacy**, the **Website icons** switch lets avatars load
  logos from sender websites and DuckDuckGo, and photos that contact cards link to; see
  [website icons](avatars.md#website-icons). Each change is saved before it is shown. See
  [inbox pills](inbox.md#inbox-pills), [swipe actions](inbox.md#swipe-actions), and
  [Read later](folders.md#read-later).
- **Theme** (`ThemeSettingsScreen` from `com.otus.settings`), a subpage of Appearance &
  behavior, previews the inbox in the current colors with `ThemeInboxPreview`, then offers the
  **Otus**, **Light**, **Graphite** (warm grey, after Notion), and **Black** (true black, after
  the X app) themes as cards and seven accent swatches. The plus swatch opens a **Custom color**
  sheet with hue and intensity sliders, a hex field, and the shades in each theme; Done applies
  it. Otus derives each accent's fill, outline, and text shades for the theme and shows them with
  their hex codes. **Reset to Otus** restores the default. Every choice applies to the whole app
  at once and is saved by `AndroidThemePreferenceStore` or `IosThemePreferenceStore`.
- **Action bar** (`ActionBarSettingsScreen`), a subpage of Appearance & behavior, starts with a
  **Layout** row whose `ChoiceSheet` picks **Per screen** or **Same everywhere**. **Per screen**
  shows a section for **Message list**, **Selected messages**, **Open message**, and **Writing**;
  **Same everywhere** shows one **Every screen** section. Each section shows a preview of the
  saved bar, then **Main button** and a row per button slot, up to **Button 3**. A row, or a button
  in the preview, opens a `ChoiceSheet` for that slot with the place's default first. Choosing an
  action that another slot holds swaps the two, and a button slot can be **None**. Each change is
  saved at once. See [action bar](action-bar.md).
- **Folders** starts with the **Read later** switch, one list for mail saved from every account
  (see [Read later](folders.md#read-later)). It lists every account's folders, or one account's
  through the account picker, as
  trees. Dragging a folder's handle moves it to a new place or into another folder, holding a folder
  opens quick actions (Rename, Move into, Color, Delete), and a plus button
  creates a folder: on each account's row, or beside the hint when one account is shown. Tapping a folder opens its page: a summary line, then **Name**, **Move into**, **Color**
  (kept on this device), **Rules** (the chips that sort new mail into it, and **Add rule**), and
  **Delete folder**, which waits until the folder is empty. Rules live only on folder pages;
  there is no separate Rules page. See [Folders and rules](folders.md#the-folder-page).

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
