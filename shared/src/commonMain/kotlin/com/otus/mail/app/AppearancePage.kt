package com.otus.mail.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.otus.mail.feature.folders.domain.SharedFolder
import com.otus.mail.feature.folders.domain.sharedFolders
import com.otus.mail.feature.inbox.presentation.components.MailBehaviorSettings
import com.otus.mail.feature.inbox.presentation.components.MailLookSettings
import com.otus.mail.feature.inbox.presentation.screens.ActionBarSettingsScreen
import com.otus.settings.presentation.screens.AppearanceSettingsScreen

/** Appearance & behavior, where Mail adds its look and behavior rows to the shared page. */
@Composable
internal fun AppearancePage(
    states: MailStates,
    settings: SharedSettings,
    navigation: MailNavigation,
    back: () -> Unit,
) {
    val folderPills = rememberFolderPills(states)
    AppearanceSettingsScreen(
        settings.dateFormat,
        settings.theme,
        settings.animation,
        onTheme = { navigation.go(MailPage.ThemeSettings) },
        onBack = back,
        look = {
            MailLookSettings(
                states.behavior,
                folderPills,
                onSidebar = { navigation.go(MailPage.SidebarSettings) },
            )
        },
    ) {
        MailBehaviorSettings(
            states.behavior,
            states.inboxContents,
            onOpenInbox = { navigation.go(MailPage.InboxSettings) },
            onOpenActionBar = { navigation.go(MailPage.ActionBarSettings) },
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
