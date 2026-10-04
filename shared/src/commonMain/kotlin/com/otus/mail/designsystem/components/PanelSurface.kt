package com.otus.mail.designsystem.components

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import com.otus.mail.designsystem.theme.OtusColors
import com.otus.mail.designsystem.theme.PanelTokens

internal fun Modifier.panelSurface(
    geometry: PanelLayoutState,
    overlap: () -> Float,
): Modifier =
    drawWithCache {
        val border = Stroke(PanelTokens.borderWidth.toPx())
        onDrawBehind {
            geometry.frame(overlap(), PanelTokens.cutoutGap.toPx(), border.width)?.let { drawPanelFrame(it, border) }
        }
    }

private fun DrawScope.drawPanelFrame(
    frame: PanelFrame,
    border: Stroke,
) {
    val viewport = Path().apply { addRect(Rect(frame.viewport.left, 0f, frame.viewport.right, size.height)) }
    // Holes are opaque geometry on their pills. Blending them by opacity would show two half-visible
    // contours while pill groups swap.
    val surface =
        frame.holes.fold(
            Path().apply {
                addRoundRect(
                    RoundRect(
                        Rect(border.width / 2, border.width / 2, size.width - border.width / 2, frame.bottom),
                        CornerRadius(PanelTokens.panelRadius.toPx()),
                    ),
                )
            },
        ) { shape, hole ->
            val rect = hole.bounds
            val cutout = Path().apply { addRoundRect(RoundRect(rect, CornerRadius(rect.height / 2))) }
            val clipped = if (hole.scrolling) Path.combine(PathOperation.Intersect, cutout, viewport) else cutout
            Path.combine(PathOperation.Difference, shape, clipped)
        }
    drawPath(surface, OtusColors.bgDark)
    drawPath(surface, OtusColors.strokeBgDark, style = border)
}
