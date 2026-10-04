package com.otus.mail.feature.inbox.presentation.components

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitLongPressOrCancellation
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import com.otus.mail.designsystem.theme.CalendarTokens
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** One gesture owner for the viewport keeps the anchor when its month scrolls out of composition. */
@Composable
internal fun Modifier.calendarRangeGesture(
    state: LazyListState,
    targets: CalendarDragTargets,
    start: String?,
    end: String?,
    onRange: (String?, String?) -> Unit,
): Modifier {
    val currentRange by rememberUpdatedState(start to end)
    val updateRange by rememberUpdatedState(onRange)
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    return pointerInput(state, targets, rtl) {
        val edge = CalendarTokens.dragScrollEdge.toPx()
        val speed = CalendarTokens.dragScrollSpeed.toPx()
        coroutineScope {
            val scope = this
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                val anchor = targets.dateAt(down.position, rtl)
                if (anchor != null) {
                    val previous = currentRange
                    val held = awaitLongPressOrCancellation(down.id)
                    if (held != null) {
                        var position = held.position

                        fun update() {
                            val date = targets.dateAt(position, rtl) ?: return
                            updateRange(minOf(anchor, date).toString(), maxOf(anchor, date).toString())
                        }
                        held.consume()
                        update()
                        val scrolling =
                            scope.launch {
                                var lastFrame = withFrameNanos { it }
                                while (isActive) {
                                    val frame = withFrameNanos { it }
                                    val elapsed = (frame - lastFrame) / CalendarTokens.NANOS_PER_SECOND
                                    lastFrame = frame
                                    val direction = edgeScroll(position, size.height.toFloat(), edge)
                                    if (direction != 0f) state.scrollBy(direction * speed * elapsed)
                                    update()
                                }
                            }
                        var completed = false
                        try {
                            while (true) {
                                val event = awaitPointerEvent(PointerEventPass.Initial)
                                val change = event.changes.firstOrNull { it.id == held.id }
                                if (change == null || !change.pressed) {
                                    completed = change?.isConsumed == false
                                    change?.consume()
                                    break
                                }
                                change.consume()
                                position =
                                    Offset(change.position.x, change.position.y.coerceIn(0f, size.height - 1f))
                                update()
                            }
                        } finally {
                            scrolling.cancel()
                            if (!completed) updateRange(previous.first, previous.second)
                        }
                    }
                }
            }
        }
    }
}

private fun edgeScroll(
    position: Offset,
    height: Float,
    edge: Float,
): Float =
    when {
        position.y < edge -> -((edge - position.y) / edge).coerceIn(0f, 1f)
        position.y > height - edge -> ((position.y - height + edge) / edge).coerceIn(0f, 1f)
        else -> 0f
    }
