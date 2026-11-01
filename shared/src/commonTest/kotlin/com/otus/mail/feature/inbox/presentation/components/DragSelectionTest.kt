package com.otus.mail.feature.inbox.presentation.components

import kotlin.test.Test
import kotlin.test.assertEquals

class DragSelectionTest {
    private val ids = listOf("a", "b", "c", "d", "e")

    @Test
    fun draggingFromASelectedRowSelectsEveryRowPassed() {
        val drag = DragSelection(ids, anchor = "b", initial = setOf("b", "e"))
        assertEquals(setOf("b", "c", "d", "e"), drag.through("d"))
        assertEquals(setOf("a", "b", "e"), drag.through("a"))
    }

    @Test
    fun draggingFromADeselectedRowClearsEveryRowPassed() {
        val drag = DragSelection(ids, anchor = "d", initial = setOf("a", "b", "c", "e"))
        assertEquals(setOf("a", "e"), drag.through("b"))
    }

    @Test
    fun draggingBackRestoresRowsTheFingerLeft() {
        val drag = DragSelection(ids, anchor = "a", initial = setOf("a"))
        drag.through("e")
        assertEquals(setOf("a", "b"), drag.through("b"))
    }

    @Test
    fun aRowThatLeftTheListChangesNothing() {
        val drag = DragSelection(ids, anchor = "e", initial = setOf("e"))
        assertEquals(setOf("e"), drag.through("gone"))
    }
}
