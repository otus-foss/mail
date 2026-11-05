package com.otus.mail

import androidx.compose.ui.window.ComposeUIViewController
import com.otus.dateformat.IosDateFormatPreferenceStore
import com.otus.mail.app.App
import com.otus.mail.app.trimCache
import com.otus.mail.feature.inbox.presentation.MailBehaviorState
import com.otus.settings.data.IosAnimationPreferenceStore
import com.otus.settings.presentation.AnimationSettingsState
import com.otus.settings.presentation.DateFormatSettingsState
import com.otus.settings.presentation.ThemeSettingsState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.launch

fun mainViewController(push: IosPushRuntime? = null) =
    DateFormatSettingsState(IosDateFormatPreferenceStore()).let { dateFormatState ->
        val services = push?.mailServices ?: iosMailServices(null)
        push?.resume()
        // Cache upkeep outlives the screen; it ends with the process.
        CoroutineScope(Dispatchers.IO).launch { services.trimCache() }
        val behaviorState = MailBehaviorState(IosMailBehaviorStore())
        val themeState = ThemeSettingsState(IosThemePreferenceStore())
        val animationState = AnimationSettingsState(IosAnimationPreferenceStore())
        ComposeUIViewController { App(dateFormatState, services, behaviorState, themeState, animationState) }
    }
