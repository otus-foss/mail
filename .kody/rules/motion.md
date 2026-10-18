---
title: "Animate only through Meta's motion API"
scope: "file"
path: ["**/*.kt"]
severity_min: "high"
---

## Instructions

Flag any animation that bypasses the Meta design system's motion API, `Motion`:
`motionTween(speed, curve)` in composables, `Motion.tween(speed, curve, scale)` outside
composition, and `Motion.millis(speed, scale)` for specs that are not tweens.

Flag these:

- Any reference to `MotionTokens`. Meta removed it in favor of `Motion`.
- A raw duration constant or a `tween` with a literal duration.
- A new `CubicBezierEasing` or other custom easing.
- An `animate*AsState`, `AnimatedVisibility`, `animateContentSize`, or similar call that falls back
  to a Compose default spec.
- Code that ignores `LocalMotionScale`.
- Edits to the speed values themselves. Scaling belongs to `LocalMotionScale`.

Check that the speed matches what moves: `Quick` for touch feedback, `Fast` for small in-place
changes, `Normal` for panels, sheets, and text, `Relaxed` for large surfaces such as the sidebar.
Check that the curve matches direction: `Standard` by default, `Enter` for arrivals, `Exit` for
departures, and `Reverse` to retrace a `Standard` change.

## Examples

### Bad example

```kotlin
val alpha by animateFloatAsState(if (visible) 1f else 0f, tween(300))
```

### Good example

```kotlin
val alpha by animateFloatAsState(
    if (visible) 1f else 0f,
    motionTween<Float>(MotionSpeed.Fast, MotionCurve.Enter),
)
```
