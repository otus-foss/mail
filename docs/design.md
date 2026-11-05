# Color theme

The user picks one of four themes, **Otus**, **Light**, **Graphite**, or **Black**, and an accent
color on Settings › Appearance › Theme. System light/dark settings do not change the
app theme. `OtusColors` holds named tokens that follow the picked theme, and `OtusTheme` supplies
Material colors and default content styling. Ordinary text inherits `text1` at full opacity; muted
text requires an explicit `text2` or `text3` choice. The default, Otus with Otus blue, uses the
supplied palette in Meta's
[mail design foundations](https://github.com/reazndev/otus-meta/blob/main/docs/suite/mail-design.md).
Meta's `accentShades` derives the other themes' accent shades; see Meta's
[decision 0014](https://github.com/reazndev/otus-meta/blob/main/docs/decisions/0014-user-themes.md).

The application background and default Material surface use `bgRegular`. Alternate surfaces and
borders use their named tokens when explicitly designed. Accent roles use the supplied blues; the
Material error slot currently reuses `accentLight` because no separate error color was supplied.
Error UI must communicate meaning through text, not color alone.

Android's starting window uses the default theme's background, text, and accent tokens from
`androidApp/src/main/res/values/colors.xml`, which Meta's `tools/launch_colors.py` writes; the
architecture check fails when it drifts. `MainActivity` switches the system bar icons to their dark
appearance in the Light theme. The iOS host still requests a dark appearance, so its status bar
keeps light icons in the Light theme.
