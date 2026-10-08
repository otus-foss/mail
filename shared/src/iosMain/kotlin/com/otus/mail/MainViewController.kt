package com.otus.mail

import androidx.compose.ui.window.ComposeUIViewController
import com.otus.avatar.FileAvatarStore
import com.otus.dateformat.IosDateFormatPreferenceStore
import com.otus.jmap.login.IosLoginStore
import com.otus.mail.app.App
import com.otus.mail.app.MailServices
import com.otus.mail.feature.settings.presentation.DateFormatSettingsState
import platform.Foundation.NSCachesDirectory
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSUserDomainMask

fun mainViewController() =
    DateFormatSettingsState(IosDateFormatPreferenceStore()).let { dateFormatState ->
        // Names kept from the first release so saved accounts keep working.
        val cacheDirectory =
            NSSearchPathForDirectoriesInDomains(
                NSCachesDirectory,
                NSUserDomainMask,
                true,
            ).first() as String
        val services =
            MailServices(
                IosLoginStore(listKey = "otus.accounts", service = "otus.mail.account"),
                avatarStore = FileAvatarStore("$cacheDirectory/avatars-v1"),
            )
        ComposeUIViewController { App(dateFormatState, services) }
    }
