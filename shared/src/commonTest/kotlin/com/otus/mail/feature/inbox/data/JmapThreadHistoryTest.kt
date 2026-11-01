package com.otus.mail.feature.inbox.data

import com.otus.jmap.JmapClient
import com.otus.jmap.JmapConnections
import com.otus.jmap.login.InMemoryLoginStore
import com.otus.mail.feature.inbox.domain.MailSender
import com.otus.mail.testing.ADA_LOGIN
import com.otus.mail.testing.STALWART_SESSION
import com.otus.mail.testing.stalwartLike
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class JmapThreadHistoryTest {
    private val login = ADA_LOGIN
    private val calls = mutableListOf<String>()

    private fun history(emails: String): JmapThreadHistory {
        val store = InMemoryLoginStore().apply { save(login, "secret") }
        val engine =
            stalwartLike(STALWART_SESSION) { body ->
                calls += body
                """{"methodResponses": [
                    ["Email/get", {"state": "e", "list": [{"id": "m3", "threadId": "t1"}]}, "c0"],
                    ["Thread/get", {"state": "t", "list": [{"id": "t1", "emailIds": ["m1", "m2", "m3", "m4"]}]}, "c1"],
                    ["Email/get", {"state": "e", "list": [$emails]}, "c2"]]}"""
            }
        return JmapThreadHistory(JmapConnections(JmapClient(engine), store), store) { TimeZone.UTC }
    }

    @Test
    fun theThreadUpToTheOriginalComesNewestFirstWithoutDrafts() =
        runTest {
            val emails =
                listOf(
                    email("m1", "Radia Perlman", "2026-10-08T09:00:00Z", "Kicking off Q4 planning"),
                    email("m2", "Ada Lovelace", "2026-10-10T09:00:00Z", "Works for me", draft = true),
                    email("m3", "Grace Hopper", "2026-10-11T09:00:00Z", "Can we move the review?"),
                    email("m4", "Margaret Knuth", "2026-10-12T09:00:00Z", "Later reply"),
                ).joinToString()
            val thread = history(emails).earlier("ada", "m3")
            assertEquals(listOf("m3", "m1"), thread.map { it.emailId })
            assertEquals(MailSender("Grace Hopper", "grace@example.org"), thread.first().sender)
            assertEquals(LocalDate(2026, 10, 11), thread.first().date)
            assertEquals("Can we move the review?", thread.first().preview)
            assertTrue("\"Thread/get\"" in calls.single(), "The thread loads in one request")
        }

    @Test
    fun timesWithAndWithoutFractionsCompareAsInstants() =
        runTest {
            val emails =
                listOf(
                    email("m1", "Radia Perlman", "2026-10-11T08:59:59.900Z", "Just before"),
                    email("m3", "Grace Hopper", "2026-10-11T09:00:00Z", "The original"),
                    email("m4", "Margaret Knuth", "2026-10-11T09:00:00.500Z", "Half a second later"),
                ).joinToString()
            assertEquals(listOf("m3", "m1"), history(emails).earlier("ada", "m3").map { it.emailId })
        }

    private fun email(
        id: String,
        name: String,
        receivedAt: String,
        preview: String,
        draft: Boolean = false,
    ): String {
        val address = name.substringBefore(' ').lowercase() + "@example.org"
        val keywords = if (draft) """{"${'$'}draft": true}""" else "{}"
        return """{"id": "$id", "from": [{"name": "$name", "email": "$address"}], "receivedAt": "$receivedAt",
            "preview": "$preview", "keywords": $keywords}"""
    }
}
