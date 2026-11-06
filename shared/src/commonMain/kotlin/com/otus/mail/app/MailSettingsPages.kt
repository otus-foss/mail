package com.otus.mail.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.otus.accounts.presentation.screens.AccountSetupScreen
import com.otus.avatar.ui.presentation.components.OwnAvatarEditor
import com.otus.dateformat.DateFormatter
import com.otus.mail.feature.inbox.presentation.components.NotificationActionsSetting
import com.otus.mail.feature.inbox.presentation.components.ThemeInboxPreview
import com.otus.mail.feature.inbox.presentation.screens.FilterPanelSettingsScreen
import com.otus.mail.feature.notifications.presentation.screens.NotificationOverridesScreen
import com.otus.mail.feature.notifications.presentation.screens.NotificationsScreen
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
        MailPage.ThemeSettings -> ThemeSettingsScreen(settings.theme, onBack = back) { ThemeInboxPreview() }
        MailPage.ActionBarSettings -> ActionBarSettingsPage(states, back)
        MailPage.FilterPanelSettings -> FilterPanelSettingsScreen(states.behavior, onBack = back)
        MailPage.MailListSettings -> MailListSettingsPage(states, dateFormatter, back)
        MailPage.Folders -> FoldersPage(states, navigation, back)
        is MailPage.FolderDetail -> FolderDetailPage(page, states, navigation, back)
        else -> Unit
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
    val avatars = remember(states.ownAvatars) { MailAccountAvatars(states.ownAvatars) }
    AccountSetupScreen(
        states.accounts,
        avatars,
        { account, email, onDismiss -> OwnAvatarEditor(states.ownAvatars, account.own, email, onDismiss) },
        "Email accounts",
        MailAccountService,
        onBack = back,
    )
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
