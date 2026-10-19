package com.otus.mail.feature.inbox.presentation.components

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import kotlin.math.abs

/**
 * Opens or folds a strip with a vertical swipe: up runs [onUp], down runs [onDown], and a null one
 * ignores that way. A swipe counts once it moves more down than across, so it leaves sideways
 * swipes to the pages, and once it goes [FOLD_DP] or is let go at [FLICK_DP_PER_SECOND]. Inside a
 * list that scrolls, it counts only what the list leaves over at its top or bottom, so the list
 * scrolls first.
 */
@Composable
internal fun rememberFoldSwipe(
    onUp: (() -> Unit)? = null,
    onDown: (() -> Unit)? = null,
): Modifier {
    val density = LocalDensity.current
    val up by rememberUpdatedState(onUp)
    val down by rememberUpdatedState(onDown)
    val fold = remember(density) { FoldThresholds(density) { dy -> (if (dy < 0) up else down)?.invoke() } }
    return Modifier
        .nestedScroll(fold.leftover)
        .pointerInput(fold) { awaitEachGesture { awaitVerticalSwipe(fold) } }
}

/** When a vertical move or flick of pixels counts, and what runs then with its direction. */
private class FoldThresholds(
    density: Density,
    private val onFold: (Float) -> Unit,
) {
    private val distance = with(density) { FOLD_DP.dp.toPx() }
    private val flick = with(density) { FLICK_DP_PER_SECOND.dp.toPx() }

    /** Runs the fold for a swipe that moved [dy] and was let go at [velocity]; nothing below the thresholds. */
    fun release(
        dy: Float,
        velocity: Float,
    ) {
        val way = if (abs(dy) >= distance) dy else velocity.takeIf { abs(it) >= flick && it * dy >= 0 } ?: 0f
        if (way != 0f) onFold(way)
    }

    /** What a list inside leaves over at its edges: a pull past its top or bottom, then the fling. */
    val leftover =
        object : NestedScrollConnection {
            private var pulled = 0f

            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource,
            ): Offset {
                if (source == NestedScrollSource.UserInput) pulled += available.y
                return Offset.Zero
            }

            override suspend fun onPostFling(
                consumed: Velocity,
                available: Velocity,
            ): Velocity {
                if (pulled != 0f) release(pulled, available.y)
                pulled = 0f
                return Velocity.Zero
            }
        }
}

private suspend fun AwaitPointerEventScope.awaitVerticalSwipe(fold: FoldThresholds) {
    val down = awaitFirstDown(requireUnconsumed = false)
    var travel = Offset.Zero
    var change: PointerInputChange?
    // Children, such as a list's scroll, see each move first until the swipe counts.
    do {
        change = awaitPointerEvent().changes.firstOrNull { it.id == down.id }?.takeIf { it.pressed && !it.isConsumed }
        change?.let { travel += it.positionChange() }
    } while (change != null && travel.getDistance() < viewConfiguration.touchSlop)
    if (change == null || abs(travel.y) <= abs(travel.x)) return
    change.consume()
    val (dy, velocity) = followVertical(down.id, travel.y)
    fold.release(dy, velocity)
}

/** Follows pointer [id] until it lifts, taking its moves, and returns how far down it went and how fast. */
private suspend fun AwaitPointerEventScope.followVertical(
    id: PointerId,
    start: Float,
): Pair<Float, Float> {
    val tracker = VelocityTracker()
    var dy = start
    var change = awaitPointerEvent().changes.firstOrNull { it.id == id }
    while (change != null && change.pressed) {
        tracker.addPosition(change.uptimeMillis, change.position)
        dy += change.positionChange().y
        change.consume()
        change = awaitPointerEvent().changes.firstOrNull { it.id == id }
    }
    return dy to tracker.calculateVelocity().y
}

/** How far a swipe must go to open or fold a strip. */
private const val FOLD_DP = 32

/** How fast a shorter swipe must be let go to still open or fold a strip. */
private const val FLICK_DP_PER_SECOND = 300
