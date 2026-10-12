package com.otus.mail.feature.inbox.presentation.components

import kotlin.test.Test
import kotlin.test.assertEquals

class DragSelectionTest {
    private val ids = listOf("a", "b", "c", "d", "e")

    @Test
    fun draggingFromASelectedRowSelectsEveryRowPassed() {
        val drag = DragSelection(ids, anchor = 1, initial = setOf("b", "e"))
        assertEquals(setOf("b", "c", "d", "e"), drag.through(3))
        assertEquals(setOf("a", "b", "e"), drag.through(0))
    }

    @Test
    fun draggingFromADeselectedRowClearsEveryRowPassed() {
        val drag = DragSelection(ids, anchor = 3, initial = setOf("a", "b", "c", "e"))
        assertEquals(setOf("a", "e"), drag.through(1))
    }

    @Test
    fun draggingBackRestoresRowsTheFingerLeft() {
        val drag = DragSelection(ids, anchor = 0, initial = setOf("a"))
        drag.through(4)
        assertEquals(setOf("a", "b"), drag.through(1))
    }
}
