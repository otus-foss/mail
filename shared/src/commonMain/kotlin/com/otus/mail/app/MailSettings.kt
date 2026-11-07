package com.otus.mail.app

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import com.otus.dateformat.DateFormatter
import com.otus.designsystem.resources.Res
import com.otus.designsystem.resources.symbol_alternate_email
import com.otus.designsystem.resources.symbol_folder
import com.otus.designsystem.resources.symbol_notifications
import com.otus.designsystem.resources.symbol_palette
import com.otus.designsystem.resources.symbol_tune
import com.otus.designsystem.resources.symbol_visibility_off
import com.otus.mail.core.settings.presentation.MailBehaviorState
import com.otus.mail.core.settings.presentation.PRIVACY_TITLE
import com.otus.mail.feature.folders.presentation.screens.FOLDERS_TITLE
import com.otus.mail.feature.mailsettings.presentation.components.BehaviorPages
import com.otus.mail.feature.mailsettings.presentation.components.alertButtonsRow
import com.otus.mail.feature.mailsettings.presentation.components.mailBehaviorSettings
import com.otus.mail.feature.mailsettings.presentation.components.mailListRow
import com.otus.mail.feature.mailsettings.presentation.components.privacySettings
import com.otus.mail.feature.mailsettings.presentation.components.updateToastRow
import com.otus.mail.feature.mailsettings.presentation.screens.MAIL_BEHAVIOR_TITLE
import com.otus.mail.feature.notifications.domain.NotificationPreviews
import com.otus.mail.feature.notifications.presentation.NotificationSettingsState
import com.otus.mail.feature.notifications.presentation.screens.deliveryLabel
import com.otus.mail.feature.notifications.presentation.screens.deliverySummary
import com.otus.mail.feature.notifications.presentation.short
import com.otus.mail.feature.notifications.presentation.title
import com.otus.settings.presentation.SettingsIndexScope
import com.otus.settings.presentation.SettingsItem
import com.otus.settings.presentation.appIconIndex
import com.otus.settings.presentation.appearanceIndex
import com.otus.settings.presentation.components.PageRowSpec
import com.otus.settings.presentation.components.row
import com.otus.settings.presentation.components.sections
import com.otus.settings.presentation.settingsIndex

/**
 * Every Mail settings page and setting that search finds; the settings home lists the top-level
 * pages. Each result opens its page through [navigation] with the setting found.
 */
@Composable
internal fun mailSettingsIndex(
    states: MailStates,
    settings: SharedSettings,
    dateFormatter: DateFormatter,
    navigation: MailNavigation,
): List<SettingsItem> {
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val folderPills = rememberFolderPills(states)
    val open = navigation::finder
    return settingsIndex {
        page(
            EMAIL_ACCOUNTS_TITLE,
            open(MailPage.EmailAccounts),
            Res.drawable.symbol_alternate_email,
            summary = accountsSummary(states.accounts.accounts.size),
        ) { keywords("JMAP", "Server", "Sign in") }
        states.notifications?.let { notifications(it, states.behavior, states.accounts.accounts.isNotEmpty(), open) }
        appearance(settings, mailListRow(states.behavior, dateFormatter, open(MailPage.MailListSettings)), open)
        page(
            MAIL_BEHAVIOR_TITLE,
            open(MailPage.Behavior),
            Res.drawable.symbol_tune,
            summary = "Inbox, sidebar, swipes, reading, and writing",
        ) {
            val startPages = sidebarPinOptions(states.folders.all.accounts, states.folders.colors::groupColorOf)
            sections(mailBehaviorSettings(states.behavior, folderPills, startPages, rtl, behaviorPages(open)))
        }
        page(
            PRIVACY_TITLE,
            open(MailPage.Privacy),
            Res.drawable.symbol_visibility_off,
            summary = "Remote images, avatars, and this device",
        ) {
            keywords("Security")
            sections(privacySettings(states.behavior, states.devicePrivacy, open(MailPage.AllowedSenders)))
        }
        page(
            FOLDERS_TITLE,
            open(MailPage.Folders),
            Res.drawable.symbol_folder,
            summary = "Nest folders and file arriving mail",
        ) { keywords("Filters", "Labels") }
    }
}

/** The pages Mail behavior's rows lead to, each opened with [open]. */
internal fun behaviorPages(open: (MailPage) -> (String?) -> Unit): BehaviorPages =
    BehaviorPages(
        sidebar = open(MailPage.SidebarSettings),
        pinned = open(MailPage.PinnedSettings),
        filterPanel = open(MailPage.FilterPanelSettings),
        actionBar = open(MailPage.ActionBarSettings),
        signatures = open(MailPage.Signatures),
    )

/** The Appearance page, with Mail's App icon and [mailList] rows among the shared ones. */
private fun SettingsIndexScope.appearance(
    settings: SharedSettings,
    mailList: PageRowSpec,
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
            row(mailList)
        }
    }
}

/**
 * The Notifications page, which shows only where pushes are available. [hasAccounts] gates its
 * overrides, and the alert buttons show with previews, as on the page.
 */
private fun SettingsIndexScope.notifications(
    state: NotificationSettingsState,
    behavior: MailBehaviorState,
    hasAccounts: Boolean,
    open: (MailPage) -> (String?) -> Unit,
) {
    page(
        "Notifications",
        open(MailPage.Notifications),
        Res.drawable.symbol_notifications,
        summary = state.deliverySummary,
    ) {
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
        section("Buttons") { row(alertButtonsRow(behavior)) }
        section("Updates") { row(updateToastRow(behavior)) }
    }
}

private fun accountsSummary(count: Int): String =
    when (count) {
        0 -> "Connect a JMAP server"
        1 -> "1 account connected"
        else -> "$count accounts connected"
    }
