package com.otus.mail.feature.inbox.presentation.components

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.Composable
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * Turns [pager]'s pages from a sideways drag anywhere it is applied, such as the pages and the
 * thread strip. A drag only counts once it moves [SIDEWAYS_RATIO] times as far across as down, so
 * a scroll that drifts sideways stays a scroll. Letting go past half the width, or after a flick
 * of [FLICK_DP_PER_SECOND], turns one page; anything less springs back. Screen readers page with
 * the page left and page right actions.
 */
@Composable
internal fun rememberPageSwipe(pager: PagerState): Modifier {
    val scope = rememberCoroutineScope()
    // In right-to-left layouts the pager lays its pages out mirrored, so the finger maps the other way.
    val across = if (LocalLayoutDirection.current == LayoutDirection.Rtl) 1f else -1f
    // The page to the right is the next one, or the previous one when mirrored.
    val right = if (across < 0) 1 else -1
    return Modifier
        .pointerInput(pager, across) { swipePages(pager, scope, across) }
        .semantics {
            pageLeft { scope.turn(pager, pager.currentPage - right) }
            pageRight { scope.turn(pager, pager.currentPage + right) }
        }
}

private suspend fun PointerInputScope.swipePages(
    pager: PagerState,
    scope: CoroutineScope,
    across: Float,
) = awaitEachGesture {
    val down = awaitFirstDown(requireUnconsumed = false)
    val start = pager.currentPage
    if (awaitSideways(down.id)) {
        val drag = followDrag(down.id) { dx -> pager.dispatchRawDelta(dx * across) }
        scope.turn(pager, start + drag.step(across, size.width / 2f, FLICK_DP_PER_SECOND.dp.toPx()))
    }
}

/**
 * Whether pointer [id] passes the touch slop clearly sideways. It is not once it lifts, heads
 * down, or a child, such as the page's scroll, takes it; children see each move first until then.
 */
private suspend fun AwaitPointerEventScope.awaitSideways(id: PointerId): Boolean {
    var travel = Offset.Zero
    var change: PointerInputChange?
    do {
        change = awaitPointerEvent().changes.firstOrNull { it.id == id }?.takeIf { it.pressed && !it.isConsumed }
        change?.let { travel += it.positionChange() }
    } while (change != null && travel.getDistance() < viewConfiguration.touchSlop)
    return change != null && abs(travel.x) >= abs(travel.y) * SIDEWAYS_RATIO
}

/**
 * Hands each sideways move of pointer [id] to [onDelta] until it lifts. It takes the moves before
 * the children, so the page does not scroll while it turns.
 */
private suspend fun AwaitPointerEventScope.followDrag(
    id: PointerId,
    onDelta: (Float) -> Unit,
): PageDrag {
    val tracker = VelocityTracker()
    var distance = 0f
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
        halfWidth: Float,
        flick: Float,
    ): Int {
        val forward = distance * across
        val speed = velocity * across
        return when {
            forward > halfWidth || (forward > 0 && speed > flick) -> 1
            forward < -halfWidth || (forward < 0 && speed < -flick) -> -1
            else -> 0
        }
    }
}

private fun CoroutineScope.turn(
    pager: PagerState,
    page: Int,
): Boolean {
    launch { pager.animateScrollToPage(page.coerceIn(0, (pager.pageCount - 1).coerceAtLeast(0))) }
    return true
}

/** How many times further across than down a drag must move to turn the page. */
private const val SIDEWAYS_RATIO = 2f

/** How fast a short drag must be let go to still turn the page. */
private const val FLICK_DP_PER_SECOND = 800
