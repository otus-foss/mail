# Motion

Motion lives in common Kotlin code. `designsystem/theme/MotionTokens` owns the suite's cubic
Bézier curves. Keep their values aligned between the independent Mail and Calendar repositories.
Cross-repository library distribution remains undecided.

| Token | Bézier control points | Use |
| --- | --- | --- |
| `standard` | (0.4, 0, 0.2, 1) | Changes to content that stays on screen, including Mail press feedback |
| `enter` | (0.2, 0, 0, 1) | Incoming content decelerates into position |
| `exit` | (0.4, 0, 1, 1) | Outgoing content accelerates away |

`designsystem/elements/AnimatedText` takes text, numeric order, a modifier, and typography.
Increasing order slides upward; decreasing order slides downward. Incoming text slides from one
text height away and fades in over 260 ms. Outgoing text slides away and fades out over 180 ms.
The element clips motion to its bounds and exposes only the latest label to accessibility.
Unchanged text stays still, even when its order changes. Rapid updates retarget the animation.
Compose's motion duration scale applies, including zero-duration animations.

Mail's date-filter calendar uses separate elements for month and year in the joined navigation
capsule. Both receive chronological month order, so December/January transitions move in the
correct direction. The year animates only when its displayed value changes. Scrolling, arrow
navigation, and month/year selection all use the same visible-month state. Calendar has the
reusable tokens and element available for future screens; its welcome screen is unchanged.

Mail's `AnimatedTextTest` checks both directions, interruption, latest-label accessibility, and
unchanged text. Existing date-navigation and calendar-scroll tests cover the integrated header.
Physical-device motion review and iOS runtime validation remain separate from local Android tests.
