package com.otus.mail.app

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import com.otus.designsystem.resources.Res
import com.otus.designsystem.resources.symbol_alternate_email
import com.otus.designsystem.resources.symbol_folder
import com.otus.designsystem.resources.symbol_notifications
import com.otus.designsystem.resources.symbol_palette
import com.otus.designsystem.resources.symbol_tune
import com.otus.designsystem.resources.symbol_visibility_off
import com.otus.mail.core.settings.domain.MailBehavior
import com.otus.mail.core.settings.presentation.PRIVACY_TITLE
import com.otus.mail.feature.inbox.presentation.components.BehaviorIndexPages
import com.otus.mail.feature.inbox.presentation.components.mailBehaviorIndex
import com.otus.mail.feature.inbox.presentation.components.mailLookIndex
import com.otus.mail.feature.inbox.presentation.components.notificationActionsIndex
import com.otus.mail.feature.inbox.presentation.components.privacyIndex
import com.otus.mail.feature.notifications.domain.NotificationPreviews
import com.otus.mail.feature.notifications.presentation.NotificationSettingsState
import com.otus.mail.feature.notifications.presentation.screens.deliveryLabel
import com.otus.mail.feature.notifications.presentation.short
import com.otus.mail.feature.notifications.presentation.title
import com.otus.settings.presentation.SettingsIndexScope
import com.otus.settings.presentation.SettingsItem
import com.otus.settings.presentation.appIconIndex
import com.otus.settings.presentation.appearanceIndex
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
        ) { keywords("JMAP", "Server", "Sign in") }
        states.notifications?.let { notifications(it, behavior, states.accounts.accounts.isNotEmpty(), open) }
        appearance(settings, behavior, open)
        page(
            "Mail behavior",
            open(MailPage.Behavior),
            Res.drawable.symbol_tune,
            summary = "Inbox, sidebar, swipes, reading, and writing",
        ) {
            val startPages = sidebarPinOptions(states.folders.accounts, states.folderColors::groupColorOf)
            mailBehaviorIndex(states.behavior, folderPills, startPages, rtl, behaviorPages(open))
        }
        page(
            PRIVACY_TITLE,
            open(MailPage.Privacy),
            Res.drawable.symbol_visibility_off,
            summary = "Remote images, avatars, and this device",
        ) {
            keywords("Security")
            privacyIndex(behavior, states.devicePrivacy, open(MailPage.AllowedSenders))
        }
        page(
            "Folders & rules",
            open(MailPage.Folders),
            Res.drawable.symbol_folder,
            summary = "Nest folders and file arriving mail",
        ) { keywords("Filters", "Labels") }
    }
}

private fun behaviorPages(open: (MailPage) -> (String?) -> Unit): BehaviorIndexPages =
    BehaviorIndexPages(
        sidebar = open(MailPage.SidebarSettings),
        pinned = open(MailPage.PinnedSettings),
        filterPanel = open(MailPage.FilterPanelSettings),
        actionBar = open(MailPage.ActionBarSettings),
        signatures = open(MailPage.Signatures),
    )

/** The Appearance page, with Mail's App icon and Mail list rows among the shared ones. */
private fun SettingsIndexScope.appearance(
    settings: SharedSettings,
    behavior: MailBehavior,
    open: (MailPage) -> (String?) -> Unit,
) {
    page(
        "Appearance",
        open(MailPage.Appearance),
        Res.drawable.symbol_palette,
        summary = "Theme, app icon, mail list, dates, and animations",
    ) {
        appearanceIndex(settings.dateFormat, settings.theme, settings.animation, open(MailPage.ThemeSettings)) {
            settings.appIcon?.let { appIconIndex(it) }
            mailLookIndex(behavior, open(MailPage.MailListSettings))
        }
    }
}

/**
 * The Notifications page, which shows only where pushes are available. [hasAccounts] gates its
 * overrides, and the alert buttons show with previews, as on the page.
 */
private fun SettingsIndexScope.notifications(
    state: NotificationSettingsState,
    behavior: MailBehavior,
    hasAccounts: Boolean,
    open: (MailPage) -> (String?) -> Unit,
) {
    val summary =
        state.current?.let { "Delivery through ${it.name}" }
            ?: if (state.periodicChecks) "New mail every 15 minutes" else "New-mail alerts off"
    page("Notifications", open(MailPage.Notifications), Res.drawable.symbol_notifications, summary = summary) {
        keywords("Push", "Alerts")
        section("New mail") { setting("Instant delivery", state.deliveryLabel) }
        // The page shows previews only where they can be saved, and their overrides only with accounts.
        val previews = state.previews ?: return@page
        section("Previews") {
            setting(
                "All mail",
                previews.all.short,
                NotificationPreviews.entries.map { it.title },
                keywords = listOf("Lock screen", "Previews"),
            )
            if (hasAccounts) page("Accounts and addresses", open(MailPage.NotificationOverrides))
        }
        section("Buttons") { notificationActionsIndex(behavior) }
    }
}

private fun accountsSummary(count: Int): String =
    when (count) {
        0 -> "Connect a JMAP server"
        1 -> "1 account connected"
        else -> "$count accounts connected"
    }
