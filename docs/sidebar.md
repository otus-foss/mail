# Sidebar

Implemented in common Compose code for Mail. `Sidebar` lives in `designsystem/components`;
`SidebarEntry` is an element. Neither knows about Mail: the caller supplies the title, entries,
footer, open state, and callbacks. App wiring owns the current page and the open state.

## Behavior

- Tapping the context panel's leading chevron opens it. In search mode that chevron still means
  "Back to inbox".
- It half-opens from the leading edge: half the screen width, kept between 220 and 320 dp. The page
  stays visible beside it under a scrim. RTL mirrors the slide.
- The top shows the suite name, **Otus**, on the context panel's header height, so it lines up with
  the page title. Entries follow; the footer, used for Settings, stays at the bottom. Each entry
  is an icon followed by its name; the current page is highlighted with `accentDark`.
- Choosing an entry, tapping the scrim, or the system back gesture closes it. Its surface matches
  the context panel: `bgDark`, `strokeBgDark` border, panel radius, and page inset.
- Motion reuses the suite curves: open with `MotionTokens.enter` over 300 ms, close with
  `MotionTokens.exit` over 220 ms. One progress value drives slide and scrim, so a reversal mid-way
  is continuous. Values live in `SidebarTokens`.

## Mail destinations

`app/MailDestination` lists sidebar entries that exist: Inbox and Settings. Settings subpages stay
under the Settings entry; see [Settings](settings.md). Each page keeps its saved state, such as an
inbox search, while another page is shown. JMAP mailboxes are expected to become entries once accounts are connected; that is not
implemented.

## Calendar

The component is app-independent, but it lives in the Mail repository. Calendar does not yet have
`Symbol`, the icon set, Figtree, or `PanelTokens`, and cross-repository library distribution is
undecided. Calendar adoption therefore needs either that decision or a deliberate copy.

## Checks

`SidebarNavigationTest` covers opening from the chevron, the half-open width, entry order,
navigating to Settings and back with preserved inbox state, scrim dismissal, and back dismissal.
Opening, closing, and Settings navigation were checked on a Pixel 9a. iOS runtime validation
requires macOS/Xcode.
