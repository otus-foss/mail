package com.otus.mail.designsystem.components

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.drawscope.Stroke
import com.otus.mail.designsystem.theme.OtusColors
import com.otus.mail.designsystem.theme.PanelTokens

internal fun Modifier.panelSurface(
    geometry: PanelLayoutState,
    origin: Offset,
    headerHeight: Float,
    railHeight: Float,
    overlap: Float,
): Modifier =
    drawWithCache {
        val bottom = headerHeight + railHeight / 2 * overlap
        val border = PanelTokens.borderWidth.toPx()
        var surface =
            Path().apply {
                addRoundRect(
                    RoundRect(
                        Rect(border / 2, border / 2, size.width - border / 2, bottom),
                        CornerRadius(PanelTokens.panelRadius.toPx()),
                    ),
                )
            }
        if (overlap > 0f) {
            val viewport = Path().apply { addRect(geometry.viewport.translate(-origin)) }
            geometry.items.forEach { (id, bounds) ->
                var cutout =
                    Path().apply {
                        val rect = bounds.translate(-origin).inflate(PanelTokens.cutoutGap.toPx())
                        addRoundRect(RoundRect(rect, CornerRadius(rect.height / 2)))
                    }
                if (id != ActionKey) cutout = Path.combine(PathOperation.Intersect, cutout, viewport)
                surface = Path.combine(PathOperation.Difference, surface, cutout)
            }
        }
        onDrawBehind {
            drawPath(surface, OtusColors.bgDark)
            drawPath(surface, OtusColors.strokeBgDark, style = Stroke(border))
        }
    }
