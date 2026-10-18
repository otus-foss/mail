---
title: "Use theme color tokens, read when drawing"
scope: "file"
path: ["**/*.kt"]
severity_min: "high"
---

## Instructions

Colors come only from the `OtusColors` tokens inside `OtusTheme`. The app's theme decides them,
never the device's light or dark setting. Flag these:

- A `Color(0x...)` literal or `Color.White`/`Color.Black` outside Meta's `designsystem` `theme`
  package.
- `isSystemInDarkTheme()`, dynamic device colors, or any branch on the device's light or dark
  setting.
- A token copied into a stored value, such as a state field, a `remember` without the theme as a
  key, or a top-level `val`. Read tokens at draw time so a theme change repaints.
- `text2` or `text3` used without a clear reason, such as secondary metadata. Default text is
  `text1` at full opacity.
- Alpha applied to a token to fake a different shade instead of using the right token.

## Examples

### Bad example

```kotlin
class RowState(val titleColor: Color = OtusColors.text1)
```

### Good example

```kotlin
Text(title, color = OtusColors.text1)
```
