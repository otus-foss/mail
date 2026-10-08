# Mail component inventory

This inventory covers every production Otus composable source file in Mail or its shared UI libraries, plus selected supporting files. It includes public components, private UI helpers, UI state/value types, and the Mail playground. Compose framework widgets, generated resources, and test-only fixtures are excluded. Platform implementations of shared avatar decoding remain in otus-meta and implement the same common API.

"Shared now" means implemented in the unreleased 0.3.0 extraction and consumed by this local Mail checkout. "Already shared" means present in the released 0.2.1 shared library. "Mail adapter" means the rendering is shared while Mail maps feature data into it. "Mail" means the app still owns the composition or behavior. Calendar is unchanged.

These choices are reviewable. Keeping a Mail composition does not keep a second copy of its shared panel, pill, sidebar, or setting controls.

## Shared now

| File | UI declarations or supporting file | Owner | Reason |
| --- | --- | --- | --- |
| [AccountsState](../../otus-meta/accounts/src/commonMain/kotlin/com/otus/accounts/presentation/AccountsState.kt) | `AccountsState` | `otus-meta/accounts` | Shared account setup page, connected account row, or screen state. |
| [ConnectMessages](../../otus-meta/accounts/src/commonMain/kotlin/com/otus/accounts/presentation/ConnectMessages.kt) | `ConnectMessages` | `otus-meta/accounts` | Shared account setup page, connected account row, or screen state. |
| [ConnectedAccountRow](../../otus-meta/accounts/src/commonMain/kotlin/com/otus/accounts/presentation/components/ConnectedAccountRow.kt) | `ConnectedAccountRow` | `otus-meta/accounts` | Shared account setup page, connected account row, or screen state. |
| [AccountSetupScreen](../../otus-meta/accounts/src/commonMain/kotlin/com/otus/accounts/presentation/screens/AccountSetupScreen.kt) | `AccountSetupScreen`, `AddAccountForm`, `ConfirmHostPrompt` | `otus-meta/accounts` | Shared account setup page, connected account row, or screen state. |
| [AvatarStackChip](../../otus-meta/designsystem/src/commonMain/kotlin/com/otus/designsystem/components/AvatarStackChip.kt) | `AvatarStackChip`, `StackedAvatar` | `otus-meta/designsystem` | Reusable visual element, component, theme, or UI support. |
| [CalendarDragTargets](../../otus-meta/designsystem/src/commonMain/kotlin/com/otus/designsystem/components/CalendarDragTargets.kt) | `CalendarDragTargets` | `otus-meta/designsystem` | Reusable visual element, component, theme, or UI support. |
| [CalendarHeading](../../otus-meta/designsystem/src/commonMain/kotlin/com/otus/designsystem/components/CalendarHeading.kt) | `CalendarHeading` | `otus-meta/designsystem` | Reusable visual element, component, theme, or UI support. |
| [CalendarRangeGesture](../../otus-meta/designsystem/src/commonMain/kotlin/com/otus/designsystem/components/CalendarRangeGesture.kt) | `calendarRangeGesture` | `otus-meta/designsystem` | Reusable visual element, component, theme, or UI support. |
| [CalendarWeek](../../otus-meta/designsystem/src/commonMain/kotlin/com/otus/designsystem/components/CalendarWeek.kt) | `CalendarWeek`, `CalendarDateCell` | `otus-meta/designsystem` | Reusable visual element, component, theme, or UI support. |
| [CalendarWeeks](../../otus-meta/designsystem/src/commonMain/kotlin/com/otus/designsystem/components/CalendarWeeks.kt) | `CalendarWeeks` | `otus-meta/designsystem` | Reusable visual element, component, theme, or UI support. |
| [ChipAvatar](../../otus-meta/designsystem/src/commonMain/kotlin/com/otus/designsystem/components/ChipAvatar.kt) | `ChipAvatar` | `otus-meta/designsystem` | Reusable visual element, component, theme, or UI support. |
| [ContentRow](../../otus-meta/designsystem/src/commonMain/kotlin/com/otus/designsystem/components/ContentRow.kt) | `ContentRow` | `otus-meta/designsystem` | Central renderer accepting display values, content slots, and callbacks. |
| [ContinuousCalendar](../../otus-meta/designsystem/src/commonMain/kotlin/com/otus/designsystem/components/ContinuousCalendar.kt) | `ContinuousCalendar`, `VisibleMonthHeading`, `CalendarWeekdays` | `otus-meta/designsystem` | Reusable visual element, component, theme, or UI support. |
| [DateRangePicker](../../otus-meta/designsystem/src/commonMain/kotlin/com/otus/designsystem/components/DateRangePicker.kt) | `DateRangePicker`, `DateRangeSummary`, `DateRangeShortcuts` | `otus-meta/designsystem` | Reusable visual element, component, theme, or UI support. |
| [DetailRow](../../otus-meta/designsystem/src/commonMain/kotlin/com/otus/designsystem/components/DetailRow.kt) | `DetailRow` | `otus-meta/designsystem` | Central renderer accepting display values, content slots, and callbacks. |
| [EmptyState](../../otus-meta/designsystem/src/commonMain/kotlin/com/otus/designsystem/components/EmptyState.kt) | `EmptyState` | `otus-meta/designsystem` | Central renderer accepting display values, content slots, and callbacks. |
| [InboxTokens](../../otus-meta/designsystem/src/commonMain/kotlin/com/otus/designsystem/theme/InboxTokens.kt) | `InboxTokens` | `otus-meta/designsystem` | Central message-list and empty-state visual tokens. |
| [DateFormatExamples](../../otus-meta/settings/src/commonMain/kotlin/com/otus/settings/presentation/DateFormatExamples.kt) | `DateFormatExamples` | `otus-meta/settings` | Shared settings list, appearance page, preference state, or examples. |
| [DateFormatSettingsState](../../otus-meta/settings/src/commonMain/kotlin/com/otus/settings/presentation/DateFormatSettingsState.kt) | `DateFormatSettingsState` | `otus-meta/settings` | Shared settings list, appearance page, preference state, or examples. |
| [SettingsEntry](../../otus-meta/settings/src/commonMain/kotlin/com/otus/settings/presentation/SettingsEntry.kt) | `SettingsEntry` | `otus-meta/settings` | Shared settings list, appearance page, preference state, or examples. |
| [AppearanceSettingsScreen](../../otus-meta/settings/src/commonMain/kotlin/com/otus/settings/presentation/screens/AppearanceSettingsScreen.kt) | `AppearanceSettingsScreen` | `otus-meta/settings` | Shared settings list, appearance page, preference state, or examples. |
| [SettingsScreen](../../otus-meta/settings/src/commonMain/kotlin/com/otus/settings/presentation/screens/SettingsScreen.kt) | `SettingsScreen` | `otus-meta/settings` | Shared settings list, appearance page, preference state, or examples. |
| [ContentRowText](../../otus-meta/designsystem/src/commonMain/kotlin/com/otus/designsystem/components/ContentRowText.kt) | `ContentRowText` | `otus-meta/designsystem` | Caller-prepared display text for ContentRow. |

## Already shared

| File | UI declarations or supporting file | Owner | Reason |
| --- | --- | --- | --- |
| [ActionDialog](../../otus-meta/designsystem/src/commonMain/kotlin/com/otus/designsystem/components/ActionDialog.kt) | `ActionDialog`, `InputDialog` | `otus-meta/designsystem` | Reusable visual element, component, theme, or UI support. |
| [ChoiceDialog](../../otus-meta/designsystem/src/commonMain/kotlin/com/otus/designsystem/components/ChoiceDialog.kt) | `ChoiceDialog` | `otus-meta/designsystem` | Reusable visual element, component, theme, or UI support. |
| [ContextPanel](../../otus-meta/designsystem/src/commonMain/kotlin/com/otus/designsystem/components/ContextPanel.kt) | `ContextPanel`, `PanelHeader` | `otus-meta/designsystem` | Reusable visual element, component, theme, or UI support. |
| [ContextPanelScope](../../otus-meta/designsystem/src/commonMain/kotlin/com/otus/designsystem/components/ContextPanelScope.kt) | `item`, `transition`, `PanelChipRail` | `otus-meta/designsystem` | Reusable visual element, component, theme, or UI support. |
| [DatePickerDialog](../../otus-meta/designsystem/src/commonMain/kotlin/com/otus/designsystem/components/DatePickerDialog.kt) | `DatePickerDialog` | `otus-meta/designsystem` | Reusable visual element, component, theme, or UI support. |
| [DialogAction](../../otus-meta/designsystem/src/commonMain/kotlin/com/otus/designsystem/components/DialogAction.kt) | `DialogAction` | `otus-meta/designsystem` | Reusable visual element, component, theme, or UI support. |
| [DialogFrame](../../otus-meta/designsystem/src/commonMain/kotlin/com/otus/designsystem/components/DialogFrame.kt) | `DialogFrame`, `DialogHeading` | `otus-meta/designsystem` | Reusable visual element, component, theme, or UI support. |
| [MonthNavigation](../../otus-meta/designsystem/src/commonMain/kotlin/com/otus/designsystem/components/MonthNavigation.kt) | `MonthNavigation` | `otus-meta/designsystem` | Reusable visual element, component, theme, or UI support. |
| [MonthNavigationLabel](../../otus-meta/designsystem/src/commonMain/kotlin/com/otus/designsystem/components/MonthNavigationLabel.kt) | `MonthNavigationLabel` | `otus-meta/designsystem` | Reusable visual element, component, theme, or UI support. |
| [MonthYearPicker](../../otus-meta/designsystem/src/commonMain/kotlin/com/otus/designsystem/components/MonthYearPicker.kt) | `MonthYearPicker`, `MonthYearColumn`, `MonthYearActions`, `MonthYearTitle` | `otus-meta/designsystem` | Reusable visual element, component, theme, or UI support. |
| [PageScaffold](../../otus-meta/designsystem/src/commonMain/kotlin/com/otus/designsystem/components/PageScaffold.kt) | `PageScaffold`, `Subpage`, `PageSection`, `PageNote` | `otus-meta/designsystem` | Reusable visual element, component, theme, or UI support. |
| [PanelLayoutState](../../otus-meta/designsystem/src/commonMain/kotlin/com/otus/designsystem/components/PanelLayoutState.kt) | `PanelLayoutState` | `otus-meta/designsystem` | Reusable visual element, component, theme, or UI support. |
| [PanelSurface](../../otus-meta/designsystem/src/commonMain/kotlin/com/otus/designsystem/components/PanelSurface.kt) | `PanelSurface` | `otus-meta/designsystem` | Reusable visual element, component, theme, or UI support. |
| [PickerSearch](../../otus-meta/designsystem/src/commonMain/kotlin/com/otus/designsystem/components/PickerSearch.kt) | `PickerSearch` | `otus-meta/designsystem` | Reusable visual element, component, theme, or UI support. |
| [PullToRefresh](../../otus-meta/designsystem/src/commonMain/kotlin/com/otus/designsystem/components/PullToRefresh.kt) | `PullToRefresh` | `otus-meta/designsystem` | Reusable visual element, component, theme, or UI support. |
| [SelectionSheet](../../otus-meta/designsystem/src/commonMain/kotlin/com/otus/designsystem/components/SelectionSheet.kt) | `SelectionSheet`, `rememberRepeatedClear`, `SheetHeading` | `otus-meta/designsystem` | Reusable visual element, component, theme, or UI support. |
| [Sidebar](../../otus-meta/designsystem/src/commonMain/kotlin/com/otus/designsystem/components/Sidebar.kt) | `Sidebar`, `SidebarSurface` | `otus-meta/designsystem` | Reusable visual element, component, theme, or UI support. |
| [SidebarState](../../otus-meta/designsystem/src/commonMain/kotlin/com/otus/designsystem/components/SidebarState.kt) | `rememberSidebarState` | `otus-meta/designsystem` | Reusable visual element, component, theme, or UI support. |
| [SidebarSwipeArea](../../otus-meta/designsystem/src/commonMain/kotlin/com/otus/designsystem/components/SidebarSwipeArea.kt) | `SidebarSwipeArea`, `sidebarOpenZone` | `otus-meta/designsystem` | Reusable visual element, component, theme, or UI support. |
| [ActionButton](../../otus-meta/designsystem/src/commonMain/kotlin/com/otus/designsystem/elements/ActionButton.kt) | `ActionButton` | `otus-meta/designsystem` | Reusable visual element, component, theme, or UI support. |
| [ActionRow](../../otus-meta/designsystem/src/commonMain/kotlin/com/otus/designsystem/elements/ActionRow.kt) | `ActionRow` | `otus-meta/designsystem` | Reusable visual element, component, theme, or UI support. |
| [AnimatedText](../../otus-meta/designsystem/src/commonMain/kotlin/com/otus/designsystem/elements/AnimatedText.kt) | `AnimatedText` | `otus-meta/designsystem` | Reusable visual element, component, theme, or UI support. |
| [Avatar](../../otus-meta/designsystem/src/commonMain/kotlin/com/otus/designsystem/elements/Avatar.kt) | `Avatar`, `initialsStyle` | `otus-meta/designsystem` | Reusable visual element, component, theme, or UI support. |
| [AvatarImage](../../otus-meta/designsystem/src/commonMain/kotlin/com/otus/designsystem/elements/AvatarImage.kt) | `rememberAvatarImage`, `rememberAvatarImage` | `otus-meta/designsystem` | Reusable visual element, component, theme, or UI support. |
| [CalendarDay](../../otus-meta/designsystem/src/commonMain/kotlin/com/otus/designsystem/elements/CalendarDay.kt) | `CalendarDay`, `CalendarMonthLabel` | `otus-meta/designsystem` | Reusable visual element, component, theme, or UI support. |
| [CalendarMonthBackground](../../otus-meta/designsystem/src/commonMain/kotlin/com/otus/designsystem/elements/CalendarMonthBackground.kt) | `CalendarMonthBackground` | `otus-meta/designsystem` | Reusable visual element, component, theme, or UI support. |
| [Chip](../../otus-meta/designsystem/src/commonMain/kotlin/com/otus/designsystem/elements/Chip.kt) | `Chip` | `otus-meta/designsystem` | Reusable visual element, component, theme, or UI support. |
| [ControlPressFeedback](../../otus-meta/designsystem/src/commonMain/kotlin/com/otus/designsystem/elements/ControlPressFeedback.kt) | `controlPressFeedback` | `otus-meta/designsystem` | Reusable visual element, component, theme, or UI support. |
| [ControlVisualBounds](../../otus-meta/designsystem/src/commonMain/kotlin/com/otus/designsystem/elements/ControlVisualBounds.kt) | `reportControlVisualBounds` | `otus-meta/designsystem` | Reusable visual element, component, theme, or UI support. |
| [DecodedAvatarCache](../../otus-meta/designsystem/src/commonMain/kotlin/com/otus/designsystem/elements/DecodedAvatarCache.kt) | `DecodedAvatarCache` | `otus-meta/designsystem` | Reusable visual element, component, theme, or UI support. |
| [IconButton](../../otus-meta/designsystem/src/commonMain/kotlin/com/otus/designsystem/elements/IconButton.kt) | `IconButton` | `otus-meta/designsystem` | Reusable visual element, component, theme, or UI support. |
| [InputField](../../otus-meta/designsystem/src/commonMain/kotlin/com/otus/designsystem/elements/InputField.kt) | `InputField` | `otus-meta/designsystem` | Reusable visual element, component, theme, or UI support. |
| [InputKind](../../otus-meta/designsystem/src/commonMain/kotlin/com/otus/designsystem/elements/InputKind.kt) | `InputKind` | `otus-meta/designsystem` | Reusable visual element, component, theme, or UI support. |
| [RefreshIndicator](../../otus-meta/designsystem/src/commonMain/kotlin/com/otus/designsystem/elements/RefreshIndicator.kt) | `RefreshIndicator`, `PullArc` | `otus-meta/designsystem` | Reusable visual element, component, theme, or UI support. |
| [SearchField](../../otus-meta/designsystem/src/commonMain/kotlin/com/otus/designsystem/elements/SearchField.kt) | `SearchField` | `otus-meta/designsystem` | Reusable visual element, component, theme, or UI support. |
| [SearchPrompt](../../otus-meta/designsystem/src/commonMain/kotlin/com/otus/designsystem/elements/SearchPrompt.kt) | `SearchPrompt` | `otus-meta/designsystem` | Reusable visual element, component, theme, or UI support. |
| [SelectionRow](../../otus-meta/designsystem/src/commonMain/kotlin/com/otus/designsystem/elements/SelectionRow.kt) | `SelectionRow`, `SelectionCheckmark`, `SelectionLabel` | `otus-meta/designsystem` | Reusable visual element, component, theme, or UI support. |
| [SidebarEntry](../../otus-meta/designsystem/src/commonMain/kotlin/com/otus/designsystem/elements/SidebarEntry.kt) | `SidebarEntry`, `SidebarHeading` | `otus-meta/designsystem` | Reusable visual element, component, theme, or UI support. |
| [Symbol](../../otus-meta/designsystem/src/commonMain/kotlin/com/otus/designsystem/elements/Symbol.kt) | `Symbol` | `otus-meta/designsystem` | Reusable visual element, component, theme, or UI support. |
| [AvatarTokens](../../otus-meta/designsystem/src/commonMain/kotlin/com/otus/designsystem/theme/AvatarTokens.kt) | `AvatarTokens` | `otus-meta/designsystem` | Reusable visual element, component, theme, or UI support. |
| [CalendarTokens](../../otus-meta/designsystem/src/commonMain/kotlin/com/otus/designsystem/theme/CalendarTokens.kt) | `CalendarTokens` | `otus-meta/designsystem` | Reusable visual element, component, theme, or UI support. |
| [ControlFeedbackTokens](../../otus-meta/designsystem/src/commonMain/kotlin/com/otus/designsystem/theme/ControlFeedbackTokens.kt) | `ControlFeedbackTokens` | `otus-meta/designsystem` | Reusable visual element, component, theme, or UI support. |
| [MotionTokens](../../otus-meta/designsystem/src/commonMain/kotlin/com/otus/designsystem/theme/MotionTokens.kt) | `MotionTokens` | `otus-meta/designsystem` | Reusable visual element, component, theme, or UI support. |
| [OtusColors](../../otus-meta/designsystem/src/commonMain/kotlin/com/otus/designsystem/theme/OtusColors.kt) | `OtusColors` | `otus-meta/designsystem` | Reusable visual element, component, theme, or UI support. |
| [OtusTheme](../../otus-meta/designsystem/src/commonMain/kotlin/com/otus/designsystem/theme/OtusTheme.kt) | `OtusTheme` | `otus-meta/designsystem` | Reusable visual element, component, theme, or UI support. |
| [PageTokens](../../otus-meta/designsystem/src/commonMain/kotlin/com/otus/designsystem/theme/PageTokens.kt) | `PageTokens` | `otus-meta/designsystem` | Reusable visual element, component, theme, or UI support. |
| [PanelTokens](../../otus-meta/designsystem/src/commonMain/kotlin/com/otus/designsystem/theme/PanelTokens.kt) | `PanelTokens` | `otus-meta/designsystem` | Reusable visual element, component, theme, or UI support. |
| [PanelTypography](../../otus-meta/designsystem/src/commonMain/kotlin/com/otus/designsystem/theme/PanelTypography.kt) | `panelTextStyle` | `otus-meta/designsystem` | Reusable visual element, component, theme, or UI support. |
| [RefreshTokens](../../otus-meta/designsystem/src/commonMain/kotlin/com/otus/designsystem/theme/RefreshTokens.kt) | `RefreshTokens` | `otus-meta/designsystem` | Reusable visual element, component, theme, or UI support. |
| [SheetTokens](../../otus-meta/designsystem/src/commonMain/kotlin/com/otus/designsystem/theme/SheetTokens.kt) | `SheetTokens` | `otus-meta/designsystem` | Reusable visual element, component, theme, or UI support. |
| [SidebarTokens](../../otus-meta/designsystem/src/commonMain/kotlin/com/otus/designsystem/theme/SidebarTokens.kt) | `SidebarTokens` | `otus-meta/designsystem` | Reusable visual element, component, theme, or UI support. |

## Mail adapter

| File | UI declarations or supporting file | Owner | Reason |
| --- | --- | --- | --- |
| [FolderRow](../shared/src/commonMain/kotlin/com/otus/mail/feature/folders/presentation/components/FolderRow.kt) | `FolderRow` | `otus-mail` | Maps folder counts and tree depth into shared DetailRow. |
| [RuleRow](../shared/src/commonMain/kotlin/com/otus/mail/feature/folders/presentation/components/RuleRow.kt) | `RuleRow` | `otus-mail` | Maps rule labels and enabled state into shared DetailRow. |
| [InboxBody](../shared/src/commonMain/kotlin/com/otus/mail/feature/inbox/presentation/components/InboxBody.kt) | `InboxBody`, `InboxEmptyState` | `otus-mail` | Composes the message list and supplies feature text/actions to shared EmptyState. |
| [MessageRow](../shared/src/commonMain/kotlin/com/otus/mail/feature/inbox/presentation/components/MessageRow.kt) | `MessageRow` | `otus-mail` | Maps message labels and unread state into shared ContentRow. |
| [PeopleFilterChip](../shared/src/commonMain/kotlin/com/otus/mail/feature/inbox/presentation/components/PeopleFilterChip.kt) | `PeopleFilterChip` | `otus-mail` | Maps Mail senders and labels into shared AvatarStackChip. |
| [SenderAvatar](../shared/src/commonMain/kotlin/com/otus/mail/feature/inbox/presentation/components/SenderAvatar.kt) | `SenderAvatar` | `otus-mail` | Maps Mail sender data into the shared Avatar element. |

## Mail

| File | UI declarations or supporting file | Owner | Reason |
| --- | --- | --- | --- |
| [App](../shared/src/commonMain/kotlin/com/otus/mail/app/App.kt) | `App` | `otus-mail` | Mail app composition, dependencies, destinations, or navigation. |
| [DateFormatting](../shared/src/commonMain/kotlin/com/otus/mail/app/DateFormatting.kt) | `rememberDateFormatter` | `otus-mail` | Mail app composition, dependencies, destinations, or navigation. |
| [MailNavigation](../shared/src/commonMain/kotlin/com/otus/mail/app/MailNavigation.kt) | `MailNavigation` | `otus-mail` | Mail app composition, dependencies, destinations, or navigation. |
| [MailPage](../shared/src/commonMain/kotlin/com/otus/mail/app/MailPage.kt) | `MailPage` | `otus-mail` | Mail app composition, dependencies, destinations, or navigation. |
| [MailPages](../shared/src/commonMain/kotlin/com/otus/mail/app/MailPages.kt) | `MailPageContent`, `FolderPage`, `rememberMessageActions` | `otus-mail` | Mail app composition, dependencies, destinations, or navigation. |
| [MailServices](../shared/src/commonMain/kotlin/com/otus/mail/app/MailServices.kt) | `MailServices` | `otus-mail` | Mail app composition, dependencies, destinations, or navigation. |
| [MailSettings](../shared/src/commonMain/kotlin/com/otus/mail/app/MailSettings.kt) | `MailSettings` | `otus-mail` | Mail app composition, dependencies, destinations, or navigation. |
| [MailSidebar](../shared/src/commonMain/kotlin/com/otus/mail/app/MailSidebar.kt) | `MailSidebar` | `otus-mail` | Mail destinations and folder tree; drawer and rows are shared Sidebar/SidebarEntry. |
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
| [InboxFilter](../shared/src/commonMain/kotlin/com/otus/mail/feature/inbox/presentation/components/InboxFilter.kt) | `InboxFilter` | `otus-mail` | Mail messages, searches, filters, or previews; uses shared controls. |
| [InboxFilterSheet](../shared/src/commonMain/kotlin/com/otus/mail/feature/inbox/presentation/components/InboxFilterSheet.kt) | `InboxFilterSheet` | `otus-mail` | Mail messages, searches, filters, or previews; uses shared controls. |
| [InboxPanel](../shared/src/commonMain/kotlin/com/otus/mail/feature/inbox/presentation/components/InboxPanel.kt) | `InboxPanel`, `InboxNavigation`, `InboxSearchInput` | `otus-mail` | Mail search actions and mode; panel drawing is shared ContextPanel. |
| [InboxPanelActions](../shared/src/commonMain/kotlin/com/otus/mail/feature/inbox/presentation/components/InboxPanelActions.kt) | `InboxPanelActions` | `otus-mail` | Mail messages, searches, filters, or previews; uses shared controls. |
| [InboxPanelChips](../shared/src/commonMain/kotlin/com/otus/mail/feature/inbox/presentation/components/InboxPanelChips.kt) | `InboxPanelChips` | `otus-mail` | Mail messages, searches, filters, or previews; uses shared controls. |
| [InboxPickerChoices](../shared/src/commonMain/kotlin/com/otus/mail/feature/inbox/presentation/components/InboxPickerChoices.kt) | `InboxPickerChoices`, `AccountAvatar`, `EmptyChoices` | `otus-mail` | Mail messages, searches, filters, or previews; uses shared controls. |
| [MessagePreview](../shared/src/commonMain/kotlin/com/otus/mail/feature/inbox/presentation/components/MessagePreview.kt) | `MessagePreview` | `otus-mail` | Mail messages, searches, filters, or previews; uses shared controls. |
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

The new `accounts` and `settings` modules and picker/pill additions are prepared for version 0.3.0. They are not published yet. The app version catalog targets 0.3.0 in this working branch; local development uses `otus.meta.path`. Publish the libraries and verify their released artifacts before merging the Mail version bump into main.

## Central UI policy

All branded elements, reusable components, tokens, and symbols live in Meta. Mail retains
feature compositions and adapters. The central renderers use display values and callbacks,
without Mail domain dependencies. Whole Mail feature screens remain with their features.
