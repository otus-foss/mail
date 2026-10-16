package com.otus.mail

import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import com.otus.designsystem.theme.OtusColors
import com.otus.designsystem.theme.OtusPalette
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w412dp-h892dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ThemeSettingsTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    private val saved: String?
        get() = AndroidThemePreferenceStore(compose.activity).read()

    @Test
    fun aThemeAndAnAccentApplyToTheWholeAppAndAreSaved() {
        openTheme()
        compose.onNode(hasText("Light") and isSelectable()).performScrollTo().performClick()
        // App applies the new palette on its next recomposition, a frame after the page shows it.
        compose.waitUntil(TIMEOUT) { OtusColors.palette.isLight }
        assertEquals("light #4D6F92", saved)

        compose.onNode(hasContentDescription("Green") and isSelectable()).performScrollTo().performClick()
        assertEquals("light #3F8F6B", saved)

        compose.onNodeWithText("Reset to Otus").performScrollTo().performClick()
        compose.waitUntil(TIMEOUT) { OtusColors.palette == OtusPalette.Otus }
        assertEquals("otus #4D6F92", saved)
    }

    @Test
    fun aCustomColorIsPickedByItsHexCode() {
        openTheme()
        compose.onNode(hasContentDescription("Custom color") and hasClickAction()).performScrollTo().performClick()
        compose.onNode(hasSetTextAction()).performTextReplacement("#C75E38")
        compose.onNode(hasText("Done") and hasClickAction()).performClick()
        compose.waitUntil(TIMEOUT) { saved == "otus #C75E38" }
        compose.onNode(hasContentDescription("Custom color #C75E38") and isSelectable()).assertExists()

        compose.onNodeWithContentDescription("Back").performClick()
        compose.onNode(hasText("Theme") and hasText("Otus")).assertExists()
    }

    private fun openTheme() {
        compose.onNodeWithContentDescription("Open navigation").performClick()
        compose.onNodeWithContentDescription("Settings").performClick()
        compose.onNodeWithText("Appearance & behavior").performClick()
        compose.onNode(hasText("Theme") and hasText("Colors across the whole app")).performClick()
    }

    private companion object {
        const val TIMEOUT = 5_000L
    }
}
