# Date formatting

Mail uses the `com.otusfoss.core:dateformat` library from otus-meta for date display and preference storage.
Its choices, platform behavior, and API are documented in
[otus-meta's date-formatting guide](https://github.com/reazndev/otus-meta/blob/main/docs/dateformat.md).

## Settings state

`DateFormatSettingsState` is a presentation adapter around `DateFormatPreferenceStore`.
Settings › Appearance shows `DateFormatPreference.entries`, uses `patternLabel` as the
current English label, and shows a sample date for each choice. Translate the labels with the
app's string resources when localization is added. The screen calls `state.select(choice)`;
persistence stays outside the selection controls.

At the platform entry point, construct the state with `dateFormatChoice()`, the `StoredChoice` in
the file `otus_date_format` on Android and the key `otus.date_format` on iOS. App wiring uses
otus-meta's `rememberDateFormatter(state)`, passes the formatter to the pages, and provides it as
`LocalDateFormatter`. Changing `state.preference` through `select` updates the composition. Preview
and test compositions pass `inMemoryChoice()`.

Weekdays, month names, times, and "Yesterday" come from otus-meta's `dateformat`
(`formatNamedDay`, `shortWeekday`, `clockTime`, `formatRecentDay`). Named dates put the day first
when the date format or the device locale does, so an en-US device shows "Oct 12" in the Read view
where it showed "12 Oct" before 2026-11-06, and a day-first format shows "8 Oct" in the list where
it showed "Oct 8". Quote headers keep the day first, since they are read while a message is parsed.

## Integration and validation

Mail's Android and iOS entry points load the saved preference. Inbox message dates, selected date
filter pills, and date-picker summaries use the formatter. Changing display format leaves ISO
search criteria, sorting, drag targets, and saved draft dates intact. Day numbers and month names
retain their existing calendar presentation; full app translation is separate work.

`./gradlew checkAll` includes the settings-state tests. The formatter and storage tests run in otus-meta.
Android UI tests cover preference changes while a picker is open and restoration through the
production activity. Existing date gestures explicitly choose ISO for deterministic assertions.
These Linux checks do not compile or run iOS; the iOS adapters need the macOS build and runtime
validation.
