# 0005: User themes

## Decision

The user asked for a Theme page in Appearance & behavior where they pick a theme and an accent
color that changes the whole app. The rule that dark mode is the only color mode and that Mail
offers no theme switch is replaced. Mail offers the four themes Meta defines, Otus, Light,
Graphite, and Black, and any accent color. The device's light or dark setting still does not
change the theme, and there are no dynamic device colors. See Meta's decision 0014 for the
palette rules.

## Implementation

`App` takes a `ThemeSettingsState` and passes its palette to `OtusTheme`. `MainActivity` and the
iOS view controller create it with `AndroidThemePreferenceStore` or `IosThemePreferenceStore`, and
`MainActivity` darkens the system bar icons in the Light theme. `MailPage.ThemeSettings` shows
Meta's `ThemeSettingsScreen`, whose phone preview shows the set the picker is trying before Apply. `ThemeSettingsTest` covers picking,
saving, resetting, and a custom hex color. Below the preview, `LowContrastWarning` names the
user's colors that fall below WCAG contrast; see [the color theme](../design.md#low-contrast-warning).
Below it, `ThemeRampPicker` makes a custom theme from one hue with a port of RampenSau's color
ramps; see [colors from one hue](../design.md#colors-from-one-hue). Both live in Meta's preview
slot, the one part of the page Mail fills.

## Consequences

New UI must read colors from `OtusColors` while drawing and must work in the Light and Black
themes. The iOS host still forces a dark appearance, so its status bar icons stay light in the
Light theme until the host follows the theme.
