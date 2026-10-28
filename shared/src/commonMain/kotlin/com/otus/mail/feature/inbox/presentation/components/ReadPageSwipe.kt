package com.otus.mail.feature.inbox.presentation.components

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.pageLeft
import androidx.compose.ui.semantics.pageRight
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.otus.designsystem.theme.motion.MotionCurve
import com.otus.designsystem.theme.motion.MotionSpeed
import com.otus.designsystem.theme.motion.motionTween
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.sign

/**
 * How hard a drag must be to turn the pages: it counts once it moves [sideways] times as far
 * across as down, and letting go past [turnAt] of the width, or after a flick of [flickDp] per
 * second, turns the page.
 */
internal enum class SwipeFeel(
    val sideways: Float,
    val turnAt: Float,
    val flickDp: Int,
) {
    /** The message itself, which also scrolls down, so a scroll that drifts sideways stays a scroll. */
    Pages(sideways = 2f, turnAt = 0.5f, flickDp = 800),

    /** The thread strip, which never scrolls, so a short swipe on it is enough. */
    Strip(sideways = 1f, turnAt = 0.2f, flickDp = 300),
}

/**
 * Turns [pager]'s pages from a sideways drag anywhere it is applied, such as the pages and the
 * thread strip, as hard as [feel] asks. Letting go short of it eases back. Screen readers page
 * with the page left and page right actions.
 */
@Composable
internal fun rememberPageSwipe(
    pager: PagerState,
    feel: SwipeFeel = SwipeFeel.Pages,
): Modifier {
    val scope = rememberCoroutineScope()
    // Letting go slows the page to a stop on the design system's easing, not a spring.
    val settle = motionTween<Float>(MotionSpeed.Normal, MotionCurve.Enter)
    // Kept across recompositions, which the moving pages cause, so a drag is not restarted.
    val turn = remember(pager, scope, settle) { PageTurn(pager, scope, settle) }
    // In right-to-left layouts the pager lays its pages out mirrored, so the finger maps the other way.
    val across = if (LocalLayoutDirection.current == LayoutDirection.Rtl) 1f else -1f
    // The page to the right is the next one, or the previous one when mirrored.
    val right = if (across < 0) 1 else -1
    return Modifier
        .pointerInput(turn, across, feel) { swipePages(turn, across, feel) }
        .semantics {
            pageLeft { turn.to(pager.currentPage - right) }
            pageRight { turn.to(pager.currentPage + right) }
        }
}

private suspend fun PointerInputScope.swipePages(
    turn: PageTurn,
    across: Float,
    feel: SwipeFeel,
) = awaitEachGesture {
    val pager = turn.pager
    val down = awaitFirstDown(requireUnconsumed = false)
    val start = pager.currentPage
    awaitSideways(down.id, feel.sideways)?.let { past ->
        val drag = followDrag(down.id, past) { dx -> pager.dispatchRawDelta(dx * across) }
        turn.to(start + drag.step(across, size.width * feel.turnAt, feel.flickDp.dp.toPx()))
    }
}

/**
 * How far across pointer [id] moved past the touch slop, once it passes it [ratio] times as far
 * across as down; null
 * once it lifts, heads down, or a child, such as the page's scroll, takes it. Children see each
 * move first until then.
 */
private suspend fun AwaitPointerEventScope.awaitSideways(
    id: PointerId,
    ratio: Float,
): Float? {
    var travel = Offset.Zero
    var change: PointerInputChange?
    do {
        change = awaitPointerEvent().changes.firstOrNull { it.id == id }?.takeIf { it.pressed && !it.isConsumed }
        change?.let { travel += it.positionChange() }
    } while (change != null && travel.getDistance() < viewConfiguration.touchSlop)
    val sideways = change != null && abs(travel.x) >= abs(travel.y) * ratio
    return if (sideways) travel.x - sign(travel.x) * viewConfiguration.touchSlop else null
}

/**
 * Hands [past], the move beyond the slop, then each sideways move of pointer [id] to [onDelta]
 * until it lifts, so the pages keep up with the finger. It takes the moves before the children,
 * so the page does not scroll while it turns.
 */
private suspend fun AwaitPointerEventScope.followDrag(
    id: PointerId,
    past: Float,
    onDelta: (Float) -> Unit,
): PageDrag {
    val tracker = VelocityTracker()
    var distance = past
    onDelta(past)
    var change = awaitPointerEvent(PointerEventPass.Initial).changes.firstOrNull { it.id == id }
    while (change != null && change.pressed) {
        val dx = change.positionChange().x
        tracker.addPosition(change.uptimeMillis, change.position)
        distance += dx
        onDelta(dx)
        change.consume()
        change = awaitPointerEvent(PointerEventPass.Initial).changes.firstOrNull { it.id == id }
    }
    return PageDrag(distance, tracker.calculateVelocity().x)
}

/** How far across a finger dragged the pages and how fast it was let go, in pixels. */
private class PageDrag(
    val distance: Float,
    val velocity: Float,
) {
    /** One page on, one back, or none; a move against [across] heads to the next page. */
    fun step(
        across: Float,
        turnDistance: Float,
        flick: Float,
    ): Int {
        val forward = distance * across
        val speed = velocity * across
        return when {
            forward > turnDistance || (forward > 0 && speed > flick) -> 1
            forward < -turnDistance || (forward < 0 && speed < -flick) -> -1
            else -> 0
        }
    }
}

/** Turns [pager] to a page in [scope], easing in with [settle]. */
private class PageTurn(
    val pager: PagerState,
    private val scope: CoroutineScope,
    private val settle: AnimationSpec<Float>,
) {
    /** Animates to [page], kept within the pages; true for the screen reader action that asked. */
    fun to(page: Int): Boolean {
        val last = (pager.pageCount - 1).coerceAtLeast(0)
        scope.launch { pager.animateScrollToPage(page.coerceIn(0, last), animationSpec = settle) }
        return true
    }
}

/**
 * The bottom strips' gestures: the thread strip's [swipe], which turns the pages and drags its
 * message list open, the pages' [position] its content follows, and the folders strip's [folders]
 * drag, which pulls the folders sheet up.
 */
internal class StripGestures(
    val swipe: Modifier,
    val position: () -> Float,
    val folders: Modifier,
)
