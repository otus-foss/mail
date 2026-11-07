package com.otus.mail

import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureScreenRoboImage

/**
 * Compares the screen once idle, with any menu, sheet, or dialog that draws in a window of its own
 * laid over the page, against `src/test/screenshots/<name>.png`; `-Proborazzi.record` writes it
 * instead. Every Mail screenshot test captures through this, so all shots frame the same way.
 */
@OptIn(ExperimentalRoborazziApi::class)
internal fun AndroidComposeTestRule<*, *>.screenshot(name: String) {
    waitForIdle()
    captureScreenRoboImage("src/test/screenshots/$name.png")
}
