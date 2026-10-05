package com.otus.mail.designsystem.elements

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import com.otus.mail.designsystem.theme.OtusColors
import com.otus.mail.designsystem.theme.PanelTokens
import com.otus.mail.designsystem.theme.RefreshTokens
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * A pill-styled circle for pull-to-refresh. Its arc grows and turns with [progress], where 1 is
 * the refresh threshold, and becomes an indeterminate spinner while [refreshing].
 */
@Composable
fun RefreshIndicator(
    progress: () -> Float,
    refreshing: Boolean,
    modifier: Modifier = Modifier,
) {
    // Not a Surface: the hidden indicator rests over the panel and must not swallow its touches.
    Box(
        modifier
            .size(RefreshTokens.indicatorSize)
            .background(OtusColors.bgDark, CircleShape)
            .border(PanelTokens.borderWidth, OtusColors.strokeBgDark, CircleShape),
    ) {
        Crossfade(refreshing, animationSpec = tween(RefreshTokens.FADE_MILLIS)) { spinning ->
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                if (spinning) {
                    CircularProgressIndicator(
                        Modifier.size((RefreshTokens.arcRadius + RefreshTokens.strokeWidth) * 2),
                        color = OtusColors.text1,
                        strokeWidth = RefreshTokens.strokeWidth,
                    )
                } else {
                    PullArc(progress)
                }
            }
        }
    }
}

@Composable
private fun PullArc(progress: () -> Float) {
    val armed = progress() >= 1f
    val alpha by animateFloatAsState(if (armed) 1f else RefreshTokens.ARMING_ALPHA)
    val arrow = remember { Path() }
    Canvas(Modifier.fillMaxSize()) {
        val arc = ArcValues(progress())
        val radius = RefreshTokens.arcRadius.toPx() + RefreshTokens.strokeWidth.toPx() / 2
        val bounds = Rect(center, radius)
        drawArc(
            OtusColors.text1,
            arc.startAngle,
            arc.endAngle - arc.startAngle,
            useCenter = false,
            topLeft = bounds.topLeft,
            size = bounds.size,
            alpha = alpha,
            style = Stroke(RefreshTokens.strokeWidth.toPx(), cap = StrokeCap.Square),
        )
        drawArrow(arrow, bounds, alpha, arc)
    }
}

/** Arc geometry for a pull. Drawing starts partway to the threshold; overshoot adds damped turn. */
private class ArcValues(
    progress: Float,
) {
    val reveal = max(min(1f, progress) - REVEAL_START, 0f) / (1f - REVEAL_START)
    private val overshoot = (abs(progress) - 1f).coerceIn(0f, MAX_OVERSHOOT)
    private val tension = overshoot - overshoot.pow(2) / (2 * MAX_OVERSHOOT)
    private val turns = (START_TURN + PULL_TURN * reveal + tension) / 2
    val startAngle = turns * FULL_CIRCLE
    val endAngle = (turns + reveal * RefreshTokens.MAX_ARC) * FULL_CIRCLE
    val arrowScale = min(1f, reveal)
}

private const val REVEAL_START = 0.4f
private const val MAX_OVERSHOOT = 2f
private const val START_TURN = -0.25f
private const val PULL_TURN = 0.4f
private const val FULL_CIRCLE = 360f

private fun DrawScope.drawArrow(
    arrow: Path,
    bounds: Rect,
    alpha: Float,
    arc: ArcValues,
) {
    val width = RefreshTokens.arrowWidth.toPx() * arc.arrowScale
    arrow.reset()
    arrow.moveTo(0f, 0f)
    arrow.lineTo(width, 0f)
    arrow.lineTo(width / 2, RefreshTokens.arrowHeight.toPx() * arc.arrowScale)
    arrow.close()
    arrow.translate(
        Offset(
            bounds.width / 2 + bounds.center.x - width / 2,
            bounds.center.y + RefreshTokens.strokeWidth.toPx() / 2,
        ),
    )
    rotate(arc.endAngle) { drawPath(arrow, OtusColors.text1, alpha) }
}
