package com.otus.mail.app

import androidx.compose.runtime.Composable
import com.otus.mail.feature.inbox.presentation.components.MailBehaviorSettings
import com.otus.mail.feature.inbox.presentation.components.MailLookSettings
import com.otus.settings.presentation.components.AppIconSetting
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
            settings.appIcon?.let { AppIconSetting(it) }
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
