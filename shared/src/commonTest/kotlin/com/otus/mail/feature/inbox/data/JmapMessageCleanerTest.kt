package com.otus.mail.feature.inbox.data

import com.otus.jmap.JmapClient
import com.otus.jmap.JmapConnections
import com.otus.jmap.login.InMemoryLoginStore
import com.otus.mail.feature.inbox.domain.Deletion
import com.otus.mail.feature.inbox.domain.MailLocation
import com.otus.mail.feature.inbox.domain.MailMessage
import com.otus.mail.feature.inbox.domain.MailSender
import com.otus.mail.feature.inbox.domain.ReceivedAt
import com.otus.mail.feature.inbox.domain.ThreadMember
import com.otus.mail.testing.ADA_LOGIN
import com.otus.mail.testing.CONTACTS_ONLY_SESSION
import com.otus.mail.testing.STALWART_SESSION
import com.otus.mail.testing.stalwartLike
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class JmapMessageCleanerTest {
    private val login = ADA_LOGIN

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
        mailboxes: Map<String, Set<String>> = mapOf("m2" to setOf("t", "p")),
        reply: (String) -> String,
    ): JmapMessageCleaner {
        val store = InMemoryLoginStore().apply { save(login, "secret") }
        val engine =
            stalwartLike(session) { body ->
                if ("Email/get" in body && "Email/query" !in body) membershipReply(body, mailboxes) else reply(body)
            }
        return JmapMessageCleaner(JmapConnections(JmapClient(engine), store))
    }

    private fun membershipReply(
        body: String,
        mailboxes: Map<String, Set<String>>,
    ): String {
        val call =
            Json
                .parseToJsonElement(body)
                .jsonObject
                .getValue("methodCalls")
                .jsonArray
                .single()
                .jsonArray
        val ids =
            call[1]
                .jsonObject
                .getValue("ids")
                .jsonArray
                .map { it.jsonPrimitive.content }
        val list =
            ids.joinToString { id ->
                val boxes = mailboxes[id].orEmpty().ifEmpty { setOf("t") }.joinToString { "\"$it\":true" }
                """{"id":"$id","mailboxIds":{$boxes}}"""
            }
        return """{"methodResponses":[["Email/get",{"state":"e1","list":[$list]},"c0"]]}"""
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
        val (rejectedUpdates, acceptedUpdates) =
            UPDATE.findAll(body).map { it.groupValues[1] }.partition {
                it in
                    refused
            }
        val updated = acceptedUpdates.joinToString { "\"$it\": null" }
        val notUpdated = rejectedUpdates.joinToString { "\"$it\": {\"type\": \"forbidden\"}" }
        val (kept, gone) = destroy.partition { it in refused }
        val destroyed = gone.joinToString { "\"$it\"" }
        val notDestroyed = kept.joinToString { "\"$it\": {\"type\": \"forbidden\"}" }
        return """{"methodResponses": [["Email/set", {"newState": "e2", "updated": {$updated},
            "destroyed": [$destroyed], "notDestroyed": {$notDestroyed}, "notUpdated": {$notUpdated}}, "c0"]]}"""
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
    fun deletingAConversationRemovesEachEmailFromTheFolderItIsListedIn() =
        runTest {
            val sent = mutableListOf<String>()
            val members =
                listOf(
                    ThreadMember("m1", setOf("t"), "t"),
                    ThreadMember("m2", setOf("t", "p"), "t"),
                    ThreadMember("m3", setOf("l", "a"), "l"),
                )
            val deleted =
                cleaner(mailboxes = mapOf("m2" to setOf("t", "p"), "m3" to setOf("l", "a"))) {
                    sent += it
                    setReply(it)
                }.delete(listOf(message("m1").copy(conversation = conversationOf(members))))
            assertEquals(setOf("ada/m1"), deleted)
            assertTrue("\"update\":{\"m2\":{\"mailboxIds/t\":null}},\"destroy\":[\"m1\"]" in sent[0], sent[0])
            assertTrue("\"update\":{\"m3\":{\"mailboxIds/l\":null}}" in sent[1], "m3 is also in another mailbox")
        }

    @Test
    fun notSpamMovesEveryEmailOfTheConversation() =
        runTest {
            val sent = mutableListOf<String>()
            val members = listOf(ThreadMember("m1", setOf("j"), "j"), ThreadMember("m2", setOf("j"), "j"))
            val spam = message("m1", folder = "j").copy(conversation = conversationOf(members))
            val cleaner =
                cleaner {
                    sent += it
                    setReply(it)
                }
            assertTrue(cleaner.notSpam(spam, "i", keywords = false))
            val moved = "{\"mailboxIds/j\":null,\"mailboxIds/i\":true}"
            assertTrue("\"m1\":$moved" in sent.single() && "\"m2\":$moved" in sent.single(), sent.single())
        }

    @Test
    fun refusedAndUnreachableDeletionsAreLeftOut() =
        runTest {
            val messages = listOf(message("m1"), message("m2"), message("m3"))
            assertEquals(
                setOf("ada/m1", "ada/m3"),
                cleaner(mailboxes = emptyMap()) {
                    setReply(it, refused = setOf("m2"))
                }.delete(messages),
            )
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
    fun aRefusedConversationMemberKeepsTheRowAndReportsNotSpamFailure() =
        runTest {
            val members = listOf("m1", "m2").map { ThreadMember(it, setOf("j"), "j") }
            val row = message("m1", folder = "j").copy(conversation = conversationOf(members))
            val inJ = members.associate { it.emailId to setOf("j") }
            val partial = cleaner(mailboxes = inJ) { setReply(it, refused = setOf("m2")) }
            assertEquals(emptySet(), partial.delete(listOf(row)))
            assertFalse(partial.notSpam(row, "i", keywords = true))
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
