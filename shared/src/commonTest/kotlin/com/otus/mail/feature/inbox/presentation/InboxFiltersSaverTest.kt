package com.otus.mail.feature.inbox.presentation

import androidx.compose.runtime.saveable.SaverScope
import com.otus.mail.feature.inbox.domain.DateFilter
import com.otus.mail.feature.inbox.domain.FilterSort
import com.otus.mail.feature.inbox.domain.FolderRef
import com.otus.mail.feature.inbox.domain.InboxFilters
import com.otus.mail.feature.inbox.domain.OwnAddress
import com.otus.mail.feature.inbox.domain.ShowFacet
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class InboxFiltersSaverTest {
    private val saveScope =
        object : SaverScope {
            override fun canBeSaved(value: Any) = true
        }

    @Test
    fun everyChoiceSurvivesSaving() {
        val filters =
            InboxFilters(
                show = setOf(ShowFacet.Unread, ShowFacet.WaitingForReply),
                sentTo = setOf(OwnAddress("ada", "ada@example.org")),
                folders = setOf(FolderRef("ada", "f", includeSubfolders = false), FolderRef("bo", "g")),
                date = DateFilter.Range(LocalDate(2026, 10, 12), LocalDate(2026, 10, 18)),
                sort = FilterSort.OldestFirst,
            )
        listOf(filters, InboxFilters(), InboxFilters(date = DateFilter.Last30)).forEach { value ->
            val saved = assertNotNull(with(InboxFiltersSaver) { saveScope.save(value) })
            assertEquals(value, InboxFiltersSaver.restore(saved))
        }
    }
}
