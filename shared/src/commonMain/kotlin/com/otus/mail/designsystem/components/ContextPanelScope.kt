package com.otus.mail.designsystem.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import com.otus.mail.designsystem.elements.LocalControlVisualBounds
import com.otus.mail.designsystem.theme.MotionTokens
import com.otus.mail.designsystem.theme.PanelTokens

/** Wrap each chip in [item] so its measured position drives its own cutout. */
class ContextPanelScope internal constructor(
    private val geometry: PanelLayoutState,
    private val namespace: Any? = null,
) {
    @Composable
    fun item(
        id: Any,
        content: @Composable () -> Unit,
    ) {
        val itemKey = if (namespace == null) id else namespace to id
        key(itemKey) {
            DisposableEffect(itemKey) { onDispose { geometry.items.remove(itemKey) } }
            CompositionLocalProvider(LocalControlVisualBounds provides { geometry.items[itemKey] = it }) {
                content()
            }
        }
    }

    /** Replace a pill group with motion while keeping every pill's cutout attached to its layout. */
    @Composable
    fun transition(
        expanded: Boolean,
        content: @Composable ContextPanelScope.(Boolean) -> Unit,
    ) {
        val distance = with(LocalDensity.current) { PanelTokens.chipMotionDistance.roundToPx() }
        AnimatedContent(
            targetState = expanded,
            transitionSpec = {
                val offset = if (targetState) distance else -distance
                val movement =
                    tween<IntOffset>(
                        PanelTokens.MODE_TRANSITION_MILLIS,
                        easing = MotionTokens.standard,
                    )
                (
                    (
                        fadeIn(
                            tween(PanelTokens.MODE_TRANSITION_MILLIS),
                        ) + slideInVertically(movement) { offset }
                    ) togetherWith
                        (fadeOut(tween(PanelTokens.MODE_TRANSITION_MILLIS)) + slideOutVertically(movement) { -offset })
                ).using(
                    SizeTransform(
                        clip = false,
                    ) { _, _ -> tween(PanelTokens.MODE_TRANSITION_MILLIS, easing = MotionTokens.standard) },
                )
            },
            label = "Panel pill group",
        ) { state ->
            val scope = remember(geometry, state) { ContextPanelScope(geometry, state) }
            Row(horizontalArrangement = Arrangement.spacedBy(PanelTokens.chipGap)) { scope.content(state) }
        }
    }
}

internal class PanelLayoutState {
    val items = mutableStateMapOf<Any, Rect>()
    var viewport by mutableStateOf(Rect.Zero)
}

@Composable
internal fun PanelChipRail(
    modifier: Modifier,
    geometry: PanelLayoutState,
    scroll: ScrollState,
    content: @Composable () -> Unit,
) {
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    Box(
        modifier
            .onGloballyPositioned { geometry.viewport = it.boundsInRoot() }
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
            .drawWithContent {
                drawContent()
                val fade = PanelTokens.fadeWidth.toPx().coerceAtMost(size.width / 2)
                if (scroll.canScrollForward) {
                    drawRect(
                        Brush.horizontalGradient(
                            if (rtl) listOf(Color.Transparent, Color.Black) else listOf(Color.Black, Color.Transparent),
                            startX = if (rtl) 0f else size.width - fade,
                            endX = if (rtl) fade else size.width,
                        ),
                        blendMode = BlendMode.DstIn,
                    )
                }
                if (scroll.canScrollBackward) {
                    drawRect(
                        Brush.horizontalGradient(
                            if (rtl) listOf(Color.Black, Color.Transparent) else listOf(Color.Transparent, Color.Black),
                            startX = if (rtl) size.width - fade else 0f,
                            endX = if (rtl) size.width else fade,
                        ),
                        blendMode = BlendMode.DstIn,
                    )
                }
            },
    ) {
        // Stretching only the rail would separate the pills from the panel's cutouts.
        Row(
            Modifier.horizontalScroll(scroll, overscrollEffect = null),
            horizontalArrangement = Arrangement.spacedBy(PanelTokens.chipGap),
        ) {
            content()
        }
    }
}
