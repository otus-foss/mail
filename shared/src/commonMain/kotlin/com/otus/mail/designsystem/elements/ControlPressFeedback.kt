package com.otus.mail.designsystem.elements

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import com.otus.mail.designsystem.theme.ControlFeedbackTokens
import com.otus.mail.designsystem.theme.MotionTokens
import com.otus.mail.designsystem.theme.OtusColors

/** Share the touch target's interactions, but paint feedback only on the visible control. */
@Composable
internal fun Modifier.controlPressFeedback(
    interactions: InteractionSource,
    shape: Shape = CircleShape,
): Modifier {
    val pressed by interactions.collectIsPressedAsState()
    val focused by interactions.collectIsFocusedAsState()
    val opacity =
        animateFloatAsState(
            targetValue = if (pressed || focused) ControlFeedbackTokens.OPACITY else 0f,
            animationSpec = tween(ControlFeedbackTokens.DURATION_MILLIS, easing = MotionTokens.standard),
            label = "Control press feedback",
        )
    return drawWithCache {
        val outline = shape.createOutline(size, layoutDirection, this)
        onDrawWithContent {
            drawContent()
            if (opacity.value > 0f) drawOutline(outline, OtusColors.text1, alpha = opacity.value)
        }
    }
}
