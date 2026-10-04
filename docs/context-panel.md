# Context panel

Implemented in common Compose code for Mail. `ContextPanel` lives in
`designsystem/components`; `Chip`, `IconButton`, `SearchField`, and `Symbol` are elements.
The production inbox and test-only welcome playground own their state and supply values and callbacks. No account, search,
networking, storage, or navigation services are implemented by these controls.

## Composition

`ContextPanel` accepts optional navigation and trailing-action slots, main content, and a chip
builder. Wrap each pill in `item(stableKey) { Chip(...) }` so its position drives a cutout.
Keys must be unique within a panel. Use `ChipPlacement.Below` for detached pills or
`ChipPlacement.Overlapping` for pills embedded in the lower edge. Placement does not depend on
whether the content is a title, a search field, or metadata. Use `transition(expanded) { mode -> ... }`
inside the chip builder when replacing a two-mode pill group. Place each pill inside `item(...)`
within that group so its cutout follows the animated layout.

The main row has a 48 dp content slot and 8 dp top and bottom padding. Its 64 dp minimum height is
derived from those shared tokens. Plain titles, the search prompt, and search input use the same
centered content slot, so their text baselines and the leading icon stay fixed across modes and when
the clear button appears. Prompt text stays on one line; larger text can grow the shared header.
Chips use a horizontally scrolling
row; the trailing action remains outside the scrolling viewport. The surface erases rounded pill
shapes in an isolated drawing layer, clipped horizontally to the viewport. The holes reveal the
actual background. Scroll-edge
fades use an alpha mask rather than a painted background color. RTL reverses the fades and mirrors
the back chevron. Overscroll stretch is disabled on the chip rail: stretching that layer independently
would move the pills away from the panel cutouts during a held edge drag. Normal scrolling and
fling remain enabled.

## Intentional differences from the reference

Reference: https://www.figma.com/design/7HWGPYYexnCqvkEYdEKIgq/meta?node-id=19-322
Inbox node: `19:386`.

- Material Symbols Rounded replaces the supplied icons by explicit user decision. See
  [asset provenance and licenses](design-assets.md).
- Pills are now 36 dp tall with 20 dp leading icons and 10 dp horizontal padding.
  The detached row has no extra top gap; its touch padding leaves a 6 dp visual gap below the header.
  Pills have minimum 48 dp touch targets. The row is therefore taller than Figma's 29 px
  visual-only row; neighboring interactive targets do not overlap.
- Figtree is bundled for panel text; existing global typography remains unchanged.
- The Figma fade contains `#353637`, outside the approved palette. It is replaced with an alpha fade.
- The panel sizes to its content and does not preserve the inbox frame's unused 278 px height.
- Inbox/search mode changes keep one panel surface. Its lower edge expands and contracts over
  220 ms as the pills become overlapping or detached. Header content crossfades and resizes.
  Pill groups crossfade around the same vertical rail anchor, with animated group dimensions.
  Outgoing pills do not slide up into the header. Their moving cutouts previously created a second,
  higher contour that appeared to jump back down when the incoming group took over, even though both
  groups' coordinates moved smoothly. Keeping the vertical anchor removes that contour change.
  Cutouts clip only at the horizontal scroll edges, so moving rounded caps can extend above the rail
  without becoming flat rectangles. Header, rail, viewport, and pill coordinates are resolved together
  in panel-local space during drawing. Layout callbacks retain live coordinates and invalidate drawing;
  they do not send rectangle or size snapshots back through composition. Each pill group and its
  cutouts share one fade value, so an invisible exiting group leaves no opaque holes that snap away
  when it is disposed. Outgoing and incoming groups have distinct item keys, so interrupted transitions
  cannot remove another group's cutouts. The filter button
  remains outside the moving rail. Transitions can reverse, and system animation duration scaling applies.

## Playground and checks

The welcome playground is retained for component tests; Mail now launches the empty Inbox screen.
In the playground, the overlap toggle works independently of mode.
Search accepts text and submits a local demo status; filters and chip selections are local UI state.

Run `./gradlew checkAll :androidApp:testDebugUnitTest`.
Local Android UI tests use Robolectric native graphics and write screenshots to
`androidApp/build/reports/context-panel/`. They complement actual device checks, not replace them.
Header tests compare icon bounds and text baselines throughout mode transitions, typing, clearing,
and enlarged text, including plain titles supplied directly to the reusable panel.
Motion regression tests inspect every frame while the parent moves and the header resizes, verify
that changing modes cannot carve a raised notch above the rail, and check that disposing a faded pill
cannot abruptly change the mask. Pixel 9a recordings cover expansion,
contraction, and interrupted transitions at normal and reduced speeds.
iOS builds and runtime testing require macOS/Xcode.

## Press feedback

Chips and icon buttons share a subtle 100 ms highlight at 10% opacity using `text1`.
Feedback is clipped to the visible rounded surface, including when pressing its larger 48 dp
accessible touch target. The outer target has no rectangular ripple. Keyboard focus uses the same
highlight; release or cancellation fades it out. Appearance and timing live in `ControlFeedbackTokens`.
