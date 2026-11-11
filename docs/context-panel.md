# Context panel

Implemented in common Compose code. `ContextPanel` lives in the shared design system's
`components` (`com.otusfoss.core.designsystem`, from otus-meta); `Chip`, `IconButton`, `SearchField`, and
`Symbol` are elements.
The production inbox and test-only welcome playground own their state and supply values and callbacks. No account, search,
networking, storage, or navigation services are implemented by these controls.

## Composition

`ContextPanel` runs edge to edge, draws behind the status bar, and pads its content below it. Its
one 56 dp header row holds the navigation slot, a `PanelNavigation` chevron whose glyph sits at
the start of its 48 dp target, the main content, and an `actions` slot at the end. Pages pad
themselves with `belowControlPanel()` and their bodies with `pageSideInset()`.

Wrap each pill in `item(stableKey) { Chip(...) }` so its position drives a cutout. Keys must be
unique within a panel. Use `ChipPlacement.Overlapping` for pills across the bottom edge,
`ChipPlacement.Below` for pills under it, or `ChipPlacement.None` when there are no pills; `None`
moves the trailing action into the header row. Use `transition(expanded) { mode -> ... }` inside
the chip builder when replacing a two-mode pill group. The rail scrolls horizontally with an 8 dp
inset that scrolls with it, so pills reach the screen edge when scrolled. Cutouts are pill shapes
subtracted from the surface with a 4 dp ring.

Pass `collapse` a `PanelCollapse.progress` from `rememberPanelCollapse(list)` and attach its
`connection` to the list. The panel then follows the scroll: it shortens by 30 dp over
60 dp, and the pills rise, shrink, and fade out by 0.6. `PanelSearchPill` and `AccountStack`
take the same reading. `PanelSheet` unfolds the panel for a task, such as search, filters, or the
account switcher. `PageScaffold` takes a `search`, whose button grows into a field over the title.

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
  240 ms, `MotionSpeed.Normal`, as the pills become overlapping or detached. Header content crossfades and resizes.
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

Chips and icon buttons share a subtle 120 ms (`MotionSpeed.Quick`) highlight at 10% opacity using `text1`.
Feedback is clipped to the visible rounded surface, including when pressing its larger 48 dp
accessible touch target. The outer target has no rectangular ripple. Keyboard focus uses the same
highlight; release or cancellation fades it out. Appearance and timing live in `ControlFeedbackTokens`.
The search prompt has no highlight, so tapping it leaves the field's background unchanged.
