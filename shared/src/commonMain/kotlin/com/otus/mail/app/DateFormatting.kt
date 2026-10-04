package com.otus.mail.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.otus.mail.core.dateformat.DateFormatter
import com.otus.mail.core.dateformat.currentDateLocaleIdentifier
import com.otus.mail.feature.settings.presentation.DateFormatSettingsState

/** Rebuild only when the selected preference or the active platform locale changes. */
@Composable
fun rememberDateFormatter(settings: DateFormatSettingsState): DateFormatter {
    val localeIdentifier = currentDateLocaleIdentifier()
    return remember(settings.preference, localeIdentifier) {
        DateFormatter(settings.preference, localeIdentifier)
    }
}
