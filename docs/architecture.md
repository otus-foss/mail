# Architecture

Both Otus repositories use the same conventions. Their package roots are `com.otus.mail` and
`com.otus.calendar`.

```text
androidApp/                         Android lifecycle and entry point
  src/main/kotlin/<package>/
iosApp/                             Swift host and Xcode project
shared/src/
  commonMain/kotlin/<package>/
    app/                            Construction and routing only
    core/                           What several features share
      <slice>/                      Flat contracts, or domain/data/presentation like a feature
    feature/<feature>/
      domain/                       Models, contracts, business behavior
      data/                         Repository implementations, local/remote adapters
      presentation/
        components/                 Feature-specific UI pieces
        screens/                    Complete *Screen.kt views
                                    State holders and UI state live in presentation
  commonMain/composeResources/      Strings and assets as needed
  androidMain/kotlin/<package>/     Android implementations
  iosMain/kotlin/<package>/         iOS implementations and framework entry point
  commonTest/kotlin/<package>/      Tests mirroring production packages
  commonTest/kotlin/<package>/testing/  Fakes and fixtures that tests of several slices share
```

A feature has only the layers it needs. Only create directories when they contain real
functionality.

## Mail's packages

`app/` builds the services and routes pages: `App`, `MailPage` and `MailPageSaver`, `MailPages`,
`MailNavigation`, page requests, `MailServices` and `MailDeviceStores` (construction), the
state bundles `MailStates` holds for the pages (`FolderStates`, `ListStates`, `ContactStates`,
`ComposeStates`, `ReadWiring`), the settings routing, and one input builder per page kind, such
as `ListPage` for the Inbox and every folder page. It holds no feature behavior and does not draw.

`core/` holds what more than one feature needs:

- `core/mail`: RFC 8621 wire objects, keywords, address keys, and `MailFailure`.
- `core/mailbox`: folders, roles, the folder tree, shared folders, and the one `Mailboxes` source.
- `core/messages`: the shared message model in `domain` (messages, scopes, drafts, the action
  model, `EmailWriter`), the writer and change queue in `data`, and in `presentation` the edits
  every list and Read view observe, the action offers and bars, toasts, and the Move sheet.
- `core/settings`: the settings models, their store, and the labels settings and lists share.
- `core/cache`, `core/files`, `core/text`: the mail cache, picked, saved, and downloaded files,
  and size text.
- `core/storage`: `StoreSpec` and `DeviceStore`, the typed app-private stores each feature
  declares.

Features:

- `inbox`: the message list, search, filters, the quick view, and list actions.
- `read`: the Read view, bodies and HTML, headers, unsubscribe, newsletters, CID images, and
  attachment downloads. The inbox shows it through a `ReadView` the app supplies.
- `compose`: drafts, sending, the outbox, signatures, and inline images.
- `mailsettings`: the Mail settings screens and the settings search index.
- `sidebar`: sidebar mailboxes, counts, and the folder tree; destinations are `SidebarPin`s, which
  app maps to pages.
- `folders`: folders, rules, and filing: move targets, cleanup, sorting by rules, Rule for sender,
  and rule-based sender blocks.
- `contacts`, `notifications` (alert data, push status, Apple push), and `updates`.

Features never import each other. When two features need something, it moves to `core`; when a
feature shows another's screen, as the inbox shows the Read view, it takes a slot that `app`
fills. The checker applies the layer rules inside a core slice too.

Dependency direction: app wiring may assemble every layer; presentation depends on domain; data
implements domain contracts. Domain depends only on portable contracts and libraries. Core and
design system cannot reach into features. UI hierarchy is elements → components → screens: arrows
mean composition, so screens may use components and elements directly.

Design-system elements/components and feature components are driven by values and callbacks.
ViewModels and navigation are connected at screen entry points. Networking, persistence, and
synchronization do not belong inside UI components.

The provider-independent JMAP client and login storage come from the `com.otus:jmap` library in
otus-meta. See [JMAP](jmap.md) and [shared libraries](shared-libraries.md). Code shared with
Calendar moves to otus-meta when both apps need it; other shared package names do not share code
across Git repositories. Shared account setup and settings screens come from otus-meta.
Mail supplies their state, persistence, service requirement, and destinations in
app wiring. Mail-specific inbox, folder, and rule screens stay here. Calendar is unchanged.
Mail's `feature/folders` holds folders and the Sieve rules that file into them; see
[Folders and rules](folders.md).

The architecture checker enforces paths, package alignment, UI locations, import/reference
boundaries, and selected forbidden APIs. It is a source-level guard, not a Kotlin type resolver:
transitive behavior, aliases hidden behind third-party APIs, and semantic design quality still
require review. Promote important boundaries to Gradle modules as the system grows.

The Android host currently uses the system placeholder icon. Production launcher artwork is a
separate design task. Application backup is disabled until account-data backup requirements are
defined.

## Device storage

Mail keeps each login's mailboxes, emails, threads, bodies, and sync states in an SQLite database
behind `core/cache/MailCache`, and downloaded attachments as files behind `core/files/DownloadFolder`,
which `FileDownloadFolder` keeps in a folder each platform names. See [storage](storage.md) for what they hold, where the files
live, and how long they keep them. Each other app-private store is a `StoreSpec` in
`core/storage`, declared once beside the feature that owns it: its name on the device and the text
form of its keys and values. A `DeviceStore` reads and writes its values typed.
`MailDeviceStores.named` opens every spec, and removing an account drops what each store keeps for
it.

## Network destinations

Every host the app may contact, when it does, what it sends, and which setting turns it off is
listed in [network destinations](network.md). Update it with any change that adds a network
client, a host, or a new trigger for an existing one.

## Avatar service

The portable account-scoped avatar resolver comes from the `com.otus:avatar` library. App wiring
supplies sources; presentation resolves photos before passing bitmap or initial values to
design-system elements. Mail resolves senders progressively after inbox loading. See
[avatars](avatars.md) for Mail's wiring.

## Shared libraries

The design system (`com.otus.designsystem`), date formatting (`com.otus.dateformat`), avatars
(`com.otus.avatar`), JMAP client (`com.otus.jmap`), settings (`com.otus.settings`), and accounts
(`com.otus.accounts`) come from otus-meta; see
[shared libraries](shared-libraries.md). All visual tokens, including `InboxTokens`, and symbol resources live in Meta.
A new element or reusable component belongs in Meta even when Mail is its only current consumer.
Mail feature adapters supply domain-derived labels, values, and callbacks to shared renderers.
The checker rejects local production design-system code and drawable resources.

Meta 0.10.0 groups rendering beneath `com.otus.designsystem.shared.<group>` and
`com.otus.designsystem.mail.<feature>`, each with `elements` and `components` where needed.
For example, contact rows come from `shared.contacts.components` and message HTML from
`mail.read.elements`. Theme declarations use `theme.colors`, `theme.motion`, `theme.brand`,
`theme.typography`, and `theme.tokens.<family>`. `theme.OtusTheme` and `resources.Res` retain
their packages. These groups contain UI; the feature layers above keep their responsibilities.
See [Meta's migration guide](https://github.com/reazndev/otus-meta/blob/main/docs/ui-package-migration.md).

## Apple notification delivery

`feature/notifications/data/ApplePushRuntime` connects the Swift lifecycle to `ApplePushSession`,
which reaches Mail's services through `PushServices` and uses the shared JMAP subscription library and Mail's bridge client. Swift owns APNs registration, the shared
push Keychain group, and CryptoKit. The notification extension links only `PushSupport`, not the
Compose framework, and receives no mail credentials. The iOS app and background refresh use the
same `MailServices` instance.

`push_bridge/` is the deployable APNs provider for Mail. It accepts encrypted Web Push, stores
short-lived ciphertext in SQLite and sends APNs alerts. It is a separate process with Apple
signing credentials; neither the app nor shared libraries hold those credentials. See
[Apple push](apple-push.md) for the protocol, setup, limits and validation.
