package com.otus.mail

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.SideEffect
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import com.otus.dateformat.AndroidDateFormatPreferenceStore
import com.otus.mail.app.App
import com.otus.mail.app.MailServices
import com.otus.mail.feature.inbox.presentation.MailBehaviorState
import com.otus.settings.presentation.AnimationSettingsState
import com.otus.settings.presentation.DateFormatSettingsState
import com.otus.settings.presentation.ThemeSettingsState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    private val notificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) {}

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val dateFormatState = DateFormatSettingsState(AndroidDateFormatPreferenceStore(this))
        val app = application as OtusMailApplication
        app.signatureImages.attach(this)
        val services = app.services
        if (savedInstanceState == null) askForNotifications(services)
        val behaviorState = MailBehaviorState(AndroidMailBehaviorStore(this))
        val themeState = ThemeSettingsState(AndroidThemePreferenceStore(this))
        val animationState = AnimationSettingsState(AndroidAnimationPreferenceStore(this))
        val nightlyBuild = BuildConfig.VERSION_CODE.takeIf { BuildConfig.NIGHTLY }
        setContent {
            // System bar icons stay readable on the theme's page color.
            val light = themeState.palette.isLight
            SideEffect {
                WindowCompat.getInsetsController(window, window.decorView).apply {
                    isAppearanceLightStatusBars = light
                    isAppearanceLightNavigationBars = light
                }
            }
            App(dateFormatState, services, behaviorState, themeState, animationState, nightlyBuild)
        }
    }

    /** Asks once per launch, and only when there is mail to notify about. */
    private fun askForNotifications(services: MailServices) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) return
        lifecycleScope.launch {
            if (withContext(Dispatchers.IO) { services.logins().isNotEmpty() }) {
                notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }
}
