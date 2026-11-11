# Color theme

The user picks one of four themes, **Otus**, **Light**, **Graphite**, or **Black**, and an accent
color on Settings › Appearance › Theme. System light/dark settings do not change the
app theme. `OtusColors` holds named tokens that follow the picked theme, and `OtusTheme` supplies
Material colors and default content styling. Ordinary text inherits `text1` at full opacity; muted
text requires an explicit `text2` or `text3` choice. The default, Otus with Otus blue, uses the
supplied palette in Core's
[mail design foundations](https://github.com/otus-foss/otus-core/blob/main/docs/suite/mail-design.md).
Core's `accentShades` derives the other themes' accent shades; see Core's
[decision 0014](https://github.com/otus-foss/otus-core/blob/main/docs/decisions/0014-user-themes.md).

The application background and default Material surface use `bgRegular`. Alternate surfaces and
borders use their named tokens when explicitly designed. Accent roles use the supplied blues; the
Material error slot currently reuses `accentLight` because no separate error color was supplied.
Error UI must communicate meaning through text, not color alone.

Android's starting window uses the default theme's background, text, and accent tokens from
`androidApp/src/main/res/values/colors.xml`, which Core's `tools/launch_colors.py` writes; the
architecture check fails when it drifts. `MainActivity` switches the system bar icons to their dark
appearance in the Light theme. The iOS host still requests a dark appearance, so its status bar
keeps light icons in the Light theme.

## Low-contrast warning

When the user's own colors are hard to read, the Theme page shows a warning strip below the phone
preview, such as "Low contrast: Muted text on page" over "2.1:1, needs 4.5:1 · 2 more". It uses
WCAG contrast ratios. Text, secondary text, muted text, and accent text need 4.5:1 on the page
and on raised surfaces, and text needs 4.5:1 on controls and on the accent fill. The accent
outline marks controls rather than spelling words, so it needs 3:1 on the page and raised surfaces.
Borders are not checked. A pair counts only when the user picked one of its colors, a color of the
custom theme or an accent that is no preset, so built-in themes with a preset accent never warn.
The warning names the worst pair and counts the rest, rounding ratios down so a pair just below
its minimum never reads as passing. `feature/theme/presentation/LowContrast` finds the pairs and
`LowContrastWarning` shows them with Core's `InfoStrip`.

## Colors from one hue

Below the warning, **Colors from one hue** makes a whole set of theme colors from one hue: the
raised surface, page, borders, and controls, the three text levels, and the accent. A slider
picks the hue, starting at the current accent's, and **Dark mode** or **Light mode** picks bright
text on deep surfaces or the other way round. The stack of swatches shows the set, and the phone
preview shows it while the user tries hues. **Shuffle** draws another set from the same hue, and
**Apply** adds it as a new custom theme and selects it, so the user's own themes stay as they are.
Apply also sets the accent, which every theme shares.

The colors come from a port of the ramp generation in
[RampenSau](https://github.com/meodai/rampensau) by David Aerne, under the MIT licence, in
`feature/theme/domain/ColorRamp`. `themeRamp` walks one ramp of ten colors from the raised
surface to the strongest text: the hue starts at the picked hue and drifts up to a quarter turn,
saturation fades from a tint to nearly grey by RampenSau's `x²`, and lightness eases by `x^1.8`,
steeper than RampenSau's `x^1.5`, so the page stays close to the raised surface. The first four
colors become the surfaces and borders and the last three the text levels; the accent is the
picked hue, saturated. A seed draws the drift, the tint, and the lightness at either end, so the
same hue and seed always give the same set. Before a set is shown, `withRamp` checks it with
`lowContrast` and moves each text or accent that fails a step further from the surfaces until
every pair reaches the ratios above. `ThemeRampPicker` lays it out with Core's `SwatchStack`,
`HueSlider`, `SegmentedToggle`, and `ButtonPair`.
