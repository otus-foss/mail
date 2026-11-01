package com.otus.mail.app

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import com.otus.designsystem.resources.Res
import com.otus.designsystem.resources.symbol_alternate_email
import com.otus.designsystem.resources.symbol_folder
import com.otus.designsystem.resources.symbol_notifications
import com.otus.designsystem.resources.symbol_palette
import com.otus.designsystem.resources.symbol_signature
import com.otus.designsystem.resources.symbol_tune
import com.otus.designsystem.resources.symbol_visibility_off
import com.otus.mail.feature.inbox.presentation.components.mailBehaviorIndex
import com.otus.mail.feature.inbox.presentation.components.mailLookIndex
import com.otus.mail.feature.inbox.presentation.components.privacyIndex
import com.otus.mail.feature.notifications.presentation.NotificationSettingsState
import com.otus.mail.feature.notifications.presentation.screens.deliveryLabel
import com.otus.settings.presentation.SettingsIndexScope
import com.otus.settings.presentation.SettingsItem
import com.otus.settings.presentation.screens.appIconIndex
import com.otus.settings.presentation.screens.appearanceIndex
import com.otus.settings.presentation.settingsIndex

/**
 * Every Mail settings page and setting that search finds; the settings home lists the top-level
 * pages. Each result opens its page through [navigation] with the setting found.
 */
@Composable
internal fun mailSettingsIndex(
    states: MailStates,
    settings: SharedSettings,
    navigation: MailNavigation,
): List<SettingsItem> {
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val folderPills = rememberFolderPills(states)
    val behavior = states.behavior.behavior
    val open = navigation::finder
    return settingsIndex {
        page(
            "Email accounts",
            open(MailPage.EmailAccounts),
            Res.drawable.symbol_alternate_email,
            summary = accountsSummary(states.accounts.accounts.size),
        )
        states.notifications?.let { notifications(it, open) }
        page(
            "Appearance",
            open(MailPage.Appearance),
            Res.drawable.symbol_palette,
            summary = "Theme, sidebar, dates, and animations",
        ) {
            appearanceIndex(settings.dateFormat, settings.theme, settings.animation, open(MailPage.ThemeSettings)) {
                settings.appIcon?.let { appIconIndex(it) }
                mailLookIndex(
                    behavior,
                    open(MailPage.SidebarSettings),
                    open(MailPage.PinnedSettings),
                    open(MailPage.MailListSettings),
                )
            }
        }
        page(
            "Mail behavior",
            open(MailPage.Behavior),
            Res.drawable.symbol_tune,
            summary = "Inbox, swipes, action bar, and reading",
        ) {
            mailBehaviorIndex(states.behavior, folderPills, rtl, open(MailPage.ActionBarSettings))
        }
        page(
            "Privacy",
            open(MailPage.Privacy),
            Res.drawable.symbol_visibility_off,
            summary = "Remote images and website icons",
        ) {
            privacyIndex(behavior, states.devicePrivacy, open(MailPage.AllowedSenders))
        }
        page(
            "Folders & rules",
            open(MailPage.Folders),
            Res.drawable.symbol_folder,
            summary = "Nest folders and file arriving mail",
        )
        page(
            "Signatures",
            open(MailPage.Signatures),
            Res.drawable.symbol_signature,
            summary = "One for each address you send from",
        ) {
            toggle("Add to replies", behavior.signatureInReplies)
        }
    }
}

/** The Notifications page, which shows only where pushes are available. */
private fun SettingsIndexScope.notifications(
    state: NotificationSettingsState,
    open: (MailPage) -> (String?) -> Unit,
) {
    val summary =
        state.current?.let { "Delivery through ${it.name}" }
            ?: if (state.periodicChecks) "New mail every 15 minutes" else "New-mail alerts off"
    page("Notifications", open(MailPage.Notifications), Res.drawable.symbol_notifications, summary = summary) {
        section("New mail") { setting("Instant delivery", state.deliveryLabel) }
        section("Previews") { page("Accounts and addresses", open(MailPage.NotificationOverrides)) }
    }
}

private fun accountsSummary(count: Int): String =
    when (count) {
        0 -> "Connect a JMAP server"
        1 -> "1 account connected"
        else -> "$count accounts connected"
    }
