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
| Sheet and sidebar closing | `Normal`, 240 ms | Exit |
| Sidebar opening | `Relaxed`, 300 ms | Enter |
| Swiped row settling | `Relaxed`, 300 ms | Standard |

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
