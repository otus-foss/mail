package com.otus.mail.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.otus.dateformat.DateFormatter
import com.otus.dateformat.currentDateLocaleIdentifier
import com.otus.settings.presentation.DateFormatSettingsState

/** Rebuild only when the selected preference or the active platform locale changes. */
@Composable
fun rememberDateFormatter(settings: DateFormatSettingsState): DateFormatter {
    val localeIdentifier = currentDateLocaleIdentifier()
    return remember(settings.preference, localeIdentifier) {
        DateFormatter(settings.preference, localeIdentifier)
    }
}
