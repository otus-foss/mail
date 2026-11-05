# Color theme

The user picks one of four themes, **Otus**, **Light**, **Graphite**, or **Black**, and an accent
color on Settings › Appearance › Theme. System light/dark settings do not change the
app theme. `OtusColors` holds named tokens that follow the picked theme, and `OtusTheme` supplies
Material colors and default content styling. Ordinary text inherits `text1` at full opacity; muted
text requires an explicit `text2` or `text3` choice. The default, Otus with Otus blue, uses the
supplied palette below. Meta's `accentShades` derives the other themes' accent shades; see Meta's
decision 0014 and [decision 0005](decisions/0005-user-themes.md).

| Token         | Hex     |
| ------------- | ------- |
| accentLight   | #618BB6 |
| accentRegular | #4D6F92 |
| accentDark    | #39526B |
| bgLight       | #3F4041 |
| bgRegular     | #202021 |
| bgDark        | #151515 |
| text1         | #EBECED |
| text2         | #C2C2C2 |
| text3         | #8A8A8A |
| strokeBgDark  | #27292C |

The application background and default Material surface use `bgRegular`. Alternate surfaces and
borders use their named tokens when explicitly designed. Accent roles use the supplied blues; the
Material error slot currently reuses `accentLight` because no separate error color was supplied.
Error UI must communicate meaning through text, not color alone.

Android's starting window mirrors the default theme's background/text/accent tokens in XML
resources. `MainActivity` switches the system bar icons to their dark appearance in the Light theme.
Keep those native resource values aligned with the default palette. The iOS host still requests a
dark appearance, so its status bar keeps light icons in the Light theme.

## Low-contrast warning

When the user's own colors are hard to read, the Theme page shows a warning strip below the inbox
preview, such as "Low contrast: Muted text on page" over "2.1:1, needs 4.5:1 · 2 more". It uses
WCAG contrast ratios. Text, secondary text, muted text, and accent text need 4.5:1 on the page
and on raised surfaces, and text needs 4.5:1 on controls and on the accent fill. The accent
outline marks controls rather than spelling words, so it needs 3:1 on the page and raised surfaces.
Borders are not checked. A pair counts only when the user picked one of its colors, a color of the
custom theme or an accent that is no preset, so built-in themes with a preset accent never warn.
The warning names the worst pair and counts the rest, rounding ratios down so a pair just below
its minimum never reads as passing. `feature/theme/presentation/ThemeContrast` finds the pairs and
`LowContrastWarning` shows them with Meta's `InfoStrip`.
