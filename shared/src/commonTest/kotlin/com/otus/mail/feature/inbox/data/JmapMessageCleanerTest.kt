package com.otus.mail.feature.inbox.data

import com.otus.jmap.JmapClient
import com.otus.jmap.JmapConnections
import com.otus.jmap.login.InMemoryLoginStore
import com.otus.jmap.login.JmapLogin
import com.otus.mail.feature.inbox.domain.Deletion
import com.otus.mail.feature.inbox.domain.MailLocation
import com.otus.mail.feature.inbox.domain.MailMessage
import com.otus.mail.feature.inbox.domain.MailSender
import com.otus.mail.feature.inbox.domain.ReceivedAt
import com.otus.mail.testing.CONTACTS_ONLY_SESSION
import com.otus.mail.testing.STALWART_SESSION
import com.otus.mail.testing.stalwartLike
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class JmapMessageCleanerTest {
    private val login = JmapLogin("ada", "https://mail.example.org/.well-known/jmap", "ada@example.org", "Ada")

    /** A session that takes two objects per `/get` and `/set` call. */
    private val smallBatches =
        STALWART_SESSION.replace(
            "\"urn:ietf:params:jmap:core\": {}",
            "\"urn:ietf:params:jmap:core\": {\"maxObjectsInSet\": 2, \"maxObjectsInGet\": 2}",
        )

    private fun message(
        id: String,
        folder: String = "t",
        alsoIn: Set<String> = emptySet(),
    ) = MailMessage(
        "ada/$id",
        MailSender("Grace", "grace@example.org"),
        "Order",
        "",
        ReceivedAt("2026-10-04", "09:30"),
        MailLocation("Trash", "Ada", "ada", mailboxId = folder, mailboxIds = setOf(folder) + alsoIn),
        emailId = id,
    )

    private fun cleaner(
        session: String = smallBatches,
        reply: (String) -> String,
    ): JmapMessageCleaner {
        val store = InMemoryLoginStore().apply { save(login, "secret") }
        return JmapMessageCleaner(JmapConnections(JmapClient(stalwartLike(session, reply)), store), store)
    }

    /** Destroys or updates every id a `/set` call names, except [refused] destroys. */
    private fun setReply(
        body: String,
        refused: Set<String> = emptySet(),
    ): String {
        val destroy =
            DESTROY
                .find(
                    body,
                )?.groupValues
                ?.get(1)
                .orEmpty()
                .let(IDS::findAll)
                .map { it.groupValues[1] }
                .toList()
        val updated = UPDATE.findAll(body).map { "\"${it.groupValues[1]}\": null" }.joinToString()
        val (kept, gone) = destroy.partition { it in refused }
        val destroyed = gone.joinToString { "\"$it\"" }
        val notDestroyed = kept.joinToString { "\"$it\": {\"type\": \"forbidden\"}" }
        return """{"methodResponses": [["Email/set", {"newState": "e2", "updated": {$updated},
            "destroyed": [$destroyed], "notDestroyed": {$notDestroyed}}, "c0"]]}"""
    }

    @Test
    fun notSpamMovesToTheInboxAndSwapsTheSpamKeywords() =
        runTest {
            val sent = mutableListOf<String>()
            val cleaner =
                cleaner {
                    sent += it
                    """{"methodResponses": [["Email/set", {"newState": "e2", "updated": {"m1": null}}, "c0"]]}"""
                }
            val spam = message("m1", folder = "j")
            assertTrue(cleaner.notSpam(spam, "i", keywords = true))
            assertTrue(cleaner.notSpam(spam, "i", keywords = false))
            assertFalse(cleaner.notSpam(spam, "j", keywords = true), "It is already there")
            val marked =
                "{\"mailboxIds/j\":null,\"mailboxIds/i\":true,\"keywords/\$junk\":null,\"keywords/\$notjunk\":true}"
            assertTrue(marked in sent[0], sent[0])
            assertTrue("{\"mailboxIds/j\":null,\"mailboxIds/i\":true}}" in sent[1], sent[1])
            assertEquals(2, sent.size)
        }

    @Test
    fun deletingDestroysInBatchesAndOnlyTakesSharedMessagesOutOfTheFolder() =
        runTest {
            val sent = mutableListOf<String>()
            val deleted =
                cleaner {
                    sent += it
                    setReply(it)
                }.delete(listOf(message("m1"), message("m2", alsoIn = setOf("p")), message("m3")))
            assertEquals(setOf("ada/m1", "ada/m2", "ada/m3"), deleted)
            assertEquals(2, sent.size, "Two objects per call")
            assertTrue("\"update\":{\"m2\":{\"mailboxIds/t\":null}},\"destroy\":[\"m1\"]" in sent[0], sent[0])
            assertTrue("\"destroy\":[\"m3\"]" in sent[1], sent[1])
        }

    @Test
    fun refusedAndUnreachableDeletionsAreLeftOut() =
        runTest {
            val messages = listOf(message("m1"), message("m2"), message("m3"))
            assertEquals(setOf("ada/m1", "ada/m3"), cleaner { setReply(it, refused = setOf("m2")) }.delete(messages))
            var calls = 0
            val failing =
                cleaner {
                    if (++calls > 1) "Service unavailable" else setReply(it)
                }
            assertEquals(setOf("ada/m1", "ada/m2"), failing.delete(messages), "The first batch still counts")
            assertEquals(
                emptySet(),
                cleaner { error("Nothing is sent") }.delete(listOf(message("").copy(emailId = ""))),
            )
        }

    @Test
    fun emptyingFindsEveryPageFirstAndThenDeletesInBatches() =
        runTest {
            val sent = mutableListOf<String>()
            val folder = listOf("m1", "m2", "m3")
            val deletion =
                cleaner { body ->
                    sent += body
                    if ("Email/query" in
                        body
                    ) {
                        page(folder, POSITION.find(body)!!.groupValues[1].toInt())
                    } else {
                        setReply(body)
                    }
                }.empty("ada", "t")
            assertEquals(Deletion(folder.toSet(), complete = true), deletion)
            val queries = sent.filter { "Email/query" in it }
            assertEquals(3, queries.size, "Two pages and an empty one")
            assertTrue(queries.all { "\"inMailbox\":\"t\"" in it && "\"limit\":2" in it }, queries.first())
            val sets = sent.filter { "Email/set" in it }
            assertEquals(2, sets.size)
            assertTrue("\"update\":{\"m2\":{\"mailboxIds/t\":null}},\"destroy\":[\"m1\"]" in sets[0], sets[0])
            assertTrue(sent.indexOf(sets[0]) > sent.indexOf(queries.last()), "Nothing changes while paging")
        }

    @Test
    fun emptyingReportsMessagesTheServerKept() =
        runTest {
            val deletion =
                cleaner { body ->
                    if ("Email/query" in
                        body
                    ) {
                        page(listOf("m1"), POSITION.find(body)!!.groupValues[1].toInt())
                    } else {
                        setReply(body, setOf("m1"))
                    }
                }.empty("ada", "t")
            assertEquals(Deletion(emptySet(), complete = false), deletion)
            assertEquals(Deletion(emptySet(), complete = false), cleaner { error("unused") }.empty("gone", "t"))
        }

    @Test
    fun aLoginWithoutMailCleansNothing() =
        runTest {
            val contactsOnly = cleaner(CONTACTS_ONLY_SESSION) { error("Nothing is sent") }
            assertFalse(contactsOnly.notSpam(message("m1", folder = "j"), inboxId = "a", keywords = true))
            assertEquals(emptySet(), contactsOnly.delete(listOf(message("m1"))))
            assertEquals(Deletion(emptySet(), complete = false), contactsOnly.empty("ada", "t"))
        }

    /** The page of [ids] at [position], two at a time; "m2" is also in another folder. */
    private fun page(
        ids: List<String>,
        position: Int,
    ): String {
        val page = ids.drop(position).take(2)
        val list =
            page.joinToString {
                val elsewhere = if (it == "m2") ", \"p\": true" else ""
                """{"id": "$it", "mailboxIds": {"t": true$elsewhere}}"""
            }
        val quoted = page.joinToString { "\"$it\"" }
        return """{"methodResponses": [["Email/query", {"ids": [$quoted], "position": $position}, "c0"],
            ["Email/get", {"state": "e", "list": [$list]}, "c1"]]}"""
    }

    private companion object {
        val DESTROY = Regex("\"destroy\":\\[([^\\]]*)]")
        val IDS = Regex("\"([^\"]+)\"")
        val UPDATE = Regex("\"(m\\d)\":\\{\"mailboxIds/")
        val POSITION = Regex("\"position\":(\\d+)")
    }
}
