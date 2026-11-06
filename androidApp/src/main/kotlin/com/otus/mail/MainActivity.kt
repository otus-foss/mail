package com.otus.mail

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.core.graphics.drawable.toDrawable
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import com.otus.mail.app.App
import com.otus.mail.app.IncognitoKeyboard
import com.otus.mail.app.MailServices
import com.otus.mail.feature.updates.domain.InstalledBuild
import com.otus.mail.feature.updates.domain.ReleaseChannel
import com.otus.settings.presentation.AnimationSettingsState
import com.otus.settings.presentation.AppIconSettingsState
import com.otus.settings.presentation.DateFormatSettingsState
import com.otus.settings.presentation.ThemeSettingsState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    private val notificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) {}
    private lateinit var themeState: ThemeSettingsState
    private lateinit var appIconState: AppIconSettingsState

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val dateFormatState = DateFormatSettingsState(dateFormatChoice())
        val app = application as OtusMailApplication
        app.photoPicker.attach(this)
        app.attachmentFiles.attach(this)
        app.printer.attach(this)
        val services = app.services
        if (savedInstanceState == null) {
            askForNotifications(services)
            // A recreated activity gets its first intent again, which it has already handled.
            take(intent)
        }
        val behaviorState = app.behavior
        themeState = ThemeSettingsState(themeChoice())
        appIconState = AppIconSettingsState(appIconChoice())
        // Until Compose draws, the window shows the theme's color behind the launch animation.
        window.setBackgroundDrawable(
            themeState.palette.bgDark
                .toArgb()
                .toDrawable(),
        )
        val animationState = AnimationSettingsState(animationChoice())
        val build =
            InstalledBuild(
                ReleaseChannel.valueOf(BuildConfig.CHANNEL),
                BuildConfig.VERSION_NAME,
                BuildConfig.VERSION_CODE,
                BuildConfig.COMMIT,
            )
        setContent {
            // System bar icons stay readable on the theme's page color.
            val light = themeState.palette.isLight
            SideEffect {
                WindowCompat.getInsetsController(window, window.decorView).apply {
                    isAppearanceLightStatusBars = light
                    isAppearanceLightNavigationBars = light
                }
            }
            val secure = behaviorState.behavior.screenSecurity
            SideEffect { keepScreenPrivate(secure) }
            IncognitoKeyboard(behaviorState.behavior.incognitoKeyboard) {
                App(dateFormatState, services, behaviorState, themeState, animationState, appIconState, build)
            }
        }
    }

    /** Keeps the window out of screenshots, screen recordings, casting, and the recent apps preview while [on]. */
    private fun keepScreenPrivate(on: Boolean) {
        if (on) {
            window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) setRecentsScreenshotEnabled(!on)
    }

    /** A tapped alert or a `mailto:` link while Mail runs comes here instead of to a new activity. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        take(intent)
    }

    /** Hands [intent]'s request to Mail, and clears the alert whose Reply button sent it. */
    private fun take(intent: Intent) {
        (application as OtusMailApplication).services.requests.take(intent)
        val replied = intent.repliedAlert() ?: return
        MailNotifications(this).clear(listOf(replied))
    }

    override fun onStart() {
        super.onStart()
        (application as OtusMailApplication).services.visibility.show()
    }

    // Switching the launcher icon can close the app on some launchers, so it waits until Mail is hidden.
    override fun onStop() {
        super.onStop()
        (application as OtusMailApplication).services.visibility.hide()
        showLauncherIcon(appIconState.style, themeState.choice.accent)
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
