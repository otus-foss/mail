package com.otus.mail.feature.inbox.presentation.components

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

/**
 * Long-press a row, then drag up or down to give every row passed the pressed row's new state. The
 * row's own long press toggles it and holds the touch, so the list neither scrolls nor swipes; this
 * only follows the finger, and scrolls the list while the finger is near its top or bottom edge.
 */
internal fun Modifier.dragToSelect(
    listState: LazyListState,
    ids: () -> List<String>,
    selectedIds: () -> Set<String>,
    onSelect: (Set<String>) -> Unit,
): Modifier =
    pointerInput(listState) {
        val edge = EDGE.toPx()
        val maxStep = MAX_SCROLL_STEP.toPx()
        coroutineScope {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                val anchor = listState.rowAt(down.position.y, clamp = false)
                if (anchor == null || !awaitHold(down)) return@awaitEachGesture
                val drag = RowDrag(listState, anchor, { DragSelection(ids(), anchor, selectedIds()) }, onSelect)
                var scroll: Job? = null
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    val change = event.changes.firstOrNull { it.id == down.id }
                    if (change == null || !change.pressed) break
                    drag.moveTo(change.position.y, listState.edgeStep(change.position.y, edge, maxStep))
                    if (drag.step != 0f && scroll?.isActive != true) scroll = launch { drag.scroll() }
                }
                scroll?.cancel()
            }
        }
    }

/** Where the finger is during one drag, and how fast the list scrolls under it. */
private class RowDrag(
    private val listState: LazyListState,
    private val anchor: Int,
    private val start: () -> DragSelection,
    private val onSelect: (Set<String>) -> Unit,
) {
    private var selection: DragSelection? = null
    private var y = 0f
    var step = 0f
        private set

    fun moveTo(
        y: Float,
        step: Float,
    ) {
        this.y = y
        this.step = step
        follow()
    }

    /** Scrolls a step each frame, and selects the rows that pass under the finger, until [step] is zero. */
    suspend fun scroll() {
        while (step != 0f) {
            listState.scrollBy(step)
            follow()
            withFrameNanos {}
        }
    }

    private fun follow() {
        val index = listState.rowAt(y, clamp = true) ?: return
        // The row's long press toggles the anchor, so read the selection once the finger leaves it.
        if (selection == null && index == anchor) return
        var active = selection
        if (active == null) {
            active = start()
            selection = active
        }
        onSelect(active.through(index))
    }
}

/** Waits out a long press; false if the finger lifts or moves first. */
private suspend fun AwaitPointerEventScope.awaitHold(down: PointerInputChange): Boolean =
    withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
        var change: PointerInputChange?
        do {
            change = awaitPointerEvent(PointerEventPass.Initial).changes.firstOrNull { it.id == down.id }
        } while (
            change != null &&
            change.pressed &&
            (change.position - down.position).getDistance() <= viewConfiguration.touchSlop
        )
        false
    } ?: true

/**
 * The index of the row at [y], measured from the list's top. With [clamp], a point above or below
 * the rows picks the nearest one, so a drag into the padding still reaches the end rows.
 */
private fun LazyListState.rowAt(
    y: Float,
    clamp: Boolean,
): Int? {
    val itemY = y + layoutInfo.viewportStartOffset
    val rows = layoutInfo.visibleItemsInfo
    val row = rows.firstOrNull { itemY >= it.offset && itemY < it.offset + it.size }
    return row?.index ?: if (!clamp) null else (rows.lastOrNull { it.offset <= itemY } ?: rows.firstOrNull())?.index
}

/** Pixels to scroll each frame: faster the deeper the finger sits in the top or bottom [edge]. */
private fun LazyListState.edgeStep(
    y: Float,
    edge: Float,
    maxStep: Float,
): Float {
    val itemY = y + layoutInfo.viewportStartOffset
    val bottom = layoutInfo.viewportEndOffset - layoutInfo.afterContentPadding
    return when {
        itemY < edge -> -maxStep * ((edge - itemY) / edge).coerceAtMost(1f)
        itemY > bottom - edge -> maxStep * ((itemY - bottom + edge) / edge).coerceAtMost(1f)
        else -> 0f
    }
}

private val EDGE = 64.dp
private val MAX_SCROLL_STEP = 16.dp
