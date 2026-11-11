package com.otusfoss.mail.feature.inbox.presentation

import androidx.compose.runtime.saveable.SaverScope
import com.otusfoss.mail.core.messages.domain.DateFilter
import com.otusfoss.mail.core.messages.domain.FilterSort
import com.otusfoss.mail.core.messages.domain.FolderRef
import com.otusfoss.mail.core.messages.domain.OwnAddress
import com.otusfoss.mail.core.messages.domain.QuickFacet
import com.otusfoss.mail.core.messages.domain.ReadStatus
import com.otusfoss.mail.core.messages.domain.RecipientField
import com.otusfoss.mail.core.messages.domain.SenderKey
import com.otusfoss.mail.core.messages.domain.SizeFilter
import com.otusfoss.mail.core.messages.domain.TypeFacet
import com.otusfoss.mail.feature.inbox.domain.InboxFilters
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
                status = ReadStatus.Read,
                quick = setOf(QuickFacet.AwaitingReply, QuickFacet.Flagged),
                types = setOf(TypeFacet.Newsletters),
                from =
                    setOf(
                        SenderKey("OpenAI", setOf("noreply@openai.example", "news@openai.example")),
                        SenderKey.of("sam@x.example"),
                    ),
                sentTo = setOf(OwnAddress("ada", "ada@example.org")),
                to = setOf("kim@x.example"),
                receivedIn = setOf(RecipientField.Bcc),
                account = "ada",
                folders = setOf(FolderRef("ada", "f", includeSubfolders = false), FolderRef("bo", "g")),
                date = DateFilter.Range(LocalDate(2026, 10, 12), LocalDate(2026, 10, 18)),
                size = SizeFilter.Over10Mb,
                sort = FilterSort.OldestFirst,
            )
        listOf(
            filters,
            InboxFilters(),
            InboxFilters(date = DateFilter.ThisYear),
            InboxFilters(receivedIn = emptySet()),
        ).forEach { value ->
            val saved = assertNotNull(with(InboxFiltersSaver) { saveScope.save(value) })
            assertEquals(value, InboxFiltersSaver.restore(saved))
        }
    }

    @Test
    fun filtersAnEarlierVersionSavedReadAsTheirNewChoices() {
        val saved =
            listOf(
                "NewestFirst",
                "last30",
                "Any",
                line("show", "Unread"),
                line("show", "WaitingForReply"),
                line("show", "Conversations"),
                line("show", "NotFlagged"),
                line("show", "Replied"),
                line("show", "NotReplied"),
                line("show", "Newsletters"),
                line("show", "CalendarInvites"),
                line("from", "sam@x.example"),
                line("account", "ada"),
                line("to", "ada", "ada@example.org"),
                line("as", "Cc"),
                line("folder", "ada", "f", "true"),
                line("skip", "ada", "trash", "true"),
            )
        assertEquals(
            InboxFilters(
                status = ReadStatus.Unread,
                quick = setOf(QuickFacet.AwaitingReply, QuickFacet.Conversations),
                types = setOf(TypeFacet.Newsletters, TypeFacet.CalendarInvites),
                from = setOf(SenderKey.of("sam@x.example")),
                sentTo = setOf(OwnAddress("ada", "ada@example.org")),
                receivedIn = setOf(RecipientField.Cc),
                account = "ada",
                folders = setOf(FolderRef("ada", "f")),
                date = DateFilter.Last30,
            ),
            InboxFiltersSaver.restore(saved),
        )
        assertEquals(
            InboxFilters(status = ReadStatus.Read),
            InboxFiltersSaver.restore(
                listOf("NewestFirst", "any", "Any", line("show", "Read"), line("show", "NotFlagged")),
            ),
            "Without Received as lines the fields stay the default",
        )
    }

    private fun line(vararg values: String) = values.joinToString("\u0000")
}
