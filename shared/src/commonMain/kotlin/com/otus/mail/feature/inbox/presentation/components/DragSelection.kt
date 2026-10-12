package com.otus.mail.feature.inbox.presentation.components

/**
 * One drag across rows that started with a long press on row [anchor]. Rows between the anchor and
 * the finger take the anchor's state; every other row keeps its state from [initial].
 */
internal data class DragSelection(
    val ids: List<String>,
    val anchor: Int,
    val initial: Set<String>,
) {
    fun through(index: Int): Set<String> {
        val range = ids.subList(minOf(anchor, index), maxOf(anchor, index) + 1)
        return if (ids[anchor] in initial) initial + range else initial - range
    }
}
