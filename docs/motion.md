# Motion

Motion lives in common Kotlin code. Every animation in Mail takes its duration and curve from
Meta's `Motion` API: `MotionSpeed`, `MotionCurve`, and the global speed multiplier
`LocalMotionScale`. Meta's
[design system guide](https://github.com/reazndev/otus-meta/blob/main/docs/designsystem.md#motion)
explains the curves, the speeds, how durations are computed, and how to call it. The rules for
choosing a speed and a curve are in [AGENTS.md](../AGENTS.md#motion). Calendar still has its own
motion code and adopts `Motion` when it moves to Meta's design system.

| Animation | Speed | Curve |
| --- | --- | --- |
| Press feedback, refresh indicator | `Quick`, 120 ms | Standard |
| Toggle switch, reorder shift, action bar collapse | `Fast`, 160 ms | Standard |
| Outgoing text | `Fast`, 160 ms | Exit |
| Context panel modes, inbox search mode, row selection, swipe flash, list item changes | `Normal`, 240 ms | Standard |
| Sheet and incoming text | `Normal`, 240 ms | Enter |
| Read view page turn, swiped or picked in the thread list | `Normal`, 240 ms | Enter |
| Sheet and sidebar closing | `Normal`, 240 ms | Exit |
| Sidebar opening | `Relaxed`, 300 ms | Enter |
| Swiped row settling | `Relaxed`, 300 ms | Standard |

`tools/check_architecture.py` rejects `tween(`, `spring(`, and `CubicBezierEasing(` in Mail's
production Kotlin, and `animateScrollToPage`, `animateScrollBy`, and `animate*AsState` calls that
leave their spec on the Compose default. `Motion.tween` and `motionTween` are allowed.

The [sidebar](sidebar.md) uses one progress value for its slide and scrim, so reversing mid-way
stays continuous.

The shared `elements/AnimatedText` takes text, numeric order, a modifier, and typography.
Increasing order slides upward; decreasing order slides downward. Incoming text slides from one
text height away and fades in over 240 ms. Outgoing text slides away and fades out over 160 ms.
The element clips motion to its bounds and exposes only the latest label to accessibility.
Unchanged text stays still, even when its order changes. Rapid updates retarget the animation.
`LocalMotionScale` and Android's animator duration scale both apply, including zero-duration
animations.

Mail's date-filter calendar uses separate elements for month and year in the joined navigation
capsule. Both receive chronological month order, so December/January transitions move in the
correct direction. The year animates only when its displayed value changes. Scrolling, arrow
navigation, and month/year selection all use the same visible-month state. Calendar has the
reusable element available for future screens; its welcome screen is unchanged.

Mail's `AnimatedTextTest` checks both directions, interruption, latest-label accessibility, and
unchanged text. Existing date-navigation and calendar-scroll tests cover the integrated header.
Physical-device motion review and iOS runtime validation remain separate from local Android tests.

## Launch

Each launch opens on Meta's `MailLaunch`: the envelope pops up, the owl rises behind it, and tilts
upright. The inbox starts loading underneath at once, and the animation ends as soon as the inbox
shows mail, usually the device's copy before the server answers, or when the first load ends for an
empty inbox. It does not wait for the owl to arrive, and a tap ends it early. Then the owl shrinks
and fades out. Rotating the phone does not replay it.

The animation follows the theme: the owl is in the accent color, or in the text color when App
icon is set to Monochrome, on the theme's `bgDark`. The window takes that color before Compose
draws. On Android 12 and later the system splash shows only the Otus background, so the animation
brings the owl in itself instead of replacing a static icon.

## App icon

Appearance has an App icon row: Accent or Monochrome. The manifest declares one
launcher alias per icon, Monochrome plus one per preset accent, all opening `MainActivity`.
`showLauncherIcon` enables the one that matches the setting and the accent, using the closest
preset for a custom accent. It runs when Mail goes to the background, because some launchers
close an app whose launcher entry changes.
