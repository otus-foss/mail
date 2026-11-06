package com.otus.mail

import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.otus.designsystem.theme.OtusTheme
import com.otus.designsystem.theme.colors.OtusColors
import com.otus.mail.core.settings.presentation.MailBehaviorState
import com.otus.mail.feature.mailsettings.presentation.components.WritingSettings
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The Undo send delay in Mail behavior's Writing section. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w360dp-h780dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class UndoSendSettingTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun theDelayDefaultsToFifteenSecondsAndCanBeTurnedOff() {
        val state = MailBehaviorState()
        compose.runOnUiThread {
            compose.activity.setContent {
                OtusTheme {
                    Surface(Modifier.fillMaxSize(), color = OtusColors.bgRegular) {
                        Column(
                            Modifier.verticalScroll(rememberScrollState()),
                        ) { WritingSettings(state, onOpenSignatures = {}) }
                    }
                }
            }
        }
        compose.onNodeWithText("15 seconds").performScrollTo().performClick()
        compose.screenshot("undo-send-setting")
        compose.onNodeWithText("Off").performClick()
        compose.waitForIdle()
        assertEquals(0, state.behavior.write.undoSendSeconds)
    }
}
