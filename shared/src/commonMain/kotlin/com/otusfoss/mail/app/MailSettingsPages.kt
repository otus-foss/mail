package com.otusfoss.mail.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.otusfoss.core.accounts.presentation.screens.AccountSetupScreen
import com.otusfoss.core.avatar.ui.presentation.components.OwnAvatarEditor
import com.otusfoss.core.dateformat.DateFormatter
import com.otusfoss.core.settings.presentation.components.SettingSpecRow
import com.otusfoss.core.settings.presentation.screens.SettingsScreen
import com.otusfoss.mail.feature.mailsettings.presentation.components.alertButtonsRow
import com.otusfoss.mail.feature.mailsettings.presentation.components.updateToastRow
import com.otusfoss.mail.feature.mailsettings.presentation.screens.FilterPanelSettingsScreen
import com.otusfoss.mail.feature.notifications.presentation.screens.NotificationOverridesScreen
import com.otusfoss.mail.feature.notifications.presentation.screens.NotificationsScreen
import com.otusfoss.mail.feature.theme.presentation.screens.MailThemeScreen

/** Settings and its subpages. */
@Composable
internal fun SettingsPageContent(
    page: MailPage.SettingsPage,
    states: MailStates,
    settings: SharedSettings,
    dateFormatter: DateFormatter,
    navigation: MailNavigation,
    back: () -> Unit,
) {
    when (page) {
        MailPage.Settings -> SettingsIndex(states, settings, dateFormatter, navigation)
        MailPage.EmailAccounts -> EmailAccountsPage(states, back)
        MailPage.Appearance -> AppearancePage(states, settings, dateFormatter, navigation, back)
        MailPage.Behavior -> MailBehaviorPage(states, navigation, back)
        MailPage.Notifications, MailPage.NotificationOverrides -> NotificationPages(page, states, navigation, back)
        MailPage.SidebarSettings, MailPage.PinnedSettings -> SidebarSettingsPage(page, states, navigation, back)
        MailPage.ThemeSettings -> MailThemeScreen(settings.theme, onBack = back)
        MailPage.ActionBarSettings -> ActionBarSettingsPage(states, back)
        MailPage.FilterPanelSettings -> FilterPanelSettingsScreen(states.behavior, onBack = back)
        MailPage.MailListSettings -> MailListSettingsPage(states, dateFormatter, back)
        MailPage.Folders -> FoldersPage(states, navigation, back)
        is MailPage.FolderDetail -> FolderDetailPage(page, states, navigation, back)
    }
}

@Composable
private fun SettingsIndex(
    states: MailStates,
    settings: SharedSettings,
    dateFormatter: DateFormatter,
    navigation: MailNavigation,
) {
    SettingsScreen(
        mailSettingsIndex(states, settings, dateFormatter, navigation),
        onOpenNavigation = navigation.openSidebar,
    )
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
        EMAIL_ACCOUNTS_TITLE,
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
            buttons = { SettingSpecRow(alertButtonsRow(states.behavior)) },
            updates = { SettingSpecRow(updateToastRow(states.behavior)) },
        ) {
            navigation.go(MailPage.NotificationOverrides)
        }
    }
}

/** The accounts page's title, which the settings home and search also show. */
internal const val EMAIL_ACCOUNTS_TITLE = "Email accounts"
