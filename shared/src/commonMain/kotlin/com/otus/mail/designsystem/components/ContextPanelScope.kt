package com.otus.mail.designsystem.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import com.otus.mail.designsystem.elements.LocalControlVisualBounds
import com.otus.mail.designsystem.theme.MotionTokens
import com.otus.mail.designsystem.theme.PanelTokens

/** Wrap each chip in [item] so its measured position drives its own cutout. */
class ContextPanelScope internal constructor(
    private val geometry: PanelLayoutState,
    private val namespace: Any? = null,
    private val visibility: PillVisibility = PillVisibility({ 1f }, { true }),
) {
    @Composable
    fun item(
        id: Any,
        content: @Composable () -> Unit,
    ) {
        val itemKey = if (namespace == null) id else namespace to id
        key(itemKey) {
            DisposableEffect(itemKey) { onDispose { geometry.removeItem(itemKey) } }
            CompositionLocalProvider(
                LocalControlVisualBounds provides { geometry.trackItem(itemKey, it, visibility) },
            ) {
                content()
            }
        }
    }

    /** Crossfade and resize pill groups around one rail anchor. Cutouts stay on their pills. */
    @Composable
    fun transition(
        expanded: Boolean,
        content: @Composable ContextPanelScope.(Boolean) -> Unit,
    ) {
        AnimatedContent(
            targetState = expanded,
            contentAlignment = Alignment.CenterStart,
            transitionSpec = {
                // Moving an outgoing cutout above the rail creates a second contour that snaps back
                // to the incoming pill as the old one fades. Animate size and opacity at a stable Y.
                (EnterTransition.None togetherWith ExitTransition.None).using(
                    SizeTransform(
                        clip = false,
                    ) { _, _ -> tween(PanelTokens.MODE_TRANSITION_MILLIS, easing = MotionTokens.standard) },
                )
            },
            label = "Panel pill group",
        ) { state ->
            // The pill layer's fade, also used by any cutout that must appear or leave under the surface.
            val visibility =
                transition.animateFloat(
                    transitionSpec = { tween(PanelTokens.MODE_TRANSITION_MILLIS, easing = MotionTokens.standard) },
                    label = "Panel pill visibility",
                ) { if (it == EnterExitState.Visible) 1f else 0f }
            val scope =
                remember(geometry, state, visibility) {
                    ContextPanelScope(
                        geometry,
                        state,
                        PillVisibility({ visibility.value }, { transition.targetState == EnterExitState.Visible }),
                    )
                }
            Row(
                Modifier.graphicsLayer { alpha = visibility.value },
                horizontalArrangement = Arrangement.spacedBy(PanelTokens.chipGap),
            ) { scope.content(state) }
        }
    }
}

@Composable
internal fun PanelChipRail(
    modifier: Modifier,
    geometry: PanelLayoutState,
    scroll: ScrollState,
    content: @Composable () -> Unit,
) {
    Box(
        modifier
            .onGloballyPositioned(geometry::trackViewport)
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
            .drawWithContent {
                drawContent()
                // Fade by the pills' actual overflow. Scroll extent lags a resizing group, and an on/off
                // fade would snap when overflow begins or ends.
                val fade = PanelTokens.fadeWidth.toPx().coerceAtMost(size.width / 2)
                val overflow = geometry.railOverflow()
                drawEdgeFade((overflow.left / fade).coerceIn(0f, 1f), fade, atEnd = false)
                drawEdgeFade((overflow.right / fade).coerceIn(0f, 1f), fade, atEnd = true)
            },
    ) {
        // Span the viewport so pills outside a resizing group are not clipped at its animated width.
        // Stretching only the rail would separate the pills from the panel's cutouts.
        Row(
            Modifier.fillMaxWidth().horizontalScroll(scroll, overscrollEffect = null),
            horizontalArrangement = Arrangement.spacedBy(PanelTokens.chipGap),
        ) {
            content()
        }
    }
}

private fun DrawScope.drawEdgeFade(
    strength: Float,
    fade: Float,
    atEnd: Boolean,
) {
    if (strength == 0f) return
    val edge = Color.Black.copy(alpha = 1f - strength)
    drawRect(
        Brush.horizontalGradient(
            if (atEnd) listOf(Color.Black, edge) else listOf(edge, Color.Black),
            startX = if (atEnd) size.width - fade else 0f,
            endX = if (atEnd) size.width else fade,
        ),
        topLeft = Offset(if (atEnd) size.width - fade else 0f, 0f),
        size = Size(fade, size.height),
        blendMode = BlendMode.DstIn,
    )
}
