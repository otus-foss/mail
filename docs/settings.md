# Settings

Implemented in common Compose code for Mail. Settings is a sidebar destination with subpages.

## Pages

- **Settings** lists two subpages as `ActionRow`s, each with an icon, a summary, and a chevron.
- **Email accounts** (`feature/accounts`) lists connected accounts and connects new ones with a JMAP
  server, username, and password or app password. It supports compatible JMAP mail servers with
  HTTP Basic authentication, such as Stalwart; no provider-specific behavior exists.
- **Appearance & behavior** shows the current date format with an example date. Tapping it opens a
  `ChoiceDialog` with every format; choosing one applies it and closes, dismissing changes nothing.
  Dark mode is the only color mode, so there is no theme choice.

## Reusable pieces

- `PageScaffold` (component): context-panel header with a leading chevron and a scrolling body.
- `Subpage` (component): a `PageScaffold` whose chevron and the system back gesture call `onBack`.
- `PageSection` (component): a captioned group of rows.
- `ChoiceDialog` (component): single-choice popup over a scrim.
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
Connected accounts are listed with a remove action. The password field is cleared after connecting.
Local storage failures keep the page open with an error and a reload action. A failed removal can
be retried using the same account row. Inbox refresh preserves existing messages if account
metadata cannot be read.

## Checks

`JmapServerAddressTest` covers address resolution and that drafts never print passwords.
`SettingsPagesTest` covers the popup, dismissal, subpage back navigation, server hints, and password
semantics. `AccountConnectionTest` covers connecting, listing, and the filled inbox. Settings pages were checked on a Pixel 9a; iOS runtime
validation requires macOS/Xcode.
