package com.otus.mail

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.SideEffect
import androidx.core.view.WindowCompat
import com.otus.dateformat.AndroidDateFormatPreferenceStore
import com.otus.mail.app.App
import com.otus.mail.feature.inbox.presentation.MailBehaviorState
import com.otus.settings.presentation.DateFormatSettingsState
import com.otus.settings.presentation.ThemeSettingsState

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val dateFormatState = DateFormatSettingsState(AndroidDateFormatPreferenceStore(this))
        val services = (application as OtusMailApplication).services
        val behaviorState = MailBehaviorState(AndroidMailBehaviorStore(this))
        val themeState = ThemeSettingsState(AndroidThemePreferenceStore(this))
        setContent {
            // System bar icons stay readable on the theme's page color.
            val light = themeState.palette.isLight
            SideEffect {
                WindowCompat.getInsetsController(window, window.decorView).apply {
                    isAppearanceLightStatusBars = light
                    isAppearanceLightNavigationBars = light
                }
            }
            App(dateFormatState, services, behaviorState, themeState)
        }
    }
}
