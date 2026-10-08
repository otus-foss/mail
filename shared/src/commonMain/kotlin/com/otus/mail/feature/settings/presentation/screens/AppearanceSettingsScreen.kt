package com.otus.mail.feature.settings.presentation.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.otus.dateformat.DateFormatPreference
import com.otus.dateformat.currentDateLocaleIdentifier
import com.otus.dateformat.currentLocalDate
import com.otus.designsystem.components.ChoiceDialog
import com.otus.designsystem.components.PageSection
import com.otus.designsystem.components.Subpage
import com.otus.designsystem.elements.ActionRow
import com.otus.mail.designsystem.resources.Res
import com.otus.mail.designsystem.resources.symbol_unfold_more
import com.otus.mail.feature.settings.presentation.DateFormatSettingsState
import com.otus.mail.feature.settings.presentation.dateFormatExample
import com.otus.mail.feature.settings.presentation.dateFormatSummary

@Composable
fun AppearanceSettingsScreen(
    dateFormat: DateFormatSettingsState,
    onBack: () -> Unit,
) {
    val locale = currentDateLocaleIdentifier()
    val today = remember { currentLocalDate() }
    var choosing by rememberSaveable { mutableStateOf(false) }
    Subpage("Appearance & behavior", onBack) {
        PageSection("Dates") {
            ActionRow(
                "Date format",
                { choosing = true },
                Res.drawable.symbol_unfold_more,
                summary = dateFormatSummary(dateFormat.preference, locale, today),
            )
        }
    }
    if (choosing) {
        ChoiceDialog(
            title = "Date format",
            options = DateFormatPreference.entries,
            selected = dateFormat.preference,
            label = { it.patternLabel },
            detail = { dateFormatExample(it, locale, today) },
            onSelect = dateFormat::select,
            onDismiss = { choosing = false },
        )
    }
}
