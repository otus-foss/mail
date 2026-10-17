package com.otus.mail

import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Surface
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.otus.designsystem.components.CompactSendButton
import com.otus.designsystem.components.FormatButton
import com.otus.designsystem.components.FormattingBar
import com.otus.designsystem.elements.RichText
import com.otus.designsystem.elements.RichTextBody
import com.otus.designsystem.resources.Res
import com.otus.designsystem.resources.symbol_attach_file
import com.otus.designsystem.theme.ActionBarTokens
import com.otus.designsystem.theme.OtusColors
import com.otus.designsystem.theme.OtusTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The formatting row the compose page shows above the keyboard, rendered on its own because a
 * Robolectric window has no keyboard to raise.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w360dp-h400dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class FormattingRowTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun boldTurnsOnForWhatIsTypedNextAndShowsFilled() {
        compose.runOnUiThread {
            compose.activity.setContent {
                OtusTheme {
                    val text =
                        remember { RichText("<p>Thursday works for me. I'll bring the</p>") }
                    Surface(Modifier.fillMaxSize(), color = OtusColors.bgRegular) {
                        Box {
                            RichTextBody(text, "Write your message")
                            FormattingBar(
                                text,
                                onLink = {},
                                Modifier.align(Alignment.BottomCenter).padding(ActionBarTokens.barInset),
                                leading = {
                                    FormatButton(
                                        "Attach",
                                        Res.drawable.symbol_attach_file,
                                        false,
                                        {},
                                        enabled = false,
                                    )
                                },
                                trailing = { CompactSendButton("Send to Grace", {}, enabled = true) },
                            )
                        }
                    }
                }
            }
        }
        val body = compose.onNodeWithContentDescription("Write your message")
        body.performTextInput(" ")
        compose.onNodeWithContentDescription("Bold").performClick()
        body.performTextInput("updated numbers")
        compose.onNodeWithContentDescription("Bold").assertIsSelected()
        compose.screenshot("compose-formatting-row")
    }
}
