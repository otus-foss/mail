package com.otusfoss.mail.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.otusfoss.core.dateformat.DateFormatter
import com.otusfoss.core.designsystem.mail.brand.elements.MailAppIcon
import com.otusfoss.core.settings.presentation.components.PageRowSpec
import com.otusfoss.core.settings.presentation.components.SettingsSectionSpec
import com.otusfoss.core.settings.presentation.components.appIconRow
import com.otusfoss.core.settings.presentation.components.appearanceSections
import com.otusfoss.core.settings.presentation.screens.AppearanceSettingsScreen
import com.otusfoss.mail.core.mailbox.SharedFolder
import com.otusfoss.mail.core.mailbox.sharedFolders
import com.otusfoss.mail.feature.inbox.presentation.components.MailListPreview
import com.otusfoss.mail.feature.mailsettings.presentation.components.mailListRow
import com.otusfoss.mail.feature.mailsettings.presentation.screens.ActionBarSettingsScreen
import com.otusfoss.mail.feature.mailsettings.presentation.screens.AllowedSendersScreen
import com.otusfoss.mail.feature.mailsettings.presentation.screens.MailBehaviorScreen
import com.otusfoss.mail.feature.mailsettings.presentation.screens.MailListSettingsScreen
import com.otusfoss.mail.feature.mailsettings.presentation.screens.PrivacyScreen

/** Appearance, the shared page with Mail's app icon and mail list rows. */
@Composable
internal fun AppearancePage(
    states: MailStates,
    settings: SharedSettings,
    dateFormatter: DateFormatter,
    navigation: MailNavigation,
    back: () -> Unit,
) {
    AppearanceSettingsScreen(
        settings.mailAppearance(
            mailListRow(states.behavior, dateFormatter) { navigation.go(MailPage.MailListSettings) },
            openTheme = { navigation.go(MailPage.ThemeSettings) },
        ),
        onBack = back,
    )
}

/**
 * The Appearance page's sections, as the page shows them and search finds them, with Mail's App
 * icon and [mailList] rows.
 */
internal fun SharedSettings.mailAppearance(
    mailList: PageRowSpec,
    openTheme: (setting: String?) -> Unit,
): List<SettingsSectionSpec> =
    appearanceSections(
        dateFormat,
        theme,
        animation,
        openTheme,
        look = listOfNotNull(appIcon?.let { appIconRow(it) { style -> MailAppIcon(style) } }, mailList),
    )

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
        sidebarPinOptions(states.folders.all.accounts, states.folders.colors::groupColorOf),
        behaviorPages { page -> { navigation.go(page) } },
        onBack = back,
    )
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
            onPreset = states::applyAlertPreset,
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
        folderColor = { path -> shared.firstOrNull { it.path == path }?.let(states.folders.colors::groupColorOf) },
        onBack = back,
    )
}

/** Every account's own folders, nested ones included, for Move to buttons. */
@Composable
private fun rememberSharedFolders(states: MailStates): List<SharedFolder> =
    remember(states.folders.all.accounts) { sharedFolders(states.folders.all.accounts).flatMap { it.flatten() } }
