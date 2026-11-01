package com.otus.mail.feature.inbox.presentation.components

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.LazyListItemInfo
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
 * Only the rows of [ids] take part; other items, such as a notice or the loading footer, do not.
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
                val anchor = listState.rowAt(down.position.y, ids(), clamp = false)
                if (anchor == null || !awaitHold(down)) return@awaitEachGesture
                val drag = RowDrag(listState, anchor, ids, { DragSelection(ids(), anchor, selectedIds()) }, onSelect)
                var scroll: Job? = null
                while (true) {
                    val change =
                        awaitPointerEvent(PointerEventPass.Initial).changes.firstOrNull { it.id == down.id }
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
    private val anchor: String,
    private val ids: () -> List<String>,
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
        val id = listState.rowAt(y, ids(), clamp = true) ?: return
        // The row's long press toggles the anchor, so read the selection once the finger leaves it.
        if (selection == null && id == anchor) return
        var active = selection
        if (active == null) {
            active = start()
            selection = active
        }
        onSelect(active.through(id))
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

/** The id of the message row at [y], measured from the list's top; see [messageAt]. */
private fun LazyListState.rowAt(
    y: Float,
    ids: List<String>,
    clamp: Boolean,
): String? = layoutInfo.visibleItemsInfo.messageAt(y + layoutInfo.viewportStartOffset, ids, clamp)

/**
 * The id of the row of [ids] at [itemY], in item offsets. Items that are not one of [ids], such as
 * a notice above the rows or the loading footer below them, are left out. With [clamp], a point
 * outside the rows picks the nearest one, so a drag into the padding or onto the footer still
 * reaches the end rows.
 */
internal fun List<LazyListItemInfo>.messageAt(
    itemY: Float,
    ids: List<String>,
    clamp: Boolean,
): String? {
    val rows = filter { item -> item.key.let { it is String && it in ids } }
    val row =
        rows.firstOrNull { itemY >= it.offset && itemY < it.offset + it.size }
            ?: (rows.lastOrNull { it.offset <= itemY } ?: rows.firstOrNull()).takeIf { clamp }
    return row?.key as? String
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
