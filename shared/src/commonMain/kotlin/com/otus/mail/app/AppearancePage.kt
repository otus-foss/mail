package com.otus.mail.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.otus.mail.feature.folders.domain.SharedFolder
import com.otus.mail.feature.folders.domain.sharedFolders
import com.otus.mail.feature.inbox.presentation.components.MailLookSettings
import com.otus.mail.feature.inbox.presentation.screens.ActionBarSettingsScreen
import com.otus.mail.feature.inbox.presentation.screens.MailBehaviorScreen
import com.otus.settings.presentation.components.AppIconSetting
import com.otus.settings.presentation.screens.AppearanceSettingsScreen

/** Appearance, where Mail adds its sidebar row to the shared page. */
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
            settings.appIcon?.let { AppIconSetting(it) }
            MailLookSettings(states.behavior.behavior.sidebar) { navigation.go(MailPage.SidebarSettings) }
        },
    )
}

/** Mail behavior, whose Action bar row opens its own page. */
@Composable
internal fun MailBehaviorPage(
    states: MailStates,
    navigation: MailNavigation,
    back: () -> Unit,
) {
    MailBehaviorScreen(
        states.behavior,
        rememberFolderPills(states),
        onOpenActionBar = { navigation.go(MailPage.ActionBarSettings) },
        onBack = back,
    )
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
