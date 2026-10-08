# Architecture

Both Otus repositories use the same conventions. Their package roots are `com.otus.mail` and
`com.otus.calendar`.

```text
androidApp/                         Android lifecycle and entry point
  src/main/kotlin/<package>/
iosApp/                             Swift host and Xcode project
shared/src/
  commonMain/kotlin/<package>/
    app/                            App.kt, wiring, navigation
    core/                           Protocol/storage/platform contracts and infrastructure
    designsystem/theme/             Mail-only tokens; shared ones come from com.otus:designsystem
    feature/<feature>/
      domain/                       Models, contracts, business behavior
      data/                         Repository implementations, local/remote adapters
      presentation/
        components/                 Feature-specific UI pieces
        screens/                    Complete *Screen.kt views
                                    ViewModels and UI state live in presentation
  commonMain/composeResources/      Strings and assets as needed
  androidMain/kotlin/<package>/     Android implementations
  iosMain/kotlin/<package>/         iOS implementations and framework entry point
  commonTest/kotlin/<package>/      Tests mirroring production packages
```

Only create directories when they contain real functionality. The Inbox screen composes reusable controls and feature message rows. App wiring supplies the
message list; portable inbox matching belongs in the inbox domain. The welcome playground is
retained for component tests.

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
across Git repositories. Accounts, contacts, and settings are expected feature areas; Mail and
Calendar add their own domain features as implemented.
Mail's `feature/folders` holds folders and the Sieve rules that file into them; see
[Folders and rules](folders.md).

The architecture checker enforces paths, package alignment, UI locations, import/reference
boundaries, and selected forbidden APIs. It is a source-level guard, not a Kotlin type resolver:
transitive behavior, aliases hidden behind third-party APIs, and semantic design quality still
require review. Promote important boundaries to Gradle modules as the system grows.

The Android host currently uses the system placeholder icon. Production launcher artwork is a
separate design task. Application backup is disabled until account-data backup requirements are
defined.

## Avatar service

The portable account-scoped avatar resolver comes from the `com.otus:avatar` library. App wiring
supplies sources; presentation resolves photos before passing bitmap or initial values to
design-system elements. Mail resolves senders progressively after inbox loading. See
[avatars](avatars.md) for Mail's wiring.

## Shared libraries

The design system (`com.otus.designsystem`), date formatting (`com.otus.dateformat`), avatars
(`com.otus.avatar`), and the JMAP client (`com.otus.jmap`) come from otus-meta; see
[shared libraries](shared-libraries.md). Mail keeps feature-specific tokens such as `InboxTokens`
in its own `designsystem/theme`. A new reusable element or component belongs in otus-meta's design
system, not here.
