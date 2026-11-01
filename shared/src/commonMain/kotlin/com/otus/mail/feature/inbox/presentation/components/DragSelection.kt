package com.otus.mail.feature.inbox.presentation.components

/**
 * One drag across rows that started with a long press on the row of message [anchor]. Rows between
 * the anchor and the finger take the anchor's state; every other row keeps its state from [initial].
 */
internal data class DragSelection(
    val ids: List<String>,
    val anchor: String,
    val initial: Set<String>,
) {
    /** The selection with the finger on message [id]; a row no longer listed changes nothing. */
    fun through(id: String): Set<String> {
        val from = ids.indexOf(anchor)
        val to = ids.indexOf(id)
        if (from < 0 || to < 0) return initial
        val range = ids.subList(minOf(from, to), maxOf(from, to) + 1)
        return if (anchor in initial) initial + range else initial - range
    }
}
