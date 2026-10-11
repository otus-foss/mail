# Sidebar

Implemented in common Compose code for Mail. `Sidebar` lives in the shared design system's
`components` (`com.otus.designsystem`, from otus-meta);
`SidebarEntry` is an element. Neither knows about Mail: the caller supplies the title, entries,
footer, and a `SidebarState`. App wiring owns the current page and the `SidebarState`.
`SidebarSwipeArea` wraps the page and handles swipe-to-open; `ContextPanel` marks the bottom of
that zone, so pages need no extra wiring.

## Behavior

- Tapping the context panel's leading chevron opens it. In search mode that chevron still means
  "Back to inbox".
- On a top-level page, a swipe toward the trailing side that starts on the context panel or above
  it also opens the sidebar, following the finger. Swipes on controls that scroll sideways, such as
  the chip rail, stay with those controls. Subpages do not swipe it open, because their chevron
  means "Back". On Android gesture navigation, a swipe starting at the very screen edge is the
  system back gesture.
- It opens from the leading edge to 64% of the screen width, kept between 264 and 384 dp. It is
  flush with the top, leading, and bottom screen edges, with square corners and a `strokeBgDark`
  stroke on the trailing edge only; its content is padded clear of the system bars and cutouts. The page stays visible beside it
  under a scrim. RTL mirrors the slide.
- The top shows the suite name, **Otus**, on the context panel's header height, so it lines up with
  the page title. Entries follow; the footer, used for Settings, stays at the bottom. Each entry
  is an icon followed by its name; the current page is highlighted with `accentDark`.
- Choosing an entry, tapping the scrim, the system back gesture, or swiping it back toward the
  leading edge closes it. A released swipe settles on the nearer side unless it was flung faster
  than 400 dp/s. Its surface is `bgDark`.
- Motion reuses the suite curves: open with `MotionTokens.enter` over 300 ms, close with
  `MotionTokens.exit` over 220 ms; a sidebar released part-way animates only the remaining share
  of that time. One progress value drives slide and scrim, so swipes and reversals stay continuous. Values live in `SidebarTokens`.

## Mail destinations

`app/MailPage` lists every page; pages without a parent are sidebar destinations. The unified Inbox
comes first, then each account's folders under the account name, then Settings at the bottom.
Folders are indented by nesting and show their unread count; system folders have their own symbol.
With one account, its inbox is the unified Inbox and is not listed again; its subfolders move up a
level. Its [Read later](folders.md#read-later) folder, when that switch is on, follows the unified
Inbox above the account name, with its own subfolders nested under it. With several accounts, each
lists its Read later folder right after its inbox. Settings subpages stay under the Settings entry;
see [Settings](settings.md) and
[Folders and rules](folders.md). Each page, including each folder, keeps its saved state, such as a
search, while another page is shown.

`SidebarEntry` takes an optional nesting `depth` and `badge`; `SidebarHeading` captions a group of
entries. Both stay free of Mail knowledge.

## Calendar

The component is app-independent, but it lives in the Mail repository. Calendar does not yet have
`Symbol`, the icon set, Figtree, or `PanelTokens`, and cross-repository library distribution is
undecided. Calendar adoption therefore needs either that decision or a deliberate copy.

## Checks

`SidebarNavigationTest` covers opening from the chevron, the half-open width, entry order,
navigating to Settings and back with preserved inbox state, scrim dismissal, back dismissal,
swiping open on and above the context panel (but not on the message list), and swiping closed.
Opening, closing, and Settings navigation were checked on a Pixel 9a before the edge-to-edge and
swipe changes; those changes have not been checked on a device yet. iOS runtime validation
requires macOS/Xcode.
