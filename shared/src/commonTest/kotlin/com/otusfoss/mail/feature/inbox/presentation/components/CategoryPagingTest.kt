package com.otusfoss.mail.feature.inbox.presentation.components

import kotlin.test.Test
import kotlin.test.assertEquals

/** A category pill stops paging once pages stop adding to it, and starts again when one does. */
class CategoryPagingTest {
    @Test
    fun loadingStopsAfterThreePagesThatAddNothing() {
        val paging = CategoryPaging()
        var loads = 0
        repeat(5) {
            paging.load(2) { loads++ }
            paging.settle(2)
        }
        assertEquals(3, loads)
    }

    @Test
    fun aPageThatAddsRowsStartsTheCountAgain() {
        val paging = CategoryPaging()
        var loads = 0
        listOf(2 to 2, 2 to 2, 2 to 5, 5 to 5, 5 to 5, 5 to 5, 5 to 5).forEach { (before, after) ->
            paging.load(before) { loads++ }
            paging.settle(after)
        }
        assertEquals(6, loads)
    }
}
