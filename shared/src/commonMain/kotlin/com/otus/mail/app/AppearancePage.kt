package com.otus.mail.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.otus.dateformat.DateFormatter
import com.otus.designsystem.mail.brand.elements.MailAppIcon
import com.otus.mail.core.mailbox.SharedFolder
import com.otus.mail.core.mailbox.sharedFolders
import com.otus.mail.feature.inbox.presentation.components.MailListPreview
import com.otus.mail.feature.mailsettings.presentation.components.MailLookSettings
import com.otus.mail.feature.mailsettings.presentation.screens.ActionBarSettingsScreen
import com.otus.mail.feature.mailsettings.presentation.screens.AllowedSendersScreen
import com.otus.mail.feature.mailsettings.presentation.screens.BehaviorPage
import com.otus.mail.feature.mailsettings.presentation.screens.MailBehaviorScreen
import com.otus.mail.feature.mailsettings.presentation.screens.MailListSettingsScreen
import com.otus.mail.feature.mailsettings.presentation.screens.PrivacyScreen
import com.otus.settings.presentation.components.AppIconSetting
import com.otus.settings.presentation.screens.AppearanceSettingsScreen

/** Appearance, where Mail adds its app icon and mail list rows to the shared page. */
@Composable
internal fun AppearancePage(
    states: MailStates,
    settings: SharedSettings,
    navigation: MailNavigation,
    back: () -> Unit,
) {
    AppearanceSettingsScreen(
        settings.dateFormat,
        settings.theme,
        settings.animation,
        onTheme = { navigation.go(MailPage.ThemeSettings) },
        onBack = back,
        look = {
            settings.appIcon?.let { AppIconSetting(it) { style -> MailAppIcon(style) } }
            MailLookSettings(states.behavior.behavior) { navigation.go(MailPage.MailListSettings) }
        },
    )
}

/** How the mail list's rows read. Its preview stays empty until the list's row can draw a sample. */
@Composable
internal fun MailListSettingsPage(
    states: MailStates,
    dateFormatter: DateFormatter,
    back: () -> Unit,
) {
    MailListSettingsScreen(
        states.behavior,
        dateFormatter,
        preview = { MailListPreview(it, dateFormatter) },
        onBack = back,
    )
}

/** Mail behavior, whose Sidebar, Filter panel, Action bar, and Signatures rows open their own pages. */
@Composable
internal fun MailBehaviorPage(
    states: MailStates,
    navigation: MailNavigation,
    back: () -> Unit,
) {
    MailBehaviorScreen(
        states.behavior,
        rememberFolderPills(states),
        // Not remembered: folder colors change apart from the accounts.
        sidebarPinOptions(states.folders.accounts, states.folderColors::groupColorOf),
        open = { page -> navigation.go(page.mailPage) },
        onBack = back,
    )
}

private val BehaviorPage.mailPage: MailPage
    get() =
        when (this) {
            BehaviorPage.Sidebar -> MailPage.SidebarSettings
            BehaviorPage.FilterPanel -> MailPage.FilterPanelSettings
            BehaviorPage.ActionBar -> MailPage.ActionBarSettings
            BehaviorPage.Signatures -> MailPage.Signatures
        }

/** Settings › Privacy & security, or its Allowed senders page. */
@Composable
internal fun PrivacyPage(
    page: MailPage,
    states: MailStates,
    navigation: MailNavigation,
    back: () -> Unit,
) {
    if (page == MailPage.AllowedSenders) {
        AllowedSendersScreen(states.behavior, onBack = back)
    } else {
        PrivacyScreen(
            states.behavior,
            onOpenAllowed = { navigation.go(MailPage.AllowedSenders) },
            onBack = back,
            device = states.devicePrivacy,
        )
    }
}

/** The action bar's page, whose Move to buttons show each shared folder's color. */
@Composable
internal fun ActionBarSettingsPage(
    states: MailStates,
    back: () -> Unit,
) {
    val shared = rememberSharedFolders(states)
    ActionBarSettingsScreen(
        states.behavior,
        shared.map { it.path },
        folderColor = { path -> shared.firstOrNull { it.path == path }?.let(states.folderColors::groupColorOf) },
        onBack = back,
    )
}

/** Every account's own folders, nested ones included, for Move to buttons. */
@Composable
private fun rememberSharedFolders(states: MailStates): List<SharedFolder> =
    remember(states.folders.accounts) { sharedFolders(states.folders.accounts).flatMap { it.flatten() } }
