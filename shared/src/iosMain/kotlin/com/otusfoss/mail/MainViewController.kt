package com.otusfoss.mail

import androidx.compose.ui.window.ComposeUIViewController
import com.otusfoss.core.settings.presentation.AnimationSettingsState
import com.otusfoss.core.settings.presentation.DateFormatSettingsState
import com.otusfoss.core.settings.presentation.ThemeSettingsState
import com.otusfoss.mail.app.App
import com.otusfoss.mail.app.trimCache
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.launch

fun mainViewController(push: IosPushRuntime? = null) =
    DateFormatSettingsState(dateFormatChoice()).let { dateFormatState ->
        val services = push?.services ?: iosMailServices(null)
        push?.resume()
        // Cache upkeep outlives the screen; it ends with the process.
        CoroutineScope(Dispatchers.IO).launch { services.trimCache() }
        val behaviorState = iosMailBehavior
        val themeState = ThemeSettingsState(themeChoice())
        val animationState = AnimationSettingsState(animationChoice())
        ComposeUIViewController { App(dateFormatState, services, behaviorState, themeState, animationState) }
    }
