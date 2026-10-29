package com.otus.mail

import androidx.activity.compose.setContent
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.compose.ui.test.onLast
import com.github.takahirom.roborazzi.captureRoboImage
import com.otus.accounts.domain.ConnectedAccount
import com.otus.designsystem.theme.OtusTheme
import com.otus.designsystem.theme.colors.ThemeBase
import com.otus.designsystem.theme.colors.ThemeChoice
import com.otus.mail.feature.notifications.domain.InstantPush
import com.otus.mail.feature.notifications.presentation.NotificationSettingsState
import com.otus.mail.feature.notifications.presentation.screens.NotificationsScreen

/** The Notifications page for Ada's one account, delivered through [push], in [theme]. */
internal fun AndroidComposeTestRule<*, MainActivity>.showNotifications(
    push: InstantPush,
    theme: ThemeBase,
) {
    runOnUiThread {
        activity.setContent {
            val scope = rememberCoroutineScope()
            val state = remember(push) { NotificationSettingsState(push, scope) }
            OtusTheme(ThemeChoice(base = theme).palette) {
                NotificationsScreen(
                    state,
                    listOf(ConnectedAccount("ada", "Ada", "ada@example.org", "example.org")),
                    onBack = {},
                    onOpenOverrides = {},
                )
            }
        }
    }
    waitForIdle()
}

/** Records the top window, such as an open sheet over the page, under `src/test/screenshots/notifications`. */
internal fun AndroidComposeTestRule<*, MainActivity>.captureNotifications(name: String) {
    onAllNodes(isRoot()).onLast().captureRoboImage("src/test/screenshots/notifications/$name.png")
}
