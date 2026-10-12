package com.otus.mail.app

import com.otus.designsystem.resources.Res
import com.otus.designsystem.resources.symbol_alternate_email
import com.otus.designsystem.resources.symbol_folder
import com.otus.designsystem.resources.symbol_rule_folder
import com.otus.designsystem.resources.symbol_tune
import com.otus.settings.presentation.SettingsEntry

/** Mail chooses the destinations; the settings library renders their shared rows and page. */
internal fun mailSettingsEntries(
    accountCount: Int,
    navigation: MailNavigation,
): List<SettingsEntry> =
    listOf(
        SettingsEntry(
            "Email accounts",
            when (accountCount) {
                0 -> "Connect a JMAP server"
                1 -> "1 account connected"
                else -> "$accountCount accounts connected"
            },
            Res.drawable.symbol_alternate_email,
        ) { navigation.go(MailPage.EmailAccounts) },
        SettingsEntry(
            "Appearance & behavior",
            "Dates, sidebar, swipes, and reading",
            Res.drawable.symbol_tune,
        ) { navigation.go(MailPage.Appearance) },
        SettingsEntry(
            "Folders",
            "Create, rename, and nest folders",
            Res.drawable.symbol_folder,
        ) { navigation.go(MailPage.Folders) },
        SettingsEntry(
            "Rules",
            "Move arriving mail into folders",
            Res.drawable.symbol_rule_folder,
        ) { navigation.go(MailPage.Rules) },
    )
