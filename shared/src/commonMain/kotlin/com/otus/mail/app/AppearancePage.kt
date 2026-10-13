package com.otus.mail.app

import androidx.compose.runtime.Composable
import com.otus.mail.feature.inbox.presentation.components.MailBehaviorSettings
import com.otus.mail.feature.inbox.presentation.components.MailLookSettings
import com.otus.settings.presentation.DateFormatSettingsState
import com.otus.settings.presentation.ThemeSettingsState
import com.otus.settings.presentation.screens.AppearanceSettingsScreen

/** Appearance & behavior, where Mail adds its look and behavior rows to the shared page. */
@Composable
internal fun AppearancePage(
    states: MailStates,
    dateFormatState: DateFormatSettingsState,
    themeState: ThemeSettingsState,
    navigation: MailNavigation,
    back: () -> Unit,
) {
    val folderPills = rememberFolderPills(states)
    AppearanceSettingsScreen(
        dateFormatState,
        themeState,
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
        MailBehaviorSettings(states.behavior, onOpenActionBar = { navigation.go(MailPage.ActionBarSettings) })
    }
}
