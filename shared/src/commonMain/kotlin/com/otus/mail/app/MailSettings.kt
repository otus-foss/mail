package com.otus.mail.app

import com.otus.designsystem.resources.Res
import com.otus.designsystem.resources.symbol_alternate_email
import com.otus.designsystem.resources.symbol_folder
import com.otus.designsystem.resources.symbol_notifications
import com.otus.designsystem.resources.symbol_palette
import com.otus.designsystem.resources.symbol_signature
import com.otus.designsystem.resources.symbol_tune
import com.otus.mail.feature.notifications.presentation.NotificationSettingsState
import com.otus.settings.presentation.SettingsEntry

/** Mail chooses the destinations; the settings library renders their shared rows and page. */
internal fun mailSettingsEntries(
    accountCount: Int,
    notifications: NotificationSettingsState?,
    navigation: MailNavigation,
): List<SettingsEntry> =
    listOfNotNull(
        SettingsEntry(
            "Email accounts",
            when (accountCount) {
                0 -> "Connect a JMAP server"
                1 -> "1 account connected"
                else -> "$accountCount accounts connected"
            },
            Res.drawable.symbol_alternate_email,
        ) { navigation.go(MailPage.EmailAccounts) },
        notifications?.let { state ->
            SettingsEntry(
                "Notifications",
                state.current?.let { "Instant through ${it.name}" } ?: "New mail every 15 minutes",
                Res.drawable.symbol_notifications,
            ) { navigation.go(MailPage.Notifications) }
        },
        SettingsEntry(
            "Appearance",
            "Theme, sidebar, dates, and animations",
            Res.drawable.symbol_palette,
        ) { navigation.go(MailPage.Appearance) },
        SettingsEntry(
            "Mail behavior",
            "Inbox, swipes, action bar, and reading",
            Res.drawable.symbol_tune,
        ) { navigation.go(MailPage.Behavior) },
        SettingsEntry(
            "Folders & rules",
            "Nest folders and file arriving mail",
            Res.drawable.symbol_folder,
        ) { navigation.go(MailPage.Folders) },
        SettingsEntry(
            "Signatures",
            "One for each address you send from",
            Res.drawable.symbol_signature,
        ) { navigation.go(MailPage.Signatures) },
    )
