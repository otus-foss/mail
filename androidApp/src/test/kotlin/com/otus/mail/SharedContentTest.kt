package com.otus.mail

import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.otus.designsystem.resources.Res
import com.otus.designsystem.resources.symbol_folder
import com.otus.designsystem.shared.controls.elements.Symbol
import com.otus.designsystem.shared.rows.components.ContentRow
import com.otus.designsystem.shared.rows.components.ContentRowText
import com.otus.designsystem.shared.rows.components.DetailRow
import com.otus.designsystem.theme.OtusTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w412dp-h892dp-xxhdpi")
class SharedContentTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun sharedRowsRenderCallerValuesAndForwardIndependentActions() {
        var contentClicks = 0
        var detailClicks = 0
        compose.runOnUiThread {
            compose.activity.setContent {
                OtusTheme {
                    Column {
                        ContentRow(
                            text = ContentRowText("Planning session", "Team room", "10:00", "Friday"),
                            emphasized = true,
                            stateLabel = "Upcoming",
                            onClick = { contentClicks++ },
                            leading = { Symbol(Res.drawable.symbol_folder) },
                        )
                        DetailRow("Projects", listOf("3 items"), { detailClicks++ }, depth = 2) {
                            Symbol(Res.drawable.symbol_folder)
                        }
                    }
                }
            }
        }
        compose.onNodeWithText("Team room").assertIsDisplayed()
        compose.onNodeWithText("Friday").assertIsDisplayed()
        compose.onNodeWithText("3 items").assertIsDisplayed()
        compose.onNodeWithText("Planning session").performClick()
        compose.onNodeWithText("Projects").performClick()
        compose.runOnIdle {
            assertEquals(1, contentClicks)
            assertEquals(1, detailClicks)
        }
    }
}
