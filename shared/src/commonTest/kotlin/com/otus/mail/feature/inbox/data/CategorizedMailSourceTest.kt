package com.otus.mail.feature.inbox.data

import com.otus.mail.core.cache.CacheKind
import com.otus.mail.core.cache.InMemoryMailCache
import com.otus.mail.core.mail.EmailAddress
import com.otus.mail.core.mail.EmailObject
import com.otus.mail.core.mail.putObject
import com.otus.mail.feature.inbox.domain.InboxSnapshot
import com.otus.mail.feature.inbox.domain.MailLocation
import com.otus.mail.feature.inbox.domain.MailMessage
import com.otus.mail.feature.inbox.domain.MailScope
import com.otus.mail.feature.inbox.domain.MailSender
import com.otus.mail.feature.inbox.domain.MailSource
import com.otus.mail.feature.inbox.domain.ReceivedAt
import com.otus.mail.feature.inbox.domain.category.MailCategory
import com.otus.mail.feature.inbox.domain.category.SenderDirectory
import kotlinx.coroutines.test.runTest
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Listed rows get the category of their cached email; supplied messages and other calls pass through. */
class CategorizedMailSourceTest {
    private val cache = InMemoryMailCache()

    private fun row(
        id: String,
        emailId: String,
    ) = MailMessage(
        id = "ada/$id",
        sender = MailSender("", "news@weekly.example"),
        subject = "This week",
        body = "",
        received = ReceivedAt("2026-11-01", "09:00"),
        location = MailLocation("Inbox", "Ada", "ada"),
        emailId = emailId,
    )

    private fun source(
        rows: List<MailMessage>,
        enabled: Boolean = true,
    ): MailSource {
        val listed =
            object : MailSource {
                override suspend fun messages(scope: MailScope) = InboxSnapshot(rows)
            }
        val records = CategoryRecords({ SenderDirectory.Empty }, { enabled }, EmptyCoroutineContext)
        return CategorizedMailSource(listed, cache, records)
    }

    @Test
    fun listedRowsCarryTheirEmailsCategory() =
        runTest {
            val email =
                EmailObject(
                    id = "e1",
                    receivedAt = "2026-11-01T09:00:00Z",
                    from = listOf(EmailAddress(email = "news@weekly.example")),
                    subject = "This week",
                    listId = "<weekly.example>",
                )
            cache.write("ada") { putObject(CacheKind.Email, "e1", EmailObject.serializer(), email) }
            val rows = source(listOf(row("e1", "e1"), row("supplied", ""))).messages(MailScope.Inbox).messages
            assertEquals(MailCategory.Newsletters, rows.first().category?.category)
            assertNull(rows.last().category, "A message without a server id is not sorted")
        }

    @Test
    fun rowsStayUnsortedWhileCategoriesAreOff() =
        runTest {
            val rows = source(listOf(row("e1", "e1")), enabled = false).messages(MailScope.Inbox).messages
            assertNull(rows.single().category)
        }
}
