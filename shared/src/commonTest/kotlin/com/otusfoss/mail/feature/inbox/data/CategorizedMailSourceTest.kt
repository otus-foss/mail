package com.otusfoss.mail.feature.inbox.data

import com.otusfoss.mail.core.cache.CacheKind
import com.otusfoss.mail.core.cache.InMemoryMailCache
import com.otusfoss.mail.core.categories.domain.MailCategory
import com.otusfoss.mail.core.mail.EmailAddress
import com.otusfoss.mail.core.mail.EmailObject
import com.otusfoss.mail.core.mail.putObject
import com.otusfoss.mail.core.messages.domain.MailLocation
import com.otusfoss.mail.core.messages.domain.MailMessage
import com.otusfoss.mail.core.messages.domain.MailScope
import com.otusfoss.mail.core.messages.domain.MailSender
import com.otusfoss.mail.core.messages.domain.ReceivedAt
import com.otusfoss.mail.feature.inbox.domain.InboxSnapshot
import com.otusfoss.mail.feature.inbox.domain.MailSource
import com.otusfoss.mail.feature.inbox.domain.category.SenderDirectory
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
        sender: MailSender = MailSender("", "news@weekly.example"),
    ) = MailMessage(
        id = "ada/$id",
        sender = sender,
        subject = "This week",
        body = "",
        received = ReceivedAt("2026-11-01", "09:00"),
        location = MailLocation("Inbox", "Ada", "ada"),
        emailId = emailId,
    )

    private fun source(
        rows: List<MailMessage>,
        enabled: Boolean = true,
        senders: SenderDirectory = SenderDirectory.Empty,
    ): MailSource {
        val listed =
            object : MailSource {
                override suspend fun messages(scope: MailScope) = InboxSnapshot(rows)
            }
        val records = CategoryRecords({ senders }, { enabled }, EmptyCoroutineContext)
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
    fun aRowsDmarcPassLetsTheSenderListDecide() =
        runTest {
            val email =
                EmailObject(
                    id = "e1",
                    receivedAt = "2026-11-01T09:00:00Z",
                    from = listOf(EmailAddress(email = "orders@shop.example")),
                    subject = "Thanks for shopping with us",
                )
            cache.write("ada") { putObject(CacheKind.Email, "e1", EmailObject.serializer(), email) }
            val listed = SenderDirectory.parse("version 1\ntransactions shop.example")
            val passed = MailSender("", "orders@shop.example", brandDomain = "shop.example")
            val rows = source(listOf(row("e1", "e1", passed)), senders = listed).messages(MailScope.Inbox).messages
            assertEquals(MailCategory.Transactions, rows.single().category?.category)
        }

    @Test
    fun rowsStayUnsortedWhileCategoriesAreOff() =
        runTest {
            val rows = source(listOf(row("e1", "e1")), enabled = false).messages(MailScope.Inbox).messages
            assertNull(rows.single().category)
        }
}
