# Date formatting

Mail uses the `com.otus:dateformat` library from otus-meta for date display and preference storage.
Its choices, platform behavior, and API are documented in
[otus-meta's date-formatting guide](https://git.hilfikers.com/florian.ruby/otus-meta/src/branch/main/docs/dateformat.md).

## Settings state

`DateFormatSettingsState` is a presentation adapter around `DateFormatPreferenceStore`.
Settings › Appearance & behavior shows `DateFormatPreference.entries`, uses `patternLabel` as the
current English label, and shows a sample date for each choice. Translate the labels with the
app's string resources when localization is added. The screen calls `state.select(choice)`;
persistence stays outside the selection controls.

At the platform entry point, construct the state with `AndroidDateFormatPreferenceStore(context)`
or `IosDateFormatPreferenceStore()`. App wiring uses `rememberDateFormatter(state)` and passes the
formatter to screens. Changing `state.preference` through `select` updates the composition. Preview
and test compositions can use a state without a store.

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
