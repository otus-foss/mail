package com.otus.mail.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.otus.accounts.domain.AccountService
import com.otus.accounts.presentation.screens.AccountSetupScreen
import com.otus.dateformat.DateFormatter
import com.otus.designsystem.theme.colors.ThemeChoice
import com.otus.mail.feature.inbox.presentation.components.NotificationActionsSetting
import com.otus.mail.feature.inbox.presentation.screens.FilterPanelSettingsScreen
import com.otus.mail.feature.notifications.presentation.screens.NotificationOverridesScreen
import com.otus.mail.feature.notifications.presentation.screens.NotificationsScreen
import com.otus.mail.feature.theme.presentation.components.LowContrastWarning
import com.otus.mail.feature.theme.presentation.components.ThemeRampPicker
import com.otus.mail.feature.theme.presentation.lowContrast
import com.otus.settings.presentation.ThemeSettingsState
import com.otus.settings.presentation.screens.SettingsScreen
import com.otus.settings.presentation.screens.ThemeSettingsScreen

/** Settings and its subpages. */
@Composable
internal fun SettingsPageContent(
    page: MailPage,
    states: MailStates,
    settings: SharedSettings,
    dateFormatter: DateFormatter,
    navigation: MailNavigation,
    back: () -> Unit,
) {
    when (page) {
        MailPage.Settings -> SettingsIndex(states, settings, navigation)
        MailPage.EmailAccounts -> EmailAccountsPage(states, back)
        MailPage.Appearance -> AppearancePage(states, settings, navigation, back)
        MailPage.Behavior -> MailBehaviorPage(states, navigation, back)
        MailPage.Notifications, MailPage.NotificationOverrides -> NotificationPages(page, states, navigation, back)
        MailPage.SidebarSettings, MailPage.PinnedSettings -> SidebarSettingsPage(page, states, navigation, back)
        MailPage.ThemeSettings -> ThemeSettingsPage(settings.theme, back)
        MailPage.ActionBarSettings -> ActionBarSettingsPage(states, back)
        MailPage.FilterPanelSettings -> FilterPanelSettingsScreen(states.behavior, onBack = back)
        MailPage.MailListSettings -> MailListSettingsPage(states, dateFormatter, back)
        MailPage.Folders -> FoldersPage(states, navigation, back)
        is MailPage.FolderDetail -> FolderDetailPage(page, states, navigation, back)
        else -> Unit
    }
}

/**
 * The theme page with a warning while some of the user's colors are hard to read, and the picker
 * that makes a set of colors from one hue. The phone shows the picker's set until the theme changes.
 */
@Composable
private fun ThemeSettingsPage(
    theme: ThemeSettingsState,
    back: () -> Unit,
) {
    var trying by remember(theme.choice) { mutableStateOf<ThemeChoice?>(null) }
    ThemeSettingsScreen(theme, onBack = back, preview = (trying ?: theme.choice).palette) {
        LowContrastWarning(theme.choice.lowContrast())
        ThemeRampPicker(theme.choice, onTry = { trying = it }, onSelect = theme::select)
    }
}

@Composable
private fun SettingsIndex(
    states: MailStates,
    settings: SharedSettings,
    navigation: MailNavigation,
) {
    SettingsScreen(mailSettingsIndex(states, settings, navigation), onOpenNavigation = navigation.openSidebar)
}

@Composable
private fun EmailAccountsPage(
    states: MailStates,
    back: () -> Unit,
) {
    AccountSetupScreen(states.accounts, states.ownAvatars, "Email accounts", AccountService.Mail, onBack = back)
}

/** Notifications and its page of account and address previews; neither shows where pushes are unavailable. */
@Composable
private fun NotificationPages(
    page: MailPage,
    states: MailStates,
    navigation: MailNavigation,
    back: () -> Unit,
) {
    val notifications = states.notifications ?: return
    if (page == MailPage.NotificationOverrides) {
        NotificationOverridesScreen(notifications, states.accounts.accounts, onBack = back)
    } else {
        NotificationsScreen(
            notifications,
            states.accounts.accounts,
            onBack = back,
            buttons = { NotificationActionsSetting(states.behavior) },
        ) {
            navigation.go(MailPage.NotificationOverrides)
        }
    }
}
