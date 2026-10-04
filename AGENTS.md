# Otus engineering rules

## Required reading and commands

Read `docs/architecture.md` and `docs/quality.md` before changing code. Install the Python tools as described
in the quality guide, then run `./gradlew checkAll` before handing work back. Use
`./gradlew formatAll` for formatting. On macOS also run the
pinned Swift tools described in the quality guide. Never report an unrun check as passing.

## Platform priority

Android is the primary platform. Prioritize Android UX, device testing, performance, and release
readiness. Preserve portable Kotlin boundaries and the iOS target; iOS remains a secondary platform.

## Mandatory UI hierarchy

All UI follows **elements → components → screens**.

- `designsystem/elements`: small branded primitives, such as buttons, fields, and avatars.
- `designsystem/components`: reusable compositions without feature knowledge.
- `feature/<name>/presentation/components`: feature-specific compositions.
- `feature/<name>/presentation/screens`: complete views, named `*Screen.kt`.
- `app`: root composition, dependency wiring, and navigation only.

Elements cannot depend on components or features. Components cannot depend on screens. Design-system
code cannot depend on app or feature code. Elements and components take values and callbacks; they
must not own ViewModels, navigation controllers, repositories, or network/database clients. Screen
content consumes UI state and emits actions; screen entry points may connect a ViewModel and
navigation. Keep business behavior out of composables.

## Feature and dependency boundaries

Use `feature/<name>/{domain,data,presentation}`. Domain owns portable models/contracts and
meaningful use cases. Data implements domain contracts. Presentation depends on domain, never data
implementations. App wiring connects the implementations. Features do not import other features
directly: put a genuinely shared contract in `core` or compose features through app wiring.

`core` cannot depend on features, design system, or app. Domain cannot depend on Compose, Android,
UIKit, networking clients, databases, presentation, or data implementations. `commonMain` cannot
import Android/JVM/Apple-only APIs. Platform implementations belong in matching platform source
sets. No catch-all `utils`, `helpers`, or `manager` packages.

## Theme

Dark mode is the only supported color mode, regardless of the device setting. Use the supplied Otus
palette through `OtusColors` and `OtusTheme`; default text is `text1` (`#EBECED`) at full opacity.
`text2`/`text3` require an explicit semantic choice. Do not add a light-mode branch, dynamic device
colors, or a theme switch.

## Opinionated conventions

Use the committed formatters, never personal formatting preferences. No wildcard imports. Use
PascalCase for composables and types, camelCase for other functions and properties. Prefer `val`,
immutable UI state, explicit constructor dependencies, and small focused files. Avoid unnecessary
wrapper types, factories, generic base ViewModels, and speculative abstractions. Name files after
their primary responsibility. Package declarations must match the source path.

Keep colors in `designsystem/theme` and refer to named tokens. Feature UI belongs with its feature
until there is concrete reuse. Create folders with real code rather than placeholder files. Keep
handwritten Kotlin files at most 300 lines and Swift files at most 200 lines, including tests,
comments, and blank lines. Aim for 100–200 lines for typical UI files. Split by responsibility
first. The only permitted file-size exception is a first-line
`// otus-file-length: 420 -- <concrete reason>` directive; see `docs/quality.md` for validation
rules. It changes only the size limit, never other checks.

Do not add blanket suppressions, formatter-off markers, lint baselines, skipped checks, or
failure-tolerant CI to make a change pass. Fix violations. A rule change needs an explicit rationale
in `docs/decisions/` and a corresponding check/test update; do not silently weaken policy. Tool
versions must be pinned.

## Agent workflow

Inspect existing patterns first. Make focused changes, run the relevant checks, and report any
environment blockers precisely. Add behavior tests for new logic and regression tests for fixes; do
not add tests that merely repeat constants or implementation details. Update architecture docs when
boundaries change.

Each app is a separate Git repository. Never assume a sibling checkout or parent docs are available
in CI. Shared-library distribution is undecided. Do not create a third repository or publish
libraries without a concrete task. Check JMAP specifications and advertised server capabilities
before implementing protocol behavior.
