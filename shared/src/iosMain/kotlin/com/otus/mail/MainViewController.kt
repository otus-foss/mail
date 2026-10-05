package com.otus.mail

import androidx.compose.ui.window.ComposeUIViewController
import com.otus.jmap.login.IosLoginStore
import com.otus.mail.app.App
import com.otus.mail.app.MailServices
import com.otus.mail.core.dateformat.IosDateFormatPreferenceStore
import com.otus.mail.feature.settings.presentation.DateFormatSettingsState

fun mainViewController() =
    DateFormatSettingsState(IosDateFormatPreferenceStore()).let { dateFormatState ->
        // Names kept from the first release so saved accounts keep working.
        val services = MailServices(IosLoginStore(listKey = "otus.accounts", service = "otus.mail.account"))
        ComposeUIViewController { App(dateFormatState, services) }
    }
