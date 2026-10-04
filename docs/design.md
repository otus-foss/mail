# Color theme

Both apps use the supplied Otus palette in dark mode only. System light/dark settings do not change
the app theme. `OtusColors` holds named tokens and `OtusTheme` supplies Material colors and default
content styling. Ordinary text inherits `text1` (`#EBECED`) at full opacity; muted text requires an
explicit `text2` or `text3` choice.

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

Android's starting window mirrors the background/text/accent tokens in XML resources, and system bar
icons use their light appearance. Keep those native resource values aligned with the common palette.
The iOS host explicitly requests a dark appearance too.
