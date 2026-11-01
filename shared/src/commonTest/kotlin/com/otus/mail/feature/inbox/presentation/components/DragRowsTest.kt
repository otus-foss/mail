package com.otus.mail.feature.inbox.presentation.components

import androidx.compose.foundation.lazy.LazyListItemInfo
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/** The message row under the finger during a drag, among items that are not messages. */
class DragRowsTest {
    private val ids = listOf("a", "b", "c", "d", "e")

    @Test
    fun aNoticeAboveTheRowsShiftsNoRow() {
        val items = listOf(Item("notice", 0, 40), Item("a", 40, 100), Item("b", 140, 100), Item("c", 240, 100))
        assertNull(items.messageAt(20f, ids, clamp = false), "The notice is no row")
        assertEquals("a", items.messageAt(60f, ids, clamp = false))
        assertEquals("c", items.messageAt(300f, ids, clamp = false))
        assertEquals("a", items.messageAt(20f, ids, clamp = true))
    }

    @Test
    fun aFingerOnTheLoadingFooterReachesTheLastRow() {
        val items = listOf(Item("d", 0, 100), Item("e", 100, 100), Item("loading", 200, 60))
        assertNull(items.messageAt(220f, ids, clamp = false))
        assertEquals("e", items.messageAt(220f, ids, clamp = true))
        val last = assertNotNull(items.messageAt(500f, ids, clamp = true))
        assertEquals(setOf("c", "d", "e"), DragSelection(ids, anchor = "c", initial = setOf("c")).through(last))
    }

    /** A visible list item: [key], and where it starts and how tall it is, in pixels. */
    private class Item(
        override val key: Any,
        override val offset: Int,
        override val size: Int,
    ) : LazyListItemInfo {
        override val index: Int = 0
    }
}
