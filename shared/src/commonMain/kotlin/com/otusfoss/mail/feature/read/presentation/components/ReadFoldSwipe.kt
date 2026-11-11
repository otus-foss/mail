package com.otusfoss.mail.feature.read.presentation.components

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import com.otusfoss.core.designsystem.shared.overlays.components.SheetPull
import com.otusfoss.core.designsystem.shared.rows.components.Unfold
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.sign

/** What a vertical drag on a strip moves: it follows the finger, then settles once let go. */
internal interface FoldDrag {
    /** Whether it rests part of the way, so a list inside hands it the drag before scrolling. */
    val moving: Boolean

    /** Follows a finger that moved [dy] pixels, up when negative. */
    fun dragBy(dy: Float)

    /** Settles after the finger let go at [velocity] pixels a second, down when positive. */
    fun release(velocity: Float)
}

/**
 * Lets a vertical drag move [drag] with the finger: up unfolds and down folds. A drag counts once
 * it moves more down than across, so sideways swipes stay with the pages. Inside a list that
 * scrolls, it takes what the list leaves over at its top or bottom, and while [drag] is part of the
 * way it takes the drag before the list scrolls.
 */
@Composable
internal fun rememberFoldSwipe(drag: FoldDrag): Modifier {
    val connection = remember(drag) { FoldScrollConnection(drag) }
    return Modifier
        .nestedScroll(connection)
        .pointerInput(drag) { awaitEachGesture { awaitVerticalDrag(drag) } }
}

/** A [FoldDrag] that unfolds [unfold], then runs [onSettled] with whether it ended open. */
@Composable
internal fun rememberUnfoldDrag(
    unfold: Unfold,
    onSettled: (Boolean) -> Unit,
): FoldDrag {
    val scope = rememberCoroutineScope()
    val flick = with(LocalDensity.current) { FLICK_DP_PER_SECOND.dp.toPx() }
    val settled by rememberUpdatedState(onSettled)
    return remember(unfold, scope, flick) {
        object : FoldDrag {
            override val moving: Boolean
                get() = unfold.moving

            override fun dragBy(dy: Float) = unfold.dragBy(dy)

            override fun release(velocity: Float) {
                scope.launch { settled(unfold.settle(velocity, flick)) }
            }
        }
    }
}

/**
 * A [FoldDrag] that pulls a sheet up from outside it: the first upward move runs [onStart], which
 * opens the sheet with a new [SheetPull], and the finger moves that pull from then on.
 */
internal class PullDrag(
    private val onStart: () -> SheetPull,
) : FoldDrag {
    private var pull: SheetPull? = null

    override val moving: Boolean = false

    override fun dragBy(dy: Float) {
        val current = pull ?: if (dy < 0) onStart().also { pull = it } else return
        current.dragBy(dy)
    }

    override fun release(velocity: Float) {
        pull?.release(velocity)
        pull = null
    }
}

/** Hands [drag] what a list inside leaves over, and the whole drag while [drag] is part of the way. */
private class FoldScrollConnection(
    private val drag: FoldDrag,
) : NestedScrollConnection {
    private var dragged = false

    override fun onPreScroll(
        available: Offset,
        source: NestedScrollSource,
    ): Offset {
        if (source != NestedScrollSource.UserInput || !drag.moving) return Offset.Zero
        dragged = true
        drag.dragBy(available.y)
        return Offset(0f, available.y)
    }

    override fun onPostScroll(
        consumed: Offset,
        available: Offset,
        source: NestedScrollSource,
    ): Offset {
        if (source != NestedScrollSource.UserInput || available.y == 0f) return Offset.Zero
        dragged = true
        drag.dragBy(available.y)
        return Offset(0f, available.y)
    }

    override suspend fun onPreFling(available: Velocity): Velocity {
        if (!dragged) return Velocity.Zero
        dragged = false
        drag.release(available.y)
        return available
    }
}

private suspend fun AwaitPointerEventScope.awaitVerticalDrag(drag: FoldDrag) {
    val down = awaitFirstDown(requireUnconsumed = false)
    var travel = Offset.Zero
    var change: PointerInputChange?
    // Children, such as a list's scroll, see each move first until the drag counts.
    do {
        change = awaitPointerEvent().changes.firstOrNull { it.id == down.id }?.takeIf { it.pressed && !it.isConsumed }
        change?.let { travel += it.positionChange() }
    } while (change != null && travel.getDistance() < viewConfiguration.touchSlop)
    if (change == null || abs(travel.y) <= abs(travel.x)) return
    change.consume()
    drag.dragBy(travel.y - sign(travel.y) * viewConfiguration.touchSlop)
    drag.release(followVertical(down.id, drag))
}

/** Hands each vertical move of pointer [id] to [drag] until it lifts, and returns how fast it was let go. */
private suspend fun AwaitPointerEventScope.followVertical(
    id: PointerId,
    drag: FoldDrag,
): Float {
    val tracker = VelocityTracker()
    var change = awaitPointerEvent().changes.firstOrNull { it.id == id }
    while (change != null && change.pressed) {
        tracker.addPosition(change.uptimeMillis, change.position)
        drag.dragBy(change.positionChange().y)
        change.consume()
        change = awaitPointerEvent().changes.firstOrNull { it.id == id }
    }
    return tracker.calculateVelocity().y
}

/** How fast a drag must be let go to open or fold the rest of the way from wherever it is. */
private const val FLICK_DP_PER_SECOND = 300
