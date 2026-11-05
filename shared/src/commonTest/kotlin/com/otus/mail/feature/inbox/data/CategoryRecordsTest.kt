package com.otus.mail.feature.inbox.data

import com.otus.mail.core.cache.CacheKind
import com.otus.mail.core.cache.InMemoryMailCache
import com.otus.mail.core.mail.EmailAddress
import com.otus.mail.core.mail.EmailBodyPart
import com.otus.mail.core.mail.EmailBodyValue
import com.otus.mail.core.mail.EmailObject
import com.otus.mail.core.mail.objects
import com.otus.mail.core.mail.putObject
import com.otus.mail.feature.inbox.domain.category.CLASSIFIER_VERSION
import com.otus.mail.feature.inbox.domain.category.MailCategory
import com.otus.mail.feature.inbox.domain.category.SenderDirectory
import kotlinx.coroutines.test.runTest
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.test.Test
import kotlin.test.assertEquals

/** Verdicts kept beside each email, made again for a new classifier, and none while categories are off. */
class CategoryRecordsTest {
    private val cache = InMemoryMailCache()

    private val sale =
        EmailObject(
            id = "e1",
            receivedAt = "2026-11-01T09:00:00Z",
            from = listOf(EmailAddress(email = "hello@brand.example")),
            subject = "30% off this weekend",
            listUnsubscribe = listOf("https://brand.example/u/1"),
        )

    private val receipt =
        EmailObject(
            id = "e2",
            receivedAt = "2026-11-02T09:00:00Z",
            from = listOf(EmailAddress(email = "orders@shop.example")),
            subject = "Thanks for shopping with us",
        )

    private fun records(
        senders: SenderDirectory = SenderDirectory.Empty,
        enabled: Boolean = true,
    ) = CategoryRecords({ senders }, { enabled }, EmptyCoroutineContext)

    @Test
    fun verdictsAreSavedBesideTheEmail() =
        runTest {
            val verdicts = records().verdicts(cache, LOGIN, listOf(sale, receipt))
            assertEquals(MailCategory.Newsletters, verdicts["e1"]?.category)
            val automated = verdicts["e2"]?.category
            assertEquals(MailCategory.Notifications, automated, "orders@ is automated, and the subject names no order")
            val saved = cache.objects(LOGIN, CacheKind.Category, StoredCategory.serializer(), listOf("e1", "e2"))
            assertEquals(verdicts, saved.mapValues { (_, stored) -> stored.verdict("$CLASSIFIER_VERSION.0") })
        }

    @Test
    fun aNewSenderListClassifiesAgain() =
        runTest {
            records().verdicts(cache, LOGIN, listOf(receipt))
            val listed = SenderDirectory.parse("version 2\ntransactions shop.example")
            val again = records(listed).verdicts(cache, LOGIN, listOf(receipt))
            assertEquals(MailCategory.Transactions, again["e2"]?.category)
            assertEquals("$CLASSIFIER_VERSION.2", again["e2"]?.version)
        }

    @Test
    fun aSavedVerdictThisVersionCannotReadIsMadeAgain() =
        runTest {
            val unknown = StoredCategory("$CLASSIFIER_VERSION.0", "forums")
            cache.write(LOGIN) { putObject(CacheKind.Category, "e1", StoredCategory.serializer(), unknown) }
            assertEquals(MailCategory.Newsletters, records().verdicts(cache, LOGIN, listOf(sale))["e1"]?.category)
        }

    @Test
    fun anOpenedBodysMarkupDecides() =
        runTest {
            val markup = """<script type="application/ld+json">{"@type": "Order", "orderNumber": "1"}</script>"""
            val body =
                EmailObject(
                    id = "e2",
                    receivedAt = receipt.receivedAt,
                    htmlBody = listOf(EmailBodyPart(partId = "1", type = "text/html")),
                    bodyValues = mapOf("1" to EmailBodyValue(markup)),
                )
            cache.write(LOGIN) { putObject(CacheKind.Body, "e2", EmailObject.serializer(), body) }
            val verdict = records().verdicts(cache, LOGIN, listOf(receipt))["e2"]
            assertEquals(MailCategory.Transactions, verdict?.category)
        }

    @Test
    fun nothingIsClassifiedWhileCategoriesAreOff() =
        runTest {
            assertEquals(emptyMap(), records(enabled = false).verdicts(cache, LOGIN, listOf(sale)))
            assertEquals(emptyMap(), cache.read(LOGIN, CacheKind.Category, listOf("e1")))
        }

    private companion object {
        const val LOGIN = "ada"
    }
}
