package com.otus.mail

import androidx.compose.ui.window.ComposeUIViewController
import com.otus.mail.app.App
import com.otus.mail.core.dateformat.IosDateFormatPreferenceStore
import com.otus.mail.feature.settings.presentation.DateFormatSettingsState

fun mainViewController() =
    DateFormatSettingsState(IosDateFormatPreferenceStore()).let { dateFormatState ->
        ComposeUIViewController { App(dateFormatState) }
    }
