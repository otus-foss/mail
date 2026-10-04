package com.otus.mail.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.otus.mail.designsystem.theme.OtusTheme
import com.otus.mail.feature.inbox.presentation.screens.InboxScreen
import com.otus.mail.feature.settings.presentation.DateFormatSettingsState

@Composable
fun App(dateFormatState: DateFormatSettingsState = remember { DateFormatSettingsState() }) {
    val dateFormatter = rememberDateFormatter(dateFormatState)
    OtusTheme {
        InboxScreen(messages = emptyList(), dateFormatter = dateFormatter)
    }
}
