package com.otus.mail.designsystem.theme

import androidx.compose.animation.core.CubicBezierEasing

/** Suite motion defaults. Keep these values aligned in Mail and Calendar. */
object MotionTokens {
    val standard = CubicBezierEasing(0.4f, 0f, 0.2f, 1f)
    val enter = CubicBezierEasing(0.2f, 0f, 0f, 1f)
    val exit = CubicBezierEasing(0.4f, 0f, 1f, 1f)
    const val TEXT_REPLACEMENT_MILLIS = 260
    const val TEXT_EXIT_MILLIS = 180
}
