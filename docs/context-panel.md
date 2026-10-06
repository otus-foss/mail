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
row; the trailing action remains outside the scrolling viewport. Detached and overlapping pill rows
span the panel's full width, aligned with its outer edges. The scrolling viewport has no fixed
8 dp side inset, so pills and their cutouts reach the panel edge while scrolling. The trailing
action stays fixed at the opposite edge. The surface subtracts rounded pill shapes from its path,
clipped horizontally to the viewport. The holes reveal the
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
  they do not send rectangle or size snapshots back through composition.
- Cutouts are opaque geometry that always sits on its pill; they are never blended by opacity.
  Opacity-weighted erasing showed half-visible contours of both pill groups during a swap, which
  read as the mask jumping. A cutout switches on or off only in a drawn frame where the surface does
  not cover it, so the change is invisible: entering pills get their full cutout before the edge
  descends onto them, and leaving pills keep theirs until the edge has lifted off. When a pill enters
  or leaves while the surface already covers it, its cutout grows or shrinks with the pill's fade and
  reaches its target exactly when the fade does. Future pill effects should move or scale the pill;
  the cutout follows because it is derived from the pill's drawn bounds.
- The chip rail's scroll container spans the viewport, so a resizing pill group cannot clip pills at
  its animated width. Edge fades follow how far visible pills actually extend past each viewport edge,
  so they grow and shrink continuously instead of switching on and off.
- Outgoing and incoming groups have distinct item keys, so interrupted transitions cannot remove
  another group's cutouts. The filter button remains outside the moving rail. Transitions can
  reverse, and system animation duration scaling applies.

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
that changing modes cannot carve a raised notch above the rail, and draw every frame of pill-group
swaps, as a device does. They check that an entering cutout opens as soon as the edge reaches its
pill, that no cutout pops into a covering surface or lingers as a translucent notch, and that pills
are not clipped while their group resizes. Pixel 9a recordings of the current implementation cover
inbox ↔ search expansion and contraction at normal speed; interrupted and reduced-speed runs were
not repeated on the device for this version.
iOS builds and runtime testing require macOS/Xcode.

## Press feedback

Chips and icon buttons share a subtle 100 ms highlight at 10% opacity using `text1`.
Feedback is clipped to the visible rounded surface, including when pressing its larger 48 dp
accessible touch target. The outer target has no rectangular ripple. Keyboard focus uses the same
highlight; release or cancellation fades it out. Appearance and timing live in `ControlFeedbackTokens`.
