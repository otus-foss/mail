# Mail component inventory

This inventory covers every production Otus composable source file in Mail or its shared UI libraries, plus selected supporting files. It includes public components, private UI helpers, UI state/value types, and the Mail playground. Compose framework widgets, generated resources, and test-only fixtures are excluded. Platform implementations of shared avatar decoding remain in otus-meta and implement the same common API.

"Shared now" means added in Meta 0.3.0 or 0.4.0 and consumed by Mail. "Already shared" means present since Meta 0.2.1 and still consumed through 0.4.0. "Mail adapter" means the rendering is shared while Mail maps feature data into it. "Mail" means the app still owns the composition or behavior. Calendar is unchanged.

Meta source links point to the `v0.4.0` tag in its own repository, so
the inventory works on GitHub and in a standalone Mail clone. Mail source links remain local.

These choices are reviewable. Keeping a Mail composition does not keep a second copy of its shared panel, pill, sidebar, or setting controls.

Meta 0.4.0 adds `SpecificDatePicker` for one required date and gives `MonthYearPicker` an
optional day. Both use the shared `DateWheelPicker` body and the `WheelPicker` element.
Mail's calendar uses `MonthYearPicker`.

## Shared now

| File | UI declarations or supporting file | Owner | Reason |
| --- | --- | --- | --- |
| [AccountsState](https://github.com/reazndev/otus-meta/blob/v0.4.0/accounts/src/commonMain/kotlin/com/otus/accounts/presentation/AccountsState.kt) | `AccountsState` | `otus-meta/accounts` | Shared account setup page, connected account row, or screen state. |
| [ConnectMessages](https://github.com/reazndev/otus-meta/blob/v0.4.0/accounts/src/commonMain/kotlin/com/otus/accounts/presentation/ConnectMessages.kt) | `ConnectMessages` | `otus-meta/accounts` | Shared account setup page, connected account row, or screen state. |
| [ConnectedAccountRow](https://github.com/reazndev/otus-meta/blob/v0.4.0/accounts/src/commonMain/kotlin/com/otus/accounts/presentation/components/ConnectedAccountRow.kt) | `ConnectedAccountRow` | `otus-meta/accounts` | Shared account setup page, connected account row, or screen state. |
| [AccountSetupScreen](https://github.com/reazndev/otus-meta/blob/v0.4.0/accounts/src/commonMain/kotlin/com/otus/accounts/presentation/screens/AccountSetupScreen.kt) | `AccountSetupScreen`, `AddAccountForm`, `ConfirmHostPrompt` | `otus-meta/accounts` | Shared account setup page, connected account row, or screen state. |
| [AvatarStackChip](https://github.com/reazndev/otus-meta/blob/v0.4.0/designsystem/src/commonMain/kotlin/com/otus/designsystem/components/AvatarStackChip.kt) | `AvatarStackChip`, `StackedAvatar` | `otus-meta/designsystem` | Reusable visual element, component, theme, or UI support. |
| [CalendarDragTargets](https://github.com/reazndev/otus-meta/blob/v0.4.0/designsystem/src/commonMain/kotlin/com/otus/designsystem/components/CalendarDragTargets.kt) | `CalendarDragTargets` | `otus-meta/designsystem` | Reusable visual element, component, theme, or UI support. |
| [CalendarHeading](https://github.com/reazndev/otus-meta/blob/v0.4.0/designsystem/src/commonMain/kotlin/com/otus/designsystem/components/CalendarHeading.kt) | `CalendarHeading` | `otus-meta/designsystem` | Reusable visual element, component, theme, or UI support. |
| [CalendarRangeGesture](https://github.com/reazndev/otus-meta/blob/v0.4.0/designsystem/src/commonMain/kotlin/com/otus/designsystem/components/CalendarRangeGesture.kt) | `calendarRangeGesture` | `otus-meta/designsystem` | Reusable visual element, component, theme, or UI support. |
| [CalendarWeek](https://github.com/reazndev/otus-meta/blob/v0.4.0/designsystem/src/commonMain/kotlin/com/otus/designsystem/components/CalendarWeek.kt) | `CalendarWeek`, `CalendarDateCell` | `otus-meta/designsystem` | Reusable visual element, component, theme, or UI support. |
| [CalendarWeeks](https://github.com/reazndev/otus-meta/blob/v0.4.0/designsystem/src/commonMain/kotlin/com/otus/designsystem/components/CalendarWeeks.kt) | `CalendarWeeks` | `otus-meta/designsystem` | Reusable visual element, component, theme, or UI support. |
| [ChipAvatar](https://github.com/reazndev/otus-meta/blob/v0.4.0/designsystem/src/commonMain/kotlin/com/otus/designsystem/components/ChipAvatar.kt) | `ChipAvatar` | `otus-meta/designsystem` | Reusable visual element, component, theme, or UI support. |
| [ContentRow](https://github.com/reazndev/otus-meta/blob/v0.4.0/designsystem/src/commonMain/kotlin/com/otus/designsystem/components/ContentRow.kt) | `ContentRow` | `otus-meta/designsystem` | Central renderer accepting display values, content slots, and callbacks. |
| [ContinuousCalendar](https://github.com/reazndev/otus-meta/blob/v0.4.0/designsystem/src/commonMain/kotlin/com/otus/designsystem/components/ContinuousCalendar.kt) | `ContinuousCalendar`, `VisibleMonthHeading`, `CalendarWeekdays` | `otus-meta/designsystem` | Reusable visual element, component, theme, or UI support. |
| [DatePickerBounds](https://github.com/reazndev/otus-meta/blob/v0.4.0/designsystem/src/commonMain/kotlin/com/otus/designsystem/components/DatePickerBounds.kt) | `DatePickerBounds` | `otus-meta/designsystem` | Reusable visual element, component, theme, or UI support. |
| [DateRangePicker](https://github.com/reazndev/otus-meta/blob/v0.4.0/designsystem/src/commonMain/kotlin/com/otus/designsystem/components/DateRangePicker.kt) | `DateRangePicker`, `DateRangeSummary`, `DateRangeShortcuts` | `otus-meta/designsystem` | Reusable visual element, component, theme, or UI support. |
| [DateWheelPicker](https://github.com/reazndev/otus-meta/blob/v0.4.0/designsystem/src/commonMain/kotlin/com/otus/designsystem/components/DateWheelPicker.kt) | `DateWheelPicker`, `DatePickerColumn`, `DatePickerHeading`, `DatePickerConfirm` | `otus-meta/designsystem` | Reusable visual element, component, theme, or UI support. |
| [DetailRow](https://github.com/reazndev/otus-meta/blob/v0.4.0/designsystem/src/commonMain/kotlin/com/otus/designsystem/components/DetailRow.kt) | `DetailRow` | `otus-meta/designsystem` | Central renderer accepting display values, content slots, and callbacks. |
| [EmptyState](https://github.com/reazndev/otus-meta/blob/v0.4.0/designsystem/src/commonMain/kotlin/com/otus/designsystem/components/EmptyState.kt) | `EmptyState` | `otus-meta/designsystem` | Central renderer accepting display values, content slots, and callbacks. |
| [SpecificDatePicker](https://github.com/reazndev/otus-meta/blob/v0.4.0/designsystem/src/commonMain/kotlin/com/otus/designsystem/components/SpecificDatePicker.kt) | `SpecificDatePicker` | `otus-meta/designsystem` | Reusable visual element, component, theme, or UI support. |
| [WheelPicker](https://github.com/reazndev/otus-meta/blob/v0.4.0/designsystem/src/commonMain/kotlin/com/otus/designsystem/elements/WheelPicker.kt) | `WheelPicker` | `otus-meta/designsystem` | Reusable visual element, component, theme, or UI support. |
| [InboxTokens](https://github.com/reazndev/otus-meta/blob/v0.4.0/designsystem/src/commonMain/kotlin/com/otus/designsystem/theme/InboxTokens.kt) | `InboxTokens` | `otus-meta/designsystem` | Central message-list and empty-state visual tokens. |
| [WheelTokens](https://github.com/reazndev/otus-meta/blob/v0.4.0/designsystem/src/commonMain/kotlin/com/otus/designsystem/theme/WheelTokens.kt) | `WheelTokens` | `otus-meta/designsystem` | Reusable visual element, component, theme, or UI support. |
| [DateFormatExamples](https://github.com/reazndev/otus-meta/blob/v0.4.0/settings/src/commonMain/kotlin/com/otus/settings/presentation/DateFormatExamples.kt) | `DateFormatExamples` | `otus-meta/settings` | Shared settings list, appearance page, preference state, or examples. |
| [DateFormatSettingsState](https://github.com/reazndev/otus-meta/blob/v0.4.0/settings/src/commonMain/kotlin/com/otus/settings/presentation/DateFormatSettingsState.kt) | `DateFormatSettingsState` | `otus-meta/settings` | Shared settings list, appearance page, preference state, or examples. |
| [SettingsEntry](https://github.com/reazndev/otus-meta/blob/v0.4.0/settings/src/commonMain/kotlin/com/otus/settings/presentation/SettingsEntry.kt) | `SettingsEntry` | `otus-meta/settings` | Shared settings list, appearance page, preference state, or examples. |
| [AppearanceSettingsScreen](https://github.com/reazndev/otus-meta/blob/v0.4.0/settings/src/commonMain/kotlin/com/otus/settings/presentation/screens/AppearanceSettingsScreen.kt) | `AppearanceSettingsScreen` | `otus-meta/settings` | Shared settings list, appearance page, preference state, or examples. |
| [SettingsScreen](https://github.com/reazndev/otus-meta/blob/v0.4.0/settings/src/commonMain/kotlin/com/otus/settings/presentation/screens/SettingsScreen.kt) | `SettingsScreen` | `otus-meta/settings` | Shared settings list, appearance page, preference state, or examples. |
| [ContentRowText](https://github.com/reazndev/otus-meta/blob/v0.4.0/designsystem/src/commonMain/kotlin/com/otus/designsystem/components/ContentRowText.kt) | `ContentRowText` | `otus-meta/designsystem` | Caller-prepared display text for ContentRow. |

## Already shared

| File | UI declarations or supporting file | Owner | Reason |
| --- | --- | --- | --- |
| [ActionDialog](https://github.com/reazndev/otus-meta/blob/v0.4.0/designsystem/src/commonMain/kotlin/com/otus/designsystem/components/ActionDialog.kt) | `ActionDialog`, `InputDialog` | `otus-meta/designsystem` | Reusable visual element, component, theme, or UI support. |
| [ChoiceDialog](https://github.com/reazndev/otus-meta/blob/v0.4.0/designsystem/src/commonMain/kotlin/com/otus/designsystem/components/ChoiceDialog.kt) | `ChoiceDialog` | `otus-meta/designsystem` | Reusable visual element, component, theme, or UI support. |
| [ContextPanel](https://github.com/reazndev/otus-meta/blob/v0.4.0/designsystem/src/commonMain/kotlin/com/otus/designsystem/components/ContextPanel.kt) | `ContextPanel`, `PanelHeader` | `otus-meta/designsystem` | Reusable visual element, component, theme, or UI support. |
| [ContextPanelScope](https://github.com/reazndev/otus-meta/blob/v0.4.0/designsystem/src/commonMain/kotlin/com/otus/designsystem/components/ContextPanelScope.kt) | `item`, `transition`, `PanelChipRail` | `otus-meta/designsystem` | Reusable visual element, component, theme, or UI support. |
| [DatePickerDialog](https://github.com/reazndev/otus-meta/blob/v0.4.0/designsystem/src/commonMain/kotlin/com/otus/designsystem/components/DatePickerDialog.kt) | `DatePickerDialog` | `otus-meta/designsystem` | Reusable visual element, component, theme, or UI support. |
| [DialogAction](https://github.com/reazndev/otus-meta/blob/v0.4.0/designsystem/src/commonMain/kotlin/com/otus/designsystem/components/DialogAction.kt) | `DialogAction` | `otus-meta/designsystem` | Reusable visual element, component, theme, or UI support. |
| [DialogFrame](https://github.com/reazndev/otus-meta/blob/v0.4.0/designsystem/src/commonMain/kotlin/com/otus/designsystem/components/DialogFrame.kt) | `DialogFrame`, `DialogHeading` | `otus-meta/designsystem` | Reusable visual element, component, theme, or UI support. |
| [MonthNavigation](https://github.com/reazndev/otus-meta/blob/v0.4.0/designsystem/src/commonMain/kotlin/com/otus/designsystem/components/MonthNavigation.kt) | `MonthNavigation` | `otus-meta/designsystem` | Reusable visual element, component, theme, or UI support. |
| [MonthNavigationLabel](https://github.com/reazndev/otus-meta/blob/v0.4.0/designsystem/src/commonMain/kotlin/com/otus/designsystem/components/MonthNavigationLabel.kt) | `MonthNavigationLabel` | `otus-meta/designsystem` | Reusable visual element, component, theme, or UI support. |
| [MonthYearPicker](https://github.com/reazndev/otus-meta/blob/v0.4.0/designsystem/src/commonMain/kotlin/com/otus/designsystem/components/MonthYearPicker.kt) | `MonthYearPicker` | `otus-meta/designsystem` | Reusable visual element, component, theme, or UI support. |
| [PageScaffold](https://github.com/reazndev/otus-meta/blob/v0.4.0/designsystem/src/commonMain/kotlin/com/otus/designsystem/components/PageScaffold.kt) | `PageScaffold`, `Subpage`, `PageSection`, `PageNote` | `otus-meta/designsystem` | Reusable visual element, component, theme, or UI support. |
| [PanelLayoutState](https://github.com/reazndev/otus-meta/blob/v0.4.0/designsystem/src/commonMain/kotlin/com/otus/designsystem/components/PanelLayoutState.kt) | `PanelLayoutState` | `otus-meta/designsystem` | Reusable visual element, component, theme, or UI support. |
| [PanelSurface](https://github.com/reazndev/otus-meta/blob/v0.4.0/designsystem/src/commonMain/kotlin/com/otus/designsystem/components/PanelSurface.kt) | `PanelSurface` | `otus-meta/designsystem` | Reusable visual element, component, theme, or UI support. |
| [PickerSearch](https://github.com/reazndev/otus-meta/blob/v0.4.0/designsystem/src/commonMain/kotlin/com/otus/designsystem/components/PickerSearch.kt) | `PickerSearch` | `otus-meta/designsystem` | Reusable visual element, component, theme, or UI support. |
| [PullToRefresh](https://github.com/reazndev/otus-meta/blob/v0.4.0/designsystem/src/commonMain/kotlin/com/otus/designsystem/components/PullToRefresh.kt) | `PullToRefresh` | `otus-meta/designsystem` | Reusable visual element, component, theme, or UI support. |
| [SelectionSheet](https://github.com/reazndev/otus-meta/blob/v0.4.0/designsystem/src/commonMain/kotlin/com/otus/designsystem/components/SelectionSheet.kt) | `SelectionSheet`, `rememberRepeatedClear`, `SheetHeading` | `otus-meta/designsystem` | Reusable visual element, component, theme, or UI support. |
| [Sidebar](https://github.com/reazndev/otus-meta/blob/v0.4.0/designsystem/src/commonMain/kotlin/com/otus/designsystem/components/Sidebar.kt) | `Sidebar`, `SidebarSurface` | `otus-meta/designsystem` | Reusable visual element, component, theme, or UI support. |
| [SidebarState](https://github.com/reazndev/otus-meta/blob/v0.4.0/designsystem/src/commonMain/kotlin/com/otus/designsystem/components/SidebarState.kt) | `rememberSidebarState` | `otus-meta/designsystem` | Reusable visual element, component, theme, or UI support. |
| [SidebarSwipeArea](https://github.com/reazndev/otus-meta/blob/v0.4.0/designsystem/src/commonMain/kotlin/com/otus/designsystem/components/SidebarSwipeArea.kt) | `SidebarSwipeArea`, `sidebarOpenZone` | `otus-meta/designsystem` | Reusable visual element, component, theme, or UI support. |
| [ActionButton](https://github.com/reazndev/otus-meta/blob/v0.4.0/designsystem/src/commonMain/kotlin/com/otus/designsystem/elements/ActionButton.kt) | `ActionButton` | `otus-meta/designsystem` | Reusable visual element, component, theme, or UI support. |
| [ActionRow](https://github.com/reazndev/otus-meta/blob/v0.4.0/designsystem/src/commonMain/kotlin/com/otus/designsystem/elements/ActionRow.kt) | `ActionRow` | `otus-meta/designsystem` | Reusable visual element, component, theme, or UI support. |
| [AnimatedText](https://github.com/reazndev/otus-meta/blob/v0.4.0/designsystem/src/commonMain/kotlin/com/otus/designsystem/elements/AnimatedText.kt) | `AnimatedText` | `otus-meta/designsystem` | Reusable visual element, component, theme, or UI support. |
| [Avatar](https://github.com/reazndev/otus-meta/blob/v0.4.0/designsystem/src/commonMain/kotlin/com/otus/designsystem/elements/Avatar.kt) | `Avatar`, `initialsStyle` | `otus-meta/designsystem` | Reusable visual element, component, theme, or UI support. |
| [AvatarImage](https://github.com/reazndev/otus-meta/blob/v0.4.0/designsystem/src/commonMain/kotlin/com/otus/designsystem/elements/AvatarImage.kt) | `rememberAvatarImage`, `rememberAvatarImage` | `otus-meta/designsystem` | Reusable visual element, component, theme, or UI support. |
| [CalendarDay](https://github.com/reazndev/otus-meta/blob/v0.4.0/designsystem/src/commonMain/kotlin/com/otus/designsystem/elements/CalendarDay.kt) | `CalendarDay`, `CalendarMonthLabel` | `otus-meta/designsystem` | Reusable visual element, component, theme, or UI support. |
| [CalendarMonthBackground](https://github.com/reazndev/otus-meta/blob/v0.4.0/designsystem/src/commonMain/kotlin/com/otus/designsystem/elements/CalendarMonthBackground.kt) | `CalendarMonthBackground` | `otus-meta/designsystem` | Reusable visual element, component, theme, or UI support. |
| [Chip](https://github.com/reazndev/otus-meta/blob/v0.4.0/designsystem/src/commonMain/kotlin/com/otus/designsystem/elements/Chip.kt) | `Chip` | `otus-meta/designsystem` | Reusable visual element, component, theme, or UI support. |
| [ControlPressFeedback](https://github.com/reazndev/otus-meta/blob/v0.4.0/designsystem/src/commonMain/kotlin/com/otus/designsystem/elements/ControlPressFeedback.kt) | `controlPressFeedback` | `otus-meta/designsystem` | Reusable visual element, component, theme, or UI support. |
| [ControlVisualBounds](https://github.com/reazndev/otus-meta/blob/v0.4.0/designsystem/src/commonMain/kotlin/com/otus/designsystem/elements/ControlVisualBounds.kt) | `reportControlVisualBounds` | `otus-meta/designsystem` | Reusable visual element, component, theme, or UI support. |
| [DecodedAvatarCache](https://github.com/reazndev/otus-meta/blob/v0.4.0/designsystem/src/commonMain/kotlin/com/otus/designsystem/elements/DecodedAvatarCache.kt) | `DecodedAvatarCache` | `otus-meta/designsystem` | Reusable visual element, component, theme, or UI support. |
| [IconButton](https://github.com/reazndev/otus-meta/blob/v0.4.0/designsystem/src/commonMain/kotlin/com/otus/designsystem/elements/IconButton.kt) | `IconButton` | `otus-meta/designsystem` | Reusable visual element, component, theme, or UI support. |
| [InputField](https://github.com/reazndev/otus-meta/blob/v0.4.0/designsystem/src/commonMain/kotlin/com/otus/designsystem/elements/InputField.kt) | `InputField` | `otus-meta/designsystem` | Reusable visual element, component, theme, or UI support. |
| [InputKind](https://github.com/reazndev/otus-meta/blob/v0.4.0/designsystem/src/commonMain/kotlin/com/otus/designsystem/elements/InputKind.kt) | `InputKind` | `otus-meta/designsystem` | Reusable visual element, component, theme, or UI support. |
| [RefreshIndicator](https://github.com/reazndev/otus-meta/blob/v0.4.0/designsystem/src/commonMain/kotlin/com/otus/designsystem/elements/RefreshIndicator.kt) | `RefreshIndicator`, `PullArc` | `otus-meta/designsystem` | Reusable visual element, component, theme, or UI support. |
| [SearchField](https://github.com/reazndev/otus-meta/blob/v0.4.0/designsystem/src/commonMain/kotlin/com/otus/designsystem/elements/SearchField.kt) | `SearchField` | `otus-meta/designsystem` | Reusable visual element, component, theme, or UI support. |
| [SearchPrompt](https://github.com/reazndev/otus-meta/blob/v0.4.0/designsystem/src/commonMain/kotlin/com/otus/designsystem/elements/SearchPrompt.kt) | `SearchPrompt` | `otus-meta/designsystem` | Reusable visual element, component, theme, or UI support. |
| [SelectionRow](https://github.com/reazndev/otus-meta/blob/v0.4.0/designsystem/src/commonMain/kotlin/com/otus/designsystem/elements/SelectionRow.kt) | `SelectionRow`, `SelectionCheckmark`, `SelectionLabel` | `otus-meta/designsystem` | Reusable visual element, component, theme, or UI support. |
| [SidebarEntry](https://github.com/reazndev/otus-meta/blob/v0.4.0/designsystem/src/commonMain/kotlin/com/otus/designsystem/elements/SidebarEntry.kt) | `SidebarEntry`, `SidebarHeading` | `otus-meta/designsystem` | Reusable visual element, component, theme, or UI support. |
| [Symbol](https://github.com/reazndev/otus-meta/blob/v0.4.0/designsystem/src/commonMain/kotlin/com/otus/designsystem/elements/Symbol.kt) | `Symbol` | `otus-meta/designsystem` | Reusable visual element, component, theme, or UI support. |
| [AvatarTokens](https://github.com/reazndev/otus-meta/blob/v0.4.0/designsystem/src/commonMain/kotlin/com/otus/designsystem/theme/AvatarTokens.kt) | `AvatarTokens` | `otus-meta/designsystem` | Reusable visual element, component, theme, or UI support. |
| [CalendarTokens](https://github.com/reazndev/otus-meta/blob/v0.4.0/designsystem/src/commonMain/kotlin/com/otus/designsystem/theme/CalendarTokens.kt) | `CalendarTokens` | `otus-meta/designsystem` | Reusable visual element, component, theme, or UI support. |
| [ControlFeedbackTokens](https://github.com/reazndev/otus-meta/blob/v0.4.0/designsystem/src/commonMain/kotlin/com/otus/designsystem/theme/ControlFeedbackTokens.kt) | `ControlFeedbackTokens` | `otus-meta/designsystem` | Reusable visual element, component, theme, or UI support. |
| [MotionTokens](https://github.com/reazndev/otus-meta/blob/v0.4.0/designsystem/src/commonMain/kotlin/com/otus/designsystem/theme/MotionTokens.kt) | `MotionTokens` | `otus-meta/designsystem` | Reusable visual element, component, theme, or UI support. |
| [OtusColors](https://github.com/reazndev/otus-meta/blob/v0.4.0/designsystem/src/commonMain/kotlin/com/otus/designsystem/theme/OtusColors.kt) | `OtusColors` | `otus-meta/designsystem` | Reusable visual element, component, theme, or UI support. |
| [OtusTheme](https://github.com/reazndev/otus-meta/blob/v0.4.0/designsystem/src/commonMain/kotlin/com/otus/designsystem/theme/OtusTheme.kt) | `OtusTheme` | `otus-meta/designsystem` | Reusable visual element, component, theme, or UI support. |
| [PageTokens](https://github.com/reazndev/otus-meta/blob/v0.4.0/designsystem/src/commonMain/kotlin/com/otus/designsystem/theme/PageTokens.kt) | `PageTokens` | `otus-meta/designsystem` | Reusable visual element, component, theme, or UI support. |
| [PanelTokens](https://github.com/reazndev/otus-meta/blob/v0.4.0/designsystem/src/commonMain/kotlin/com/otus/designsystem/theme/PanelTokens.kt) | `PanelTokens` | `otus-meta/designsystem` | Reusable visual element, component, theme, or UI support. |
| [PanelTypography](https://github.com/reazndev/otus-meta/blob/v0.4.0/designsystem/src/commonMain/kotlin/com/otus/designsystem/theme/PanelTypography.kt) | `panelTextStyle` | `otus-meta/designsystem` | Reusable visual element, component, theme, or UI support. |
| [RefreshTokens](https://github.com/reazndev/otus-meta/blob/v0.4.0/designsystem/src/commonMain/kotlin/com/otus/designsystem/theme/RefreshTokens.kt) | `RefreshTokens` | `otus-meta/designsystem` | Reusable visual element, component, theme, or UI support. |
| [SheetTokens](https://github.com/reazndev/otus-meta/blob/v0.4.0/designsystem/src/commonMain/kotlin/com/otus/designsystem/theme/SheetTokens.kt) | `SheetTokens` | `otus-meta/designsystem` | Reusable visual element, component, theme, or UI support. |
| [SidebarTokens](https://github.com/reazndev/otus-meta/blob/v0.4.0/designsystem/src/commonMain/kotlin/com/otus/designsystem/theme/SidebarTokens.kt) | `SidebarTokens` | `otus-meta/designsystem` | Reusable visual element, component, theme, or UI support. |

## Mail adapter

| File | UI declarations or supporting file | Owner | Reason |
| --- | --- | --- | --- |
| [FolderRow](../shared/src/commonMain/kotlin/com/otus/mail/feature/folders/presentation/components/FolderRow.kt) | `FolderRow` | `otus-mail` | Maps folder counts and tree depth into shared DetailRow. |
| [RuleRow](../shared/src/commonMain/kotlin/com/otus/mail/feature/folders/presentation/components/RuleRow.kt) | `RuleRow` | `otus-mail` | Maps rule labels and enabled state into shared DetailRow. |
| [InboxBody](../shared/src/commonMain/kotlin/com/otus/mail/feature/inbox/presentation/components/InboxBody.kt) | `InboxBody`, `InboxEmptyState` | `otus-mail` | Composes the message list and supplies feature text/actions to shared EmptyState. |
| [MessageRow](../shared/src/commonMain/kotlin/com/otus/mail/feature/inbox/presentation/components/MessageRow.kt) | `MessageRow` | `otus-mail` | Maps the sender, arrival time, folder labels, and unread state into shared ContentRow and LabelPill. |
| [AvatarFilterChips](../shared/src/commonMain/kotlin/com/otus/mail/feature/inbox/presentation/components/AvatarFilterChips.kt) | `PeopleFilterChip`, `AccountsFilterChip`, `AvatarFilterChip` | `otus-mail` | Maps selected senders and accounts into shared AvatarStackChip. |
| [SenderAvatar](../shared/src/commonMain/kotlin/com/otus/mail/feature/inbox/presentation/components/SenderAvatar.kt) | `SenderAvatar` | `otus-mail` | Maps Mail sender data into the shared Avatar element. |

## Mail

| File | UI declarations or supporting file | Owner | Reason |
| --- | --- | --- | --- |
| [App](../shared/src/commonMain/kotlin/com/otus/mail/app/App.kt) | `App` | `otus-mail` | Mail app composition, dependencies, destinations, or navigation. |
| [DateFormatting](../shared/src/commonMain/kotlin/com/otus/mail/app/DateFormatting.kt) | `rememberDateFormatter` | `otus-mail` | Mail app composition, dependencies, destinations, or navigation. |
| [MailNavigation](../shared/src/commonMain/kotlin/com/otus/mail/app/MailNavigation.kt) | `MailNavigation` | `otus-mail` | Mail app composition, dependencies, destinations, or navigation. |
| [MailPage](../shared/src/commonMain/kotlin/com/otus/mail/app/MailPage.kt) | `MailPage` | `otus-mail` | Mail app composition, dependencies, destinations, or navigation. |
| [MailPages](../shared/src/commonMain/kotlin/com/otus/mail/app/MailPages.kt) | `MailPageContent`, `FolderPage`, `rememberMessageActions`, `rememberFolderPills` | `otus-mail` | Mail app composition, dependencies, destinations, or navigation. |
| [MailServices](../shared/src/commonMain/kotlin/com/otus/mail/app/MailServices.kt) | `MailServices` | `otus-mail` | Mail app composition, dependencies, destinations, or navigation. |
| [MailSettings](../shared/src/commonMain/kotlin/com/otus/mail/app/MailSettings.kt) | `MailSettings` | `otus-mail` | Mail app composition, dependencies, destinations, or navigation. |
| [MailSidebar](../shared/src/commonMain/kotlin/com/otus/mail/app/MailSidebar.kt) | `MailSidebar`, `FolderEntry` | `otus-mail` | Mail destinations and folder tree; drawer and rows are shared Sidebar/SidebarEntry. |
| [MailStates](../shared/src/commonMain/kotlin/com/otus/mail/app/MailStates.kt) | `MailStates` | `otus-mail` | Mail app composition, dependencies, destinations, or navigation. |
| [ConditionEditor](../shared/src/commonMain/kotlin/com/otus/mail/feature/folders/presentation/components/ConditionEditor.kt) | `ConditionEditor` | `otus-mail` | Mail folders or Sieve rule behavior; uses shared controls. |
| [FolderActions](../shared/src/commonMain/kotlin/com/otus/mail/feature/folders/presentation/components/FolderActions.kt) | `FolderActions` | `otus-mail` | Mail folders or Sieve rule behavior; uses shared controls. |
| [FolderDialogs](../shared/src/commonMain/kotlin/com/otus/mail/feature/folders/presentation/components/FolderDialogs.kt) | `FolderDialogs`, `FolderChangeDialog`, `CreateFolderDialog`, `FolderActionsDialog` | `otus-mail` | Mail folders or Sieve rule behavior; uses shared controls. |
| [RuleEditorActions](../shared/src/commonMain/kotlin/com/otus/mail/feature/folders/presentation/components/RuleEditorActions.kt) | `RuleEditorActions` | `otus-mail` | Mail folders or Sieve rule behavior; uses shared controls. |
| [RuleEditorForm](../shared/src/commonMain/kotlin/com/otus/mail/feature/folders/presentation/components/RuleEditorForm.kt) | `RuleEditorForm`, `MatchChoice`, `FolderChoice` | `otus-mail` | Mail folders or Sieve rule behavior; uses shared controls. |
| [RulesActions](../shared/src/commonMain/kotlin/com/otus/mail/feature/folders/presentation/components/RulesActions.kt) | `RulesActions` | `otus-mail` | Mail folders or Sieve rule behavior; uses shared controls. |
| [RulesDialogs](../shared/src/commonMain/kotlin/com/otus/mail/feature/folders/presentation/components/RulesDialogs.kt) | `RulesDialogs`, `RuleActionsDialog`, `TurnOnDialog` | `otus-mail` | Mail folders or Sieve rule behavior; uses shared controls. |
| [FoldersScreen](../shared/src/commonMain/kotlin/com/otus/mail/feature/folders/presentation/screens/FoldersScreen.kt) | `FoldersScreen`, `AccountFolderSection` | `otus-mail` | Mail folders or Sieve rule behavior; uses shared controls. |
| [RuleEditorScreen](../shared/src/commonMain/kotlin/com/otus/mail/feature/folders/presentation/screens/RuleEditorScreen.kt) | `RuleEditorScreen` | `otus-mail` | Mail folders or Sieve rule behavior; uses shared controls. |
| [RulesScreen](../shared/src/commonMain/kotlin/com/otus/mail/feature/folders/presentation/screens/RulesScreen.kt) | `RulesScreen`, `AccountRulesContent`, `RunButton` | `otus-mail` | Mail folders or Sieve rule behavior; uses shared controls. |
| [DateFilterLabel](../shared/src/commonMain/kotlin/com/otus/mail/feature/inbox/presentation/components/DateFilterLabel.kt) | `DateFilterLabel` | `otus-mail` | Mail messages, searches, filters, or previews; uses shared controls. |
| [FilterSelection](../shared/src/commonMain/kotlin/com/otus/mail/feature/inbox/presentation/components/FilterSelection.kt) | `FilterSelection` | `otus-mail` | Senders and accounts behind the applied filters, passed to the search pills. |
| [InboxFilter](../shared/src/commonMain/kotlin/com/otus/mail/feature/inbox/presentation/components/InboxFilter.kt) | `InboxFilter` | `otus-mail` | Mail messages, searches, filters, or previews; uses shared controls. |
| [InboxFilterSheet](../shared/src/commonMain/kotlin/com/otus/mail/feature/inbox/presentation/components/InboxFilterSheet.kt) | `InboxFilterSheet` | `otus-mail` | Mail messages, searches, filters, or previews; uses shared controls. |
| [InboxPanel](../shared/src/commonMain/kotlin/com/otus/mail/feature/inbox/presentation/components/InboxPanel.kt) | `InboxPanel`, `InboxNavigation`, `InboxSearchInput` | `otus-mail` | Mail search actions and mode; panel drawing is shared ContextPanel. |
| [InboxPanelActions](../shared/src/commonMain/kotlin/com/otus/mail/feature/inbox/presentation/components/InboxPanelActions.kt) | `InboxPanelActions` | `otus-mail` | Mail messages, searches, filters, or previews; uses shared controls. |
| [InboxPanelChips](../shared/src/commonMain/kotlin/com/otus/mail/feature/inbox/presentation/components/InboxPanelChips.kt) | `InboxPanelChips` | `otus-mail` | Mail messages, searches, filters, or previews; uses shared controls. |
| [InboxPillChips](../shared/src/commonMain/kotlin/com/otus/mail/feature/inbox/presentation/components/InboxPillChips.kt) | `InboxRowPills`, `FolderPillChip` | `otus-mail` | The user's inbox pills; pill drawing is shared Chip. |
| [InboxPillsSheet](../shared/src/commonMain/kotlin/com/otus/mail/feature/inbox/presentation/components/InboxPillsSheet.kt) | `InboxPillsSheet`, `Caption` | `otus-mail` | Chooses and orders the inbox pills; uses shared `SelectionSheet`, `ReorderableColumn`, and `SelectionRow`. |
| [InboxPickerChoices](../shared/src/commonMain/kotlin/com/otus/mail/feature/inbox/presentation/components/InboxPickerChoices.kt) | `InboxPickerChoices`, `AccountAvatar`, `EmptyChoices`, `SectionCaption` | `otus-mail` | Mail messages, searches, filters, or previews; uses shared controls. |
| [MailBehaviorSettings](../shared/src/commonMain/kotlin/com/otus/mail/feature/inbox/presentation/components/MailBehaviorSettings.kt) | `MailBehaviorSettings` | `otus-mail` | Mail pill, swipe, reading, and Read later choices; uses shared rows and `ChoiceSheet`. |
| [MessageActionLabels](../shared/src/commonMain/kotlin/com/otus/mail/feature/inbox/presentation/components/MessageActionLabels.kt) | `swipeAction` | `otus-mail` | Mail adapter mapping message actions to Meta's `SwipeActions`. |
| [MessageActionRow](../shared/src/commonMain/kotlin/com/otus/mail/feature/inbox/presentation/components/MessageActionRow.kt) | `MessageActionRow` | `otus-mail` | Open-message actions as Material text buttons, like the preview dialog around them. |
| [MessagePreview](../shared/src/commonMain/kotlin/com/otus/mail/feature/inbox/presentation/components/MessagePreview.kt) | `MessagePreview` | `otus-mail` | The open message in a Material `AlertDialog`, with its actions above the body. |
| [SearchFilterChip](../shared/src/commonMain/kotlin/com/otus/mail/feature/inbox/presentation/components/SearchFilterChip.kt) | `SearchFilterChip` | `otus-mail` | Mail filter selection and labels; pill drawing is shared Chip/AvatarStackChip. |
| [InboxScreen](../shared/src/commonMain/kotlin/com/otus/mail/feature/inbox/presentation/screens/InboxScreen.kt) | `InboxScreen`, `InboxResults` | `otus-mail` | Mail messages, searches, filters, or previews; uses shared controls. |
| [DemoFilters](../shared/src/commonMain/kotlin/com/otus/mail/feature/welcome/presentation/components/DemoFilters.kt) | `DemoFilters` | `otus-mail` | Mail component playground; uses shared controls. |
| [DemoModePicker](../shared/src/commonMain/kotlin/com/otus/mail/feature/welcome/presentation/components/DemoModePicker.kt) | `DemoModePicker` | `otus-mail` | Mail component playground; uses shared controls. |
| [DemoScaffold](../shared/src/commonMain/kotlin/com/otus/mail/feature/welcome/presentation/components/DemoScaffold.kt) | `DemoScaffold` | `otus-mail` | Mail component playground; uses shared controls. |
| [PanelDemo](../shared/src/commonMain/kotlin/com/otus/mail/feature/welcome/presentation/components/PanelDemo.kt) | `PanelDemo` | `otus-mail` | Mail component playground; uses shared controls. |
| [PanelDemoAction](../shared/src/commonMain/kotlin/com/otus/mail/feature/welcome/presentation/components/PanelDemoAction.kt) | `PanelDemoAction` | `otus-mail` | Mail component playground; uses shared controls. |
| [PanelDemoContent](../shared/src/commonMain/kotlin/com/otus/mail/feature/welcome/presentation/components/PanelDemoContent.kt) | `PanelDemoContent` | `otus-mail` | Mail component playground; uses shared controls. |
| [WelcomeScreen](../shared/src/commonMain/kotlin/com/otus/mail/feature/welcome/presentation/screens/WelcomeScreen.kt) | `WelcomeScreen` | `otus-mail` | Mail component playground; uses shared controls. |

## Shared non UI libraries

- `jmap` owns transport, session capabilities, typed methods, and protected login storage.
- `accounts` owns address parsing, account contracts, verification, and connected-account state in addition to its UI listed above.
- `dateformat` owns formatting and app-private preference-store adapters.
- `avatar` owns photo/icon lookup, caches, domain resolution, and generated initials.
- Every visual token, Figtree, and all symbols are in otus-meta. Settings and accounts use the same public design-system resource bundle.

## Release state

Meta 0.6.0 is published to GitHub Packages from `v0.6.0`. Mail pins all six libraries to that
release and normally downloads its Maven artifacts. Local source substitution through
`otus.meta.path` remains optional.

## Central UI policy

All branded elements, reusable components, tokens, and symbols live in Meta. Mail retains
feature compositions and adapters. The central renderers use display values and callbacks,
without Mail domain dependencies. Whole Mail feature screens remain with their features.
