package com.otus.mail

import androidx.compose.ui.window.ComposeUIViewController
import com.otus.avatar.FileAvatarStore
import com.otus.dateformat.IosDateFormatPreferenceStore
import com.otus.jmap.login.IosLoginStore
import com.otus.mail.app.App
import com.otus.mail.app.MailDeviceStores
import com.otus.mail.app.MailServices
import com.otus.mail.feature.inbox.presentation.MailBehaviorState
import com.otus.settings.data.IosAnimationPreferenceStore
import com.otus.settings.presentation.AnimationSettingsState
import com.otus.settings.presentation.DateFormatSettingsState
import com.otus.settings.presentation.ThemeSettingsState
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
                stores = MailDeviceStores(IosReadLaterStore(), IosFolderColorStore(), IosAddressPinStore()),
            )
        val behaviorState = MailBehaviorState(IosMailBehaviorStore())
        val themeState = ThemeSettingsState(IosThemePreferenceStore())
        val animationState = AnimationSettingsState(IosAnimationPreferenceStore())
        ComposeUIViewController { App(dateFormatState, services, behaviorState, themeState, animationState) }
    }
