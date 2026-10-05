package com.otus.mail.feature.settings.presentation.screens

import androidx.compose.runtime.Composable
import com.otus.mail.designsystem.components.PageScaffold
import com.otus.mail.designsystem.elements.ActionRow
import com.otus.mail.designsystem.resources.Res
import com.otus.mail.designsystem.resources.symbol_alternate_email
import com.otus.mail.designsystem.resources.symbol_chevron_right
import com.otus.mail.designsystem.resources.symbol_tune
import com.otus.mail.feature.settings.presentation.DateFormatSettingsState

@Composable
fun SettingsScreen(
    dateFormat: DateFormatSettingsState,
    accountsSummary: String,
    onOpenNavigation: () -> Unit,
    onOpenAccounts: () -> Unit,
    onOpenAppearance: () -> Unit,
) {
    PageScaffold("Settings", navigationLabel = "Open navigation", onNavigation = onOpenNavigation) {
        ActionRow(
            "Email accounts",
            onOpenAccounts,
            Res.drawable.symbol_chevron_right,
            summary = accountsSummary,
            icon = Res.drawable.symbol_alternate_email,
            mirrorTrailingInRtl = true,
        )
        ActionRow(
            "Appearance & behavior",
            onOpenAppearance,
            Res.drawable.symbol_chevron_right,
            summary = "Date format: ${dateFormat.preference.patternLabel}",
            icon = Res.drawable.symbol_tune,
            mirrorTrailingInRtl = true,
        )
    }
}
