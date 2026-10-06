package com.otus.mail.designsystem.components

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitHorizontalTouchSlopOrCancellation
import androidx.compose.foundation.gestures.horizontalDrag
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.input.pointer.util.addPointerInputChange
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import com.otus.mail.designsystem.theme.SidebarTokens
import kotlinx.coroutines.launch

/**
 * Hosts the page beneath a [Sidebar]. A swipe toward the open side that starts on the page's
 * context panel, or anywhere above it, pulls the sidebar out. Controls that scroll sideways,
 * such as the chip rail, keep their own swipes.
 */
@Composable
fun SidebarSwipeArea(
    state: SidebarState,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable () -> Unit,
) {
    val zone = remember { SidebarOpenZone() }
    val scope = rememberCoroutineScope()
    val canOpen = rememberUpdatedState(enabled && !state.isOpen)
    val sign = if (LocalLayoutDirection.current == LayoutDirection.Rtl) -1f else 1f
    val flingThreshold = with(LocalDensity.current) { SidebarTokens.flingVelocity.toPx() }
    Box(
        modifier
            .fillMaxSize()
            .onGloballyPositioned { zone.host = it }
            .pointerInput(state, sign, flingThreshold) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    if (!canOpen.value || !zone.contains(down.position.y)) return@awaitEachGesture
                    val velocity = VelocityTracker()
                    // Only a swipe toward the open side claims the gesture; anything else passes through.
                    val start =
                        awaitHorizontalTouchSlopOrCancellation(down.id) { change, over ->
                            if (over * sign > 0f) {
                                change.consume()
                                scope.launch { state.stop() }
                                state.dragBy(over * sign)
                            }
                        } ?: return@awaitEachGesture
                    velocity.addPointerInputChange(start)
                    horizontalDrag(start.id) { change ->
                        velocity.addPointerInputChange(change)
                        state.dragBy((change.position.x - change.previousPosition.x) * sign)
                        change.consume()
                    }
                    val release = velocity.calculateVelocity().x * sign
                    scope.launch { state.settle(release, flingThreshold) }
                }
            },
    ) {
        CompositionLocalProvider(LocalSidebarOpenZone provides zone, content = content)
    }
}

/** Marks the bottom of the area where a swipe opens the sidebar. Has no effect outside [SidebarSwipeArea]. */
@Composable
internal fun Modifier.sidebarOpenZone(): Modifier {
    val zone = LocalSidebarOpenZone.current ?: return this
    return onGloballyPositioned { zone.panel = it }
}

private class SidebarOpenZone {
    var host: LayoutCoordinates? = null
    var panel: LayoutCoordinates? = null

    fun contains(y: Float): Boolean {
        val host = host?.takeIf { it.isAttached }
        val panel = panel?.takeIf { it.isAttached }
        return host != null && panel != null && y <= host.localBoundingBoxOf(panel, clipBounds = false).bottom
    }
}

private val LocalSidebarOpenZone = staticCompositionLocalOf<SidebarOpenZone?> { null }
