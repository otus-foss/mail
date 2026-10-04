# Date formatting

Implemented in `core/dateformat` for portable date display and app-local preference storage.
The Settings screen is not implemented yet. The formatter and settings state are ready for it.

## Choices and defaults

`DateFormatPreference.DeviceLocale` is the default. Platform locale data determines the numeric
date order, separators, and digits. Language and region both matter, so en-US and en-GB differ.
Dates use the Gregorian calendar and four-digit years, matching `kotlinx.datetime.LocalDate`.
Explicit choices use Latin digits and zero-padded day and month values.

| Choice | Example for 4 October 2026 |
| --- | --- |
| Device locale | 10/04/2026 for en-US, 04/10/2026 for en-GB, 04.10.2026 for de-DE |
| DD.MM.YYYY | 04.10.2026 |
| DD/MM/YYYY | 04/10/2026 |
| MM.DD.YYYY | 10.04.2026 |
| MM/DD/YYYY | 10/04/2026 |
| YYYY-MM-DD | 2026-10-04 |
| YYYY/MM/DD | 2026/10/04 |

Android uses `DateFormat.getBestDateTimePattern`; iOS uses
`NSDateFormatter.setLocalizedDateFormatFromTemplate`. Both receive a year/month/day template,
a Gregorian calendar, and UTC. The input is a date, not an instant, so the device time zone must
not shift it to another day. Formatters capture their locale when constructed. App wiring caches
them by preference and locale identifier and checks the locale when it recomposes. Android locale
configuration changes recreate the activity. A caller retaining a formatter must recreate it if
the locale changes.

## Reuse and future settings

`DateFormatter` accepts a `DateFormatPreference` and an optional locale identifier. It formats
`LocalDate`, ISO date strings, and ranges. It has no Compose, feature, storage, or account dependencies.
Malformed ISO input is rejected; do not feed user-entered dates to this API without validation.

```kotlin
val formatter = DateFormatter(DateFormatPreference.DayMonthYearSlashes)
val label = formatter.format(LocalDate(2026, 10, 4)) // 04/10/2026
```

`DateFormatSettingsState` is a presentation adapter around `DateFormatPreferenceStore`.
The future Settings screen should show `DateFormatPreference.entries`, use `patternLabel` as the
current English label, and show a sample date formatted for each choice. Translate the labels
with the app's string resources when localization is added. Pass values and a callback to the
existing reusable selection controls. The screen calls `state.select(choice)`; persistence stays
outside those controls.

At the platform entry point, construct the state with `AndroidDateFormatPreferenceStore(context)`
or `IosDateFormatPreferenceStore()`. App wiring uses `rememberDateFormatter(state)` and passes the
formatter to screens. Changing `state.preference` through `select` updates the composition. Preview
and test compositions can use a state without a store.

Android stores a stable choice ID in app-private SharedPreferences; iOS uses NSUserDefaults.
Missing or unknown IDs fall back to device formatting. Selecting DeviceLocale saves the automatic
choice rather than the currently resolved pattern, so later phone locale changes still apply.
Mail and Calendar have independent preferences; no cross-app storage or synchronization is implied.

Each repository includes the same contracts, formatter, platform adapters, settings state, and
behavior tests under its own package root. Keep those copies aligned. Shared-library packaging
is still undecided; neither repository needs a sibling checkout or a third repository to build.

## Integration and validation

Mail's Android and iOS entry points load the saved preference. Inbox message dates, selected date
filter pills, and date-picker summaries use the formatter. Changing display format leaves ISO
search criteria, sorting, drag targets, and saved draft dates intact. Day numbers and month names
retain their existing calendar presentation; full app translation is separate work.

`./gradlew checkAll` includes portable format/settings tests and Android locale/storage tests.
Android UI tests cover preference changes while a picker is open and restoration through the
production activity. Existing date gestures explicitly choose ISO for deterministic assertions.
These Linux checks do not compile or run iOS; the iOS adapters need the macOS build and runtime
validation.

Platform references:
- [Android date pattern generation](https://developer.android.com/reference/android/text/format/DateFormat#getBestDateTimePattern(java.util.Locale,%20java.lang.String))
- [Apple localized date templates](https://developer.apple.com/documentation/foundation/dateformatter/setlocalizeddateformatfromtemplate(_:))
