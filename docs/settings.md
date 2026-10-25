# Settings

Implemented through the shared `settings` and `accounts` libraries from otus-meta, with
Mail-specific destination entries in `app/MailSettings.kt`. Mail pins both libraries to Meta
0.7.0. The Appearance page's title and the removal of its `content` slot are unreleased Meta
changes, so Mail builds against Meta's `feat/settings-layout` until the next release. Settings is
a sidebar destination with subpages.

Meta 0.7.0 includes the connection sheet and sending-address list described below.

## Pages

Each page has one subject, and no page is more than two levels below Settings except a folder's
own page.

- **Settings** lists its subpages as `SettingPageRow`s, each with an icon, a summary, and a
  chevron, separated by hairlines: **Email accounts**, **Notifications**, **Appearance**, **Mail
  behavior**, **Privacy**, **Folders & rules**, and **Signatures**. Settings pages follow Notion's layout: title and
  description on the left, the control on the right, and large section titles.
- **Email accounts** (`com.otus.accounts`) lists connected accounts as flat rows with a Remove
  button, under large section titles, and connects new ones with a JMAP
  server, username, and password or app password. It supports compatible JMAP mail servers with
  HTTP Basic authentication, such as Stalwart; no provider-specific behavior exists.
- **Notifications** chooses Google Play services or a UnifiedPush app for instant delivery and shows each account's push
  status; see [notifications](notifications.md).
- **Appearance** (`AppearanceSettingsScreen` from `com.otus.settings`) holds only visual choices,
  in one **Look** section, and every row has an icon. Rows that open a page are Meta's `SettingPageRow`s: they end in a
  chevron and name the page's value under the title. **Theme** names the theme and opens the Theme
  page. Mail adds `MailLookSettings` through the screen's `look` slot:
  **Sidebar** names how many sections the sidebar shows, such as "2 sections", and opens the
  Sidebar page, which arranges the sidebar's sections, mailboxes, and counts (see
  [Mail destinations](sidebar.md#mail-destinations)). Its **Folder colors** switch, on by default,
  hides folder icon colors without deleting them, with the caption "Colors only change the folder
  icon. They stay on this device." **Date format** shows the pattern in a `ValueButton`, with an
  example date below the title. Tapping the row opens a `ChoiceSheet` with every format and its
  example; choosing one applies it at once and moves the check, and Done, the scrim, Back, or
  swiping down anywhere on the sheet closes it. A short, slow pull springs back. **Animations**
  chooses Instant, Slow, Regular, or Fast.
- **Mail behavior** (`MailBehaviorScreen`) holds how Mail acts. Under **Inbox**, **Inbox shows**
  names what the Inbox lists in a `ValueButton`: **Inbox**, **Inbox + Spam**, **Inbox + Trash**, or
  **All mail**, and opens a sheet with
  **Spam** and **Trash** switches; see
  [Inbox contents](inbox.md#inbox-contents). **Pills** shows how many pills the inbox row shows and
  opens `InboxPillsSheet`, a `SelectionSheet` where the shown pills are reordered by their drag
  handles and others are added; Done saves the row. The **Read later** switch, off by default,
  gives each account a Read later folder and adds its pill, swipe, and button; see
  [Read later](folders.md#read-later). Under **Actions**, **Swipe right** and **Swipe left** open
  a `ChoiceSheet` of message actions, shown in a `ValueButton`, and **Action bar**, also a
  `SettingPageRow`, opens its page; it names no mode, since the page shows it first. Under **Reading**, **Mark as read
  when opened** is a switch. Each change is saved before it is shown. See
  [inbox pills](inbox.md#inbox-pills) and [swipe actions](inbox.md#swipe-actions).
- **Privacy** (`PrivacyScreen`) decides what mail and avatars load from other servers. Under
  **Load remote images**, three rows choose **Always**, **From people you know** (the default:
  contacts, people the user wrote to, and allowed senders), or **Never**; the chosen one ends in a
  check. A note says that images inside the mail itself always show and that mail in Spam never
  loads remote images. Mail in a folder the app has not loaded yet waits as if it were in Spam. **Allowed senders** names how many there are and opens their page
  (`AllowedSendersScreen`), which lists each address with a **Remove** button, or says how to add
  one. The Read view adds a sender with "Always for" on its image banner. Under **Avatars**, the
  **Website icons** switch lets avatars load logos from sender websites and DuckDuckGo, and photos
  that contact cards link to; see [website icons](avatars.md#website-icons). The choice and the
  list are saved in `MailBehavior.remoteImages` and `allowedSenders` before they are shown. The
  setting moved to a new key when this default returned; from the keys before it, only Never
  carries over, since those held their default for anyone who saved any setting.
- **Theme** (`ThemeSettingsScreen` from `com.otus.settings`), a subpage of Appearance, previews
  the inbox in the current colors with `ThemeInboxPreview`, then offers the
  **Otus**, **Light**, **Graphite** (warm grey, after Notion), and **Black** (true black, after
  the X app) themes as cards and seven accent swatches. The plus swatch opens a **Custom color**
  sheet with hue and intensity sliders, a hex field, and the shades in each theme; Done applies
  it. Otus derives each accent's fill, outline, and text shades for the theme and shows them with
  their hex codes. **Reset to Otus** restores the default. Every choice applies to the whole app
  at once and is saved by `AndroidThemePreferenceStore` or `IosThemePreferenceStore`.
- **Action bar** (`ActionBarSettingsScreen`), a subpage of Mail behavior, starts with a
  **Layout** row whose `ChoiceSheet` picks **Per screen** or **Same everywhere**, then the
  **Minimize** switch, which shrinks every bar to a round button for its main action. **Per
  screen** shows a card for **Message list**, **Selected messages**, **Open message**, and
  **Writing**; **Same everywhere** shows one **Every screen** card. Each card previews the saved
  bar and opens its editor. Each change is saved at once. See [action bar](action-bar.md).
- **Folders & rules** lists every account's folders, or one account's
  through the account picker, as
  trees. Dragging a folder's handle moves it to a new place or into another folder, holding a folder
  opens quick actions (Rename, Move into, Color, Delete), and a plus button
  creates a folder: on each account's row, or beside the hint when one account is shown. Tapping a folder opens its page: a summary line, then **Name**, **Move into**, **Color**
  (kept on this device), **Rules** (the chips that sort new mail into it, and **Add rule**), and
  **Delete folder**, which waits until the folder is empty. Rules live only on folder pages;
  there is no separate Rules page, because Otus writes only rules that file mail into a folder, and
  each folder row on this page shows its rules. See [Folders and rules](folders.md#the-folder-page).

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
