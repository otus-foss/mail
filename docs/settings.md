# Settings

Implemented through the shared `settings` and `accounts` libraries from otus-meta, with
Mail-specific destination entries in `app/MailSettings.kt`. Mail pins both libraries to Meta
0.5.0, the first release with the appearance page's content slot. Settings is a sidebar destination with subpages.

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
  Dark mode is the only color mode, so there is no theme choice. Below it, Mail adds
  `MailBehaviorSettings` through the screen's content slot: **Swipe right** and **Swipe left** open a
  `ChoiceSheet` of message actions, shown in a `ValueButton`. **Mark as read when opened** and
  **Read later** are `SettingToggleRow` switches. Each change is saved before it is shown. See
  [swipe actions](inbox.md#swipe-actions) and [Read later](folders.md#read-later).
- **Folders** shows each account as a section of flat folder rows, indented by nesting, ending with
  a **New folder** row. **Folders** and **Rules** manage each account's folders and the rules that
  file arriving mail;
  see [Folders and rules](folders.md).

## Reusable pieces

- `PageScaffold` (component): context-panel header with a leading chevron and a scrolling body.
- `Subpage` (component): a `PageScaffold` whose chevron and the system back gesture call `onBack`.
- `PageSection` (component): a captioned group of rows.
- `ChoiceSheet` (component): single-choice bottom sheet for settings; choosing applies at once.
  The default option, "Use device format" or a swipe's own default marked "Default", is listed
  first in a group of its own.
- `ChoiceDialog` (component): single-choice popup over a scrim, for one-shot picks.
- `DialogFrame` (component): the shared popup frame with a title and close button.
- `ConfirmDialog` (component): asks before a destructive change, with Cancel and the
  confirming action. Removing an account and deleting a folder or rule use it.
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
Connected accounts are listed with a Remove button, which asks in a `ConfirmDialog` before
the device forgets the account and its saved password; Cancel keeps it. The password field is cleared after connecting.
Local storage failures keep the page open with an error and a reload action. A failed removal can
be retried using the same account row. Inbox refresh preserves existing messages if account
metadata cannot be read.

## Checks

The account library's `JmapServerAddressTest` covers address resolution and that drafts never print passwords.
`SettingsPagesTest` covers the popup, dismissal, subpage back navigation, server hints, and password
semantics. `AccountConnectionTest` covers connecting, listing, and the filled inbox. Settings pages were checked on a Pixel 9a; iOS runtime
validation requires macOS/Xcode.
