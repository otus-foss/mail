package com.otus.mail

import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import com.otus.designsystem.shared.controls.elements.AnimatedText
import com.otus.designsystem.theme.OtusTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w320dp-h480dp-mdpi")
class AnimatedTextTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val label = mutableStateOf("January")
    private val order = mutableStateOf(0)

    @Test
    fun laterLabelsEnterFromBelowAndEarlierLabelsEnterFromAbove() {
        showLabel()
        val restingTop = child("January").fetchSemanticsNode().positionInRoot.y
        compose.mainClock.autoAdvance = false
        replace("February", 1)
        compose.mainClock.advanceTimeBy(64)
        assertTrue(child("February").fetchSemanticsNode().positionInRoot.y > restingTop)
        assertTrue(child("January").fetchSemanticsNode().positionInRoot.y < restingTop)
        compose.onNodeWithTag("Animated label").assertTextEquals("February")
        compose.mainClock.advanceTimeBy(400)
        child("January").assertDoesNotExist()
        assertEquals(restingTop, child("February").fetchSemanticsNode().positionInRoot.y, 1f)
        replace("January", 0)
        compose.mainClock.advanceTimeBy(64)
        assertTrue(child("January").fetchSemanticsNode().positionInRoot.y < restingTop)
        assertTrue(child("February").fetchSemanticsNode().positionInRoot.y > restingTop)
        compose.mainClock.advanceTimeBy(400)
        child("February").assertDoesNotExist()
    }

    @Test
    fun interruptedReplacementSettlesOnLatestLabel() {
        showLabel()
        compose.mainClock.autoAdvance = false
        replace("February", 1)
        compose.mainClock.advanceTimeBy(64)
        replace("December", -1)
        compose.mainClock.advanceTimeBy(64)
        replace("January", 0)
        compose.onNodeWithTag("Animated label").assertTextEquals("January")
        compose.mainClock.advanceTimeBy(500)
        child("February").assertDoesNotExist()
        child("December").assertDoesNotExist()
        assertEquals(0f, child("January").fetchSemanticsNode().positionInRoot.y, 1f)
    }

    @Test
    fun unchangedTextStaysStillWhenOrderChanges() {
        showLabel()
        val before = child("January").fetchSemanticsNode().positionInRoot
        compose.mainClock.autoAdvance = false
        replace("January", 12)
        compose.mainClock.advanceTimeBy(64)
        assertEquals(before, child("January").fetchSemanticsNode().positionInRoot)
    }

    private fun showLabel() {
        compose.runOnUiThread {
            compose.activity.setContent {
                OtusTheme {
                    Box {
                        AnimatedText(label.value, order.value, Modifier.width(200.dp).testTag("Animated label"))
                    }
                }
            }
        }
        compose.waitForIdle()
    }

    private fun replace(
        text: String,
        value: Int,
    ) {
        compose.runOnIdle {
            label.value = text
            order.value = value
        }
        compose.mainClock.advanceTimeByFrame()
        compose.waitForIdle()
    }

    private fun child(text: String) =
        compose.onNode(hasText(text) and hasAnyAncestor(hasTestTag("Animated label")), useUnmergedTree = true)
}
