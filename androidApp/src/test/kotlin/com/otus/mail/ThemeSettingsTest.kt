package com.otus.mail

import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.otus.designsystem.theme.colors.OtusColors
import com.otus.designsystem.theme.colors.OtusPalette
import com.otus.designsystem.theme.colors.ThemeBase
import com.otus.designsystem.theme.colors.ThemeChoice
import com.otus.settings.presentation.themeTitle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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
        get() = compose.activity.themeChoice().read()

    @Test
    fun aThemeAppliesToTheWholeAppAndIsSaved() {
        openTheme()
        compose.onNode(hasText("Light") and isSelectable()).performScrollTo().performClick()
        // App applies the new palette on its next recomposition, a frame after the page shows it.
        compose.waitUntil(TIMEOUT) { OtusColors.palette.isLight }
        assertEquals("light #5A7EA4", saved)

        compose.onNodeWithContentDescription("Back").performClick()
        compose
            .onNode(hasText("Theme") and hasText(ThemeChoice(ThemeBase.Light).themeTitle))
            .assertExists()
            .performClick()
        compose.onNodeWithText("Reset to Otus").performScrollTo().performClick()
        compose.waitUntil(TIMEOUT) { OtusColors.palette == OtusPalette.Otus }
        assertEquals("otus #5A7EA4", saved)
    }

    @Test
    fun aSetFromOneHueIsAppliedAsANewThemeThatReadsWell() {
        openTheme()
        compose.onNode(hasText("Light mode") and isSelectable()).performScrollTo().performClick()
        compose.onNode(hasText("Apply") and hasClickAction()).performScrollTo().performClick()
        compose.waitUntil(TIMEOUT) { OtusColors.palette.isLight }
        assertTrue(saved.orEmpty().startsWith("light #"))
        assertTrue(" custom=1 1:light 1:page=#" in saved.orEmpty())
        compose.onNode(hasText("Low contrast", substring = true)).assertDoesNotExist()
    }

    private fun openTheme() {
        compose.onNodeWithContentDescription("Open navigation").performClick()
        compose.onNodeWithContentDescription("Settings").performClick()
        compose.onNodeWithText("Appearance").performClick()
        compose.onNode(hasText("Theme") and hasText("Otus")).performClick()
    }

    private companion object {
        const val TIMEOUT = 5_000L
    }
}
