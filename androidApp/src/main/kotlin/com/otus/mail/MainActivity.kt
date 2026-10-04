package com.otus.mail

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.view.WindowCompat
import com.otus.mail.app.App
import com.otus.mail.core.dateformat.AndroidDateFormatPreferenceStore
import com.otus.mail.feature.settings.presentation.DateFormatSettingsState

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = false
            isAppearanceLightNavigationBars = false
        }
        val dateFormatState = DateFormatSettingsState(AndroidDateFormatPreferenceStore(this))
        setContent { App(dateFormatState) }
    }
}
