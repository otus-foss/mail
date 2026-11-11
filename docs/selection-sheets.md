# Selection sheets

Inbox filter pills now open custom modal bottom sheets. These are implemented UI, not complete
Figma picker replicas: `meta` page `0:1` was inspected, and search frame `23:563` was inspected
through design context and screenshot. Its popup surface (`24:655`) uses bg-dark, stroke-bg-dark,
and 22 px corners, but contains no picker content. Selected-contact examples exist at `23:447`
and `23:475`. Their visual cues inform these new pickers; the agreed bottom-sheet placement,
search fields, rows, actions, and calendar are app designs. The full date picker was subsequently recreated as editable Figma components (review `39:2`).
Its [month navigation](https://www.figma.com/design/7HWGPYYexnCqvkEYdEKIgq/meta?node-id=40-14)
is implemented as reusable `MonthNavigation`, fixed above the calendar with a single weekday row.
Its month and year use the reusable [animated text element](motion.md), with directional slides and
fades following chronological order during scrolling and navigation.
The scrolling content uses continuous Monday-based week rows. Weeks that span two months stay
in one row, with no gaps or duplicate dates. A small 12 sp abbreviated month label appears above
day 1 inside its cell, using accent-light when unselected and text-1 when selected. It has no
separate action. All day numbers share the same baseline. The large top control follows the newest
month entered by the top visible week, retains previous/next arrows, and opens the month/year
picker on its label. Only dates outside the allowed years leave empty cells at the timeline ends.
Trailing space lets the final weeks reach the top without adding dates from the next year.
Month backgrounds alternate between bg-dark and bg-regular. The tint fills each day's entire cell,
including the space around the selection band, so the boundary remains visible within a mixed-month
week. The alternation follows the calendar month and stays stable while scrolling, including across
December and January. Selected dates keep their existing accent colors above the month tint.
Each month has a continuous outline with 8 dp outer and inward corner curves at the stepped week
boundaries. The cached outline is clipped to each visible week, without seams or rounding between
ordinary rows. The shape mirrors in RTL. Day numbers stay vertically centered in their cells;
the month marker is positioned independently above day 1. Cells grow with large text so the marker
fits without overlapping the number.

## Shared controls

`ListSheet` is a design-system bottom sheet built on the same `SheetFrame` as `ChoiceSheet`:
a grab handle, a centered title with Clear before it and Done after it, an optional search slot,
and a scrollable content area. `PickerSearch` composes the existing `SearchField` element; its
text is independent of the mail query. Rows are `PickRow`s.
They share clipped press feedback with pills and icon buttons, including keyboard focus feedback.
All colors use the supplied dark palette; text uses Figtree and the existing symbols use Material
Symbols Rounded. `SheetTokens` owns sheet geometry. No new palette colors or icon assets are added.

The sheet slides up from the bottom edge. Outside-tap, system Back, or swiping down anywhere on
it slides it away; in a scrolling list or calendar, the swipe takes over once the content is at
its top. Everything else, including the header, the search field, and empty space, drags the
sheet directly. Safe-area and keyboard padding keep the content above the keyboard. Width is
capped at 560 dp on larger displays.

## Drafts and filters

Opening a sheet copies the applied criteria into a saveable draft. Row presses only change that
draft. Done commits it once the sheet has slid away; every other way of closing discards it.
Clear resets only the current filter in the draft. Double-tapping Clear also applies the cleared
draft and closes the sheet.
Picker search does not clear selections, and reopening resets its local query.
Screen criteria, open filter, draft selections, and picker query survive activity recreation.

- **People / From:** match sender name or address within the picker, with multiple sender choices,
  avatar initials, and email subtitles. Filtering is by sender, not recipient. Sender choices
  come from all supplied messages, not only the currently filtered results. JMAP Contacts is deferred.
- **Accounts:** searchable multiple account choices with account avatars. Show the address beneath
  the account name only when it differs. No selection means all accounts. The pill
  now says Accounts rather than the Figma label Mails to make its purpose explicit.
  App wiring supplies saved accounts, so accounts without loaded mail remain selectable.
  On the Inbox page, an Inboxes section follows the accounts. It lists every account's folder
  names once each, with each account's inbox as Inbox. An inbox choice matches that mailbox in
  every account the search includes, so Work and Archive together find Work's Archive. The
  search field filters both lists. Inbox starts checked. With no inbox checked, search covers every
  inbox, and Clear unchecks them all. Inbox rows are 48 dp tall, since they have one line and no
  avatar. Folder pages and active folder pills show no Inboxes section.
- **Date:** regular seven-column calendar with plain day numbers, a continuous rectangular
  range band, and softly squared endpoints. Only exposed endpoint edges have rounded corners;
  edges joining another selected day in the same row are square, including in RTL. Hold a date, then drag across days or week rows to preview
  an inclusive range; reverse dragging is supported. Releasing retains the draft, while gesture
  cancellation restores the previous range. Vertical swipes scroll continuous weeks without snapping. Holding a date before dragging
  selects a range instead. The fixed month control tracks the visible month, and inline labels mark each month's first day. Tapping two dates remains available for touch, keyboard, and accessibility
  actions, including endpoints in different months. Double-tapping a date selects the inclusive
  range between that date and today. Future dates are absent and cannot be selected. Each date also
  exposes a "Select through today" accessibility action. Done is disabled for a half-selected range.
  A start-only selection reads `[date] → Choose an end date` in one summary, wrapping only when
  needed. The separate scrolling/dragging instruction line is omitted.
  The month/year heading uses stable month and year columns so changing the month name does not
  shift the year or arrow controls. Tap it to open a reusable `MonthYearPicker` with independent
  day, month, and year columns. Column widths stay proportional to the available screen width,
  with more room for month names. Days match the chosen month and year, including leap years;
  changing to a shorter month clamps the selected day to its last valid day. Years run downward from the device’s current year to 1970;
  older years are below newer ones. Future dates are excluded: the current year stops at the current month, and the current
  month stops at today. Next-month navigation stops at the current month. Previous-month navigation
  stops at January 1970. Single-choice rows use a highlight without checkbox
  icons, while retaining accessible selected-state semantics. Go navigates to the chosen date and
  selects it using the same start/end range behavior as tapping a calendar day. Cancel, outside-tap,
  and Back discard picker edits.
  Selecting another date after a complete range starts a new range. Today, Last 7 days, and Last 30 days use the device's local date.
  The ranges include today and the previous six or 29 days, respectively. A shortcut uses the active
  blue fill and border when its complete range matches the draft, including manual date selection.
  Clearing or changing the range updates the highlight. Clear + Done removes date restrictions.
  The draft summary shows Today and Yesterday for matching device-local dates; other dates use the
  selected display format. Applied date pills show Today, This week, or This month when the selected
  dates match those shortcuts. Other single dates show one label, and other ranges label each endpoint
  independently. Stored dates and filtering remain ISO-based.
  A week can contain dates from adjacent months, with one uninterrupted range band across the boundary.
- **Filters:** unread, attachment, and flagged switches also use draft/Done behavior.

Selections are ORed within each category and ANDed across categories and the mail query.
Search pills summarize applied selections. With no account selected, the account pill reads
"All accounts" without a selection highlight. One selected account shows its avatar and name;
several show only overlapping avatars, the same as the people pill. The chosen inboxes follow a
thin divider line: "Inbox", "Inbox +1" for several, or "All inboxes" when none is checked. Beside
particular inboxes, one account shows only its avatar. Screen readers hear a comma in place of the
line. The pill is highlighted once accounts are chosen or the inboxes differ from the page's.
An empty message source shows no senders. Saved accounts still appear in the account picker.
No demonstration contacts, accounts, or messages are shipped.

Calendar rows are at least 48 dp tall and grow with text. Seven equal-width columns fit the sheet
without horizontal scrolling; cell widths are narrower than 48 dp on small screens. The calendar scrolls independently beneath the date summary and shortcuts. Those controls have a
limited, scrollable area on short screens so the calendar and footer stay reachable. Range
dragging spans months and years. Hold a date to anchor the range, then drag toward the top or bottom
of the calendar to auto-scroll while holding. The anchor survives week recycling; releasing keeps
the range and cancellation restores the previous draft. Scrolling stops at January 1970 and today. Tap endpoints across months remains available.
Calendar arithmetic uses the pinned [kotlinx-datetime 0.7.1](https://github.com/Kotlin/kotlinx-datetime/releases/tag/v0.7.1)
library in common Kotlin. Source-provided ISO message dates are compared inclusively; JMAP timestamp
conversion remains part of the future data adapter.

## Date chooser

Core 0.4.0 provides two modal wheel pickers with centered, snapping day/month/year wheels,
fading adjacent choices, a close control, and a full-width Go button. Both take an initial
`YearMonth` and an inclusive `allowedDates` range; callers may include future dates.
`SpecificDatePicker` disables Go until a day is selected and confirms with a `LocalDate`.
`MonthYearPicker` exposes Any for the day and confirms with a `YearMonth` and a nullable date.

Mail's calendar uses `MonthYearPicker` with January 1970 through today as its allowed range.
Opening the chooser starts at Any. Month-only confirmation navigates without changing the
draft range. An explicit day retains the existing start/end behavior. Close, outside-tap,
and Back discard chooser edits. The calendar layout, scrolling, and range gestures are unchanged.

## Validation

`./gradlew checkAll` covers domain combinations and inclusive ranges, UI draft/apply/dismiss,
independent search and multi-selection, single-person and multiple-person pill summaries,
double-tap Clear, date selection, held/reverse/cancelled range drags, and small-screen/large-text layouts.
Calendar checks cover unique consecutive dates, mixed-month and mixed-year rows, leap years,
navigation bounds, month markers, and range selection across a year boundary within one row.
Picker screenshots are written to `androidApp/build/reports/pickers/` by local Robolectric tests.
Physical-device keyboard, accessibility service, and iOS runtime checks remain separate.

## Calendar rendering

The lazy list composes week rows near the viewport. Integer keys identify weeks from the fixed
January 1970 origin without formatting dates for key lookup. Rows remember their date values and
labels. The scrolling month heading reads its state in its own composition scope. Days without a
month marker do not compose a placeholder text layout. Month outlines are cached drawing paths,
reused while scrolling rather than rebuilt for each frame.
Shared press feedback draws a cached shape outline only while visible, without adding a clipping
layer to every control. The animation still stays within the control's shape.

Android release builds now enable R8 optimization and resource shrinking. Debug builds remain
debuggable. Assess scrolling on a physical device with an optimized build; local UI tests check
behavior and rendering but do not establish frame performance. See
[Android's Compose performance guidance](https://developer.android.com/develop/ui/compose/performance).

### Pixel scroll comparison, 2026-10-03

Measured on the connected Pixel 9a running Android 17, before the subsequent month-outline corner
and date-label centering refinements. Raw captures are in `androidApp/build/reports/calendar-performance/`.
Each build used two warm-up swipe pairs,
then three runs of four down/up pairs inside the calendar, with 450 ms swipes and 250 ms pauses.
Each run reset `dumpsys gfxinfo com.otusfoss.mail`; results below use the process totals once, without
counting the repeated per-window summaries. Builds used the same package and local development
signing key. No system settings were changed.

| Build | Frames across three runs | Janky frames | 90th-percentile frame time per run |
| --- | ---: | ---: | --- |
| Original debug | 150 | 134, 89.3% | 300 / 300 / 300 ms |
| Updated debug | 187 | 161, 86.1% | 150 / 200 / 200 ms |
| Updated optimized release | 962 | 0 | 9 / 9 / 9 ms |

The device reported `dalvik.vm.usejit=false`. The debug package had `run-from-apk` dexopt status;
the release package was automatically compiled with `speed` at installation. This comparison
therefore includes the device's different compilation treatment of debug and release, as well as
R8 optimization. It does not isolate R8's contribution or establish performance on other devices.
The optimized release was left installed for manual review. It is a locally signed preview, not a
published release. No app-specific Baseline Profile or automated Macrobenchmark suite was added.
