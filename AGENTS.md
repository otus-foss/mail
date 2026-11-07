# Otus engineering rules

## Hosting

GitHub hosts development, pull requests, issues, CI, and released packages. Use `origin` for
GitHub. See [hosting and CI](docs/hosting.md).

## Required reading and commands

Read `docs/architecture.md` and `docs/quality.md` before changing code. Mail builds otus-meta from
source, so keep a checkout at `../otus-meta` or set `otus.meta.path`; see `docs/shared-libraries.md`.
Install the Python tools as described in the quality guide, then run `./gradlew checkAll` before
handing work back. Use `./gradlew formatAll` for formatting. On macOS also run the pinned Swift
tools described in the quality guide. Never report an unrun check as passing.

## Platform priority

Android is the primary platform. Prioritize Android UX, device testing, performance, and release
readiness. Preserve portable Kotlin boundaries and the iOS target; iOS remains a secondary platform.

## Mandatory UI hierarchy

All UI follows **elements → components → screens**.

- Meta's `designsystem/<shared|mail>/<group>/elements`: small branded controls, fields, and avatars.
- Meta's `designsystem/<shared|mail>/<group>/components`: value-driven visual compositions.
- Shared elements, components, and tokens come from otus-meta's `com.otus.designsystem`. Add new
  elements, reusable components, visual tokens, and symbol resources there, even if only Mail
  currently uses them. Mail keeps feature adapters and screens. See decision 0008 in Meta.
- `feature/<name>/presentation/components`: feature-specific compositions.
- `feature/<name>/presentation/screens`: complete views, named `*Screen.kt`.
- `app`: root composition, dependency wiring, and navigation only.

Elements cannot depend on components or features. Components cannot depend on screens. Design-system
code cannot depend on app or feature code. Elements and components take values and callbacks; they
must not own ViewModels, navigation controllers, repositories, or network/database clients. Screen
content consumes UI state and emits actions; screen entry points may connect a ViewModel and
navigation. Keep business behavior out of composables.

## Feature and dependency boundaries

Use `feature/<name>/{domain,data,presentation}`, with only the layers the feature needs. Domain
owns portable models/contracts and meaningful use cases. Data implements domain contracts.
Presentation depends on domain, never data implementations. `app` constructs services and routes
pages; it holds no feature behavior. Features do not import other features directly: what two
features need moves to `core`, and a feature that shows another's screen takes a slot `app` fills.
A shared slice of `core` may use the same `domain`/`data`/`presentation` layers, with the same
rules.

`core` cannot depend on features or app. As in a feature, a core slice's `domain` and `data` cannot
use the design system; its `presentation` draws with it. Domain cannot depend on Compose, Android,
UIKit, networking clients, databases, presentation, or data implementations. `commonMain` cannot
import Android/JVM/Apple-only APIs. Platform implementations belong in matching platform source
sets. No catch-all `utils`, `helpers`, or `manager` packages.

## Theme

The user picks the theme and accent on the Theme page; the device's light or dark setting does not
change it, and there are no dynamic device colors. Use colors only through `OtusColors` and
`OtusTheme`, which follow the picked theme, and read them when drawing; never copy a token into a
stored value. Default text is `text1` at full opacity. `text2`/`text3` require an explicit semantic
choice. Check new UI in the Light and Black themes too. See Meta's
[decision 0014](https://github.com/reazndev/otus-meta/blob/main/docs/decisions/0014-user-themes.md).

## Motion

Animate only through Meta's `Motion`. Use `motionTween(speed, curve)` in composables,
`Motion.tween(speed, curve, scale)` outside composition, or `Motion.millis(speed, scale)` for a
spec that is not a tween, and respect `LocalMotionScale` everywhere. Never add a raw duration
constant, a `tween` with a literal, a new `CubicBezierEasing`, or an animation with a Compose
default spec. Pick the speed by what moves: `Quick` for touch feedback, `Fast` for small in-place
changes, `Normal` for panels, sheets, and text, `Relaxed` for large surfaces such as the sidebar or
a settling row. Pick the curve by direction: `Standard` by default, `Enter` for things arriving,
`Exit` for things leaving. Change `LocalMotionScale` to speed everything up or down; do not edit
the speeds. See [motion](docs/motion.md).

## Opinionated conventions

Use the committed formatters, never personal formatting preferences. No wildcard imports. Use
PascalCase for composables and types, camelCase for other functions and properties. Prefer `val`,
immutable UI state, explicit constructor dependencies, and small focused files. Avoid unnecessary
wrapper types, factories, generic base ViewModels, and speculative abstractions. Name files after
their primary responsibility. Package declarations must match the source path.

Write fluent Kotlin when it reads better than the alternative. Express transformations as
collection or sequence chains, such as `filter`, `map`, `associateBy`, and `firstNotNullOfOrNull`,
or as builder lambdas such as `buildList`, `buildMap`, and `buildJsonObject`, instead of mutable
accumulators and index loops. Make composable configuration a chainable extension that returns its
receiver, like `fun Modifier.monthBackgrounds(...): Modifier`, so call sites read as one `Modifier`
chain. Change immutable values with `copy`. Keep a loop or a local `var` when early exits, retries,
or cycle tracking would make a chain harder to follow. Do not add Java-style setters that return
`this`, chain side effects through `let` or `also`, or change a public API only for style.

Keep a call chain as one expression, with one call per line, instead of splitting it into temporary
`val`s that are each read once. Use a builder or chainable API wherever the library offers one and
it fits, such as `SharedPreferences.Editor`, Ktor request builders, and `buildJsonObject`. Name an
intermediate value only when the code reads it more than once or the name says something the chain
cannot.

Call the three stops of a page's control panel regular, expanded, and full, in code, docs, and
chat. Regular is its usual height (stop 0). Expanded shows the quick view (Meta's
`GROWN_EXPANDED_STOP`). Full shows every filter in the inbox and every detail in the Read view
(`GROWN_LAST_STOP`). Use the constants instead of stop numbers.

Keep colors and all visual tokens in Meta's `designsystem/theme` and refer to named tokens.
Feature UI belongs with its feature; reusable rendering belongs in Meta. Create folders with real code rather than placeholder files. Keep
handwritten Kotlin files at most 300 lines and Swift files at most 200 lines, including tests,
comments, and blank lines. Aim for 100–200 lines for typical UI files. Split by responsibility
first. The only permitted file-size exception is a first-line
`// otus-file-length: 420 -- <concrete reason>` directive; see `docs/quality.md` for validation
rules. It changes only the size limit, never other checks.

Do not add blanket suppressions, formatter-off markers, lint baselines, skipped checks, or
failure-tolerant CI to make a change pass. Fix violations. A rule change needs an explicit rationale
in Meta's `docs/decisions/` and a corresponding update of Meta's checks and tests; do not silently
weaken policy. Tool versions must be pinned, in Meta's version catalog.

## Readable code

Write for the person who reads the code next. A file passing ktlint does not make it readable:
ktlint adds braces and line breaks, but it never takes them out again once the code gets shorter.
After formatting, read the diff and fix shapes like these.

- Give every `when` entry one line. ktlint_official braces every entry once one entry needs
  several lines, and it leaves the braces after that entry shrinks. Remove leftover braces, or move
  a long entry's body into a named function so all entries fit on one line.

  ```kotlin
  // Avoid
  when {
      created < 0 -> {
          null
      }

      kind == MISS_RECORD && bytes.isEmpty() -> {
          StoredAvatar(null, created)
      }
  }

  // Prefer
  when {
      created < 0 -> null
      kind == MISS_RECORD && bytes.isEmpty() -> StoredAvatar(null, created)
  }
  ```

- Keep nesting to two or three levels. Return early with `?: return` instead of wrapping a body in
  `if (x != null) { ... }` or `?.let { ... }`, and give a lambda nested several levels deep its
  own named function or composable, such as `decodedRecord(...)` or `StarButton(star)`.
- Join a call that fits in 120 columns back onto one line. When it does not fit, name a part of it
  first, such as `val fitted = ...` or `val header = ...`, rather than splitting
  `foo(\n    x,\n)?.let { ... }` over several lines.
- Write `Modifier.then(if (x) Modifier else Modifier.a().b())` as a named private extension, such
  as `Modifier.updateButton(...)` or `Modifier.progressRing(...)`.
- Name arguments that are bare literals: `ChipFrame(selected = null, enabled = true)`, not
  `ChipFrame(null, true)`.
- Never hide a side effect in `(...).also { if (it) save() }` or `x?.let { destroy(it) }`. Write a
  plain `if`.
- Rewrap KDoc so each paragraph reads as prose. Do not leave a sentence broken after a long link.
- Name values by what they hold. Do not shadow a parameter or a standard function such as `run`.

Do not churn code to meet these rules. Fix the shapes in code you touch.

## Commit messages

Use this format for authored commits and squash commit messages:

```text
type(scope): short action

- Explain a concrete change.
- Explain another change or why it was needed.
```

- Always include a type and scope. Use lowercase for both; use kebab-case for multiword scopes.
- Choose the type by the purpose of the change: `feat` for new behavior, `fix` for a bug,
  `refactor` for restructuring without behavior changes, `perf` for performance work,
  `docs` for documentation, `test` for tests, `build` for dependencies or build tooling,
  `ci` for automation, `chore` for other maintenance, and `revert` for undoing a commit.
- Name the affected area in the scope, such as `inbox`, `accounts`, `avatars`, `design-system`,
  `jmap`, `calendar`, `android`, `ios`, or `build`. Scopes are open-ended. Choose the most
  specific area that describes the whole commit; split unrelated changes into separate commits.
- Start the subject with a lowercase imperative verb, such as `add`, `fix`, `remove`, or
  `simplify`. Preserve the spelling of code identifiers. Aim for at most 72 characters in the
  entire title, and omit a final period. Avoid vague subjects such as `update stuff`.
- Leave one blank line after the title. Require 1–8 body bullets, each starting with `- `.
  Explain what changed; include the reason when it helps. Use only as many bullets as needed.
  Do not pad the body, repeat the title, or turn it into a file inventory.
- Describe only changes in the commit. Mention checks only if they actually ran, and report
  their result accurately. Do not claim planned behavior or unverified performance gains.
- Apply the `unslop` skill to the entire message when available. Always use plain, concrete
  wording without filler, decorative emojis, or AI stock phrases. Keep the required syntax.
- For a breaking change, put `!` after the scope and explain the impact and required migration
  in the bullets. Optional issue references or `BREAKING CHANGE:` footers follow the bullets,
  separated by a blank line; they do not count toward the bullet limit.

Example:

```text
feat(inbox): add avatars

- Show contact photos beside inbox senders.
- Fall back to initials when a photo is unavailable.
```

Enable the versioned Git hooks once per checkout, including new clones. `pre-commit` checks
formatting, and `commit-msg` checks the message with Meta's checker. No hook runs
`checkAll`, and CI checks only pull requests, so run `./gradlew checkAll` yourself before a push
that needs it, such as a direct push to `main`.

```sh
git config --local core.hooksPath .githooks
```

Run the command from this repository's root. The `commit-msg` hook needs Python 3.11+ and rejects invalid
title syntax, missing separators, and bodies outside the 1–8 bullet limit. Review wording,
imperative verbs, breaking-change explanations, and the
72-character target manually. Git-generated merge messages and temporary autosquash messages are
exempt; the final squash message must follow the rules. Meta's CI tests the checker; nothing
inspects commit history. See [the decision](https://github.com/reazndev/otus-meta/blob/main/docs/decisions/0004-commit-messages.md).

## Pull requests

Write every pull request from `.github/pull_request_template.md` and keep its headings in the same
order. GitHub fills it in on the website, but `gh pr create --body` skips it, so copy the headings
when you write the body yourself. The title follows the commit title rules above. Leave out
"Generated with" footers and session links. `gh` cannot upload images, so link recorded
screenshots at the head commit, and send phone captures to the user to drag in.

When the pull request finishes an issue, end the body with `Closes #123`, or
`Closes reazndev/otus-meta#45` for an issue in another repository, so GitHub closes it on merge.
Write `Part of #123` for a step that leaves the issue open. Search the open issues before you open
the pull request. The `issue link` workflow warns about a pull request that closes no issue.

CI runs once, when the pull request is marked ready. To check later pushes too, add the
`ci on every push` label; each push then runs CI again.

The user lands pull requests with the `ready to land` label, which also merges the partners from the
same branch name in the other repositories. Add that label only when the user asks you to land a pull
request. See [landing](https://github.com/reazndev/otus-meta/blob/main/docs/landing.md).

## Agent workflow

During development, prefer `./gradlew spotlessCheck detekt ruffCheck ruffFormatCheck` and run
only tests relevant to the change, for example
`./gradlew :shared:testAndroidHostTest --tests 'com.otus.mail.SomeTest'` with the actual test
class name. Use `qualityCheck` when all quality checks are needed. Batch edits and run `checkAll`
once at the end, rather than after each edit. Run it again only if later changes or failures
require it.

Inspect existing patterns first. Make focused changes, run the relevant checks, and report any
environment blockers precisely. Add behavior tests for new logic and regression tests for fixes; do
not add tests that merely repeat constants or implementation details. Update architecture docs when
boundaries change.

Each app is a separate Git repository. Never assume the unversioned parent docs are available.
Code shared with Calendar lives in otus-meta, which Mail builds from source; CI checks it out beside
Mail. Change shared code there on a branch with the same name as Mail's, and merge Meta's pull
request first; see `docs/shared-libraries.md`. Suite docs, decisions, and review prompts live only
in otus-meta. Check JMAP specifications and advertised server capabilities before implementing
protocol behavior.
