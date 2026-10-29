package com.otus.mail.feature.inbox.data

import com.otus.jmap.JmapClient
import com.otus.jmap.JmapConnections
import com.otus.jmap.login.InMemoryLoginStore
import com.otus.mail.testing.ADA_LOGIN
import com.otus.mail.testing.STALWART_SESSION
import com.otus.mail.testing.stalwartLike
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class JmapKnownPeopleTest {
    private val login = ADA_LOGIN
    private val requests = mutableListOf<String>()

    /** The mail account also holds address books. */
    private val contactsSession =
        STALWART_SESSION
            .replace(
                "\"urn:ietf:params:jmap:mail\": {}},",
                "\"urn:ietf:params:jmap:mail\": {}, \"urn:ietf:params:jmap:contacts\": {}},",
            ).replace(
                "\"urn:ietf:params:jmap:mail\": {}}}}",
                "\"urn:ietf:params:jmap:mail\": {}, \"urn:ietf:params:jmap:contacts\": {}}}}",
            ).replace(
                "\"primaryAccounts\": {\"urn:ietf:params:jmap:mail\": \"d333333\"}",
                "\"primaryAccounts\": {\"urn:ietf:params:jmap:mail\": \"d333333\", " +
                    "\"urn:ietf:params:jmap:contacts\": \"d333333\"}",
            )

    private fun people(session: String = contactsSession): JmapKnownPeople {
        val store = InMemoryLoginStore().apply { save(login, "secret") }
        val engine =
            stalwartLike(session) { body ->
                requests += body
                when {
                    "ContactCard/query" in body -> CARDS
                    "Mailbox/query" in body -> SENT_FOLDER
                    "Email/query" in body -> SENT_QUERY
                    else -> sentMail(body)
                }
            }
        return JmapKnownPeople(JmapConnections(JmapClient(engine), store), store)
    }

    @Test
    fun contactsAndEveryoneTheUserWroteToAreKnownInLowercase() =
        runTest {
            val known = people().addresses("ada")
            val expected = setOf("grace@example.org", "radia@example.org", "bo@example.org", "kay@example.org")
            assertEquals(expected + "ken@example.org", known)
            assertTrue(requests.any { "\"inMailbox\":\"s\"" in it && "\"limit\":500" in it }, requests.toString())
        }

    @Test
    fun sentMailIsReadInBatchesTheServerAllows() =
        runTest {
            val small = STALWART_SESSION.replace("$CORE: {}", "$CORE: {\"maxObjectsInGet\": 1}")
            val known = setOf("radia@example.org", "bo@example.org", "kay@example.org", "ken@example.org")
            assertEquals(known, people(small).addresses("ada"))
            assertEquals(2, requests.count { "Email/get" in it }, requests.toString())
        }

    @Test
    fun theSetIsKeptForLaterOpens() =
        runTest {
            val people = people(STALWART_SESSION)
            people.addresses("ada")
            val asked = requests.size
            val known = setOf("radia@example.org", "bo@example.org", "kay@example.org", "ken@example.org")
            assertEquals(known, people.addresses("ada"))
            assertEquals(asked, requests.size, "The second open asks nothing")
        }

    /** The sent emails an `Email/get` [body] names, in one response. */
    private fun sentMail(body: String): String {
        val list = listOfNotNull(M1.takeIf { "\"m1\"" in body }, M2.takeIf { "\"m2\"" in body })
        return """{"methodResponses": [["Email/get", {"state": "e", "list": [${list.joinToString()}]}, "c0"]]}"""
    }

    private companion object {
        const val CORE = "\"urn:ietf:params:jmap:core\""
        const val CARDS = """{"methodResponses": [
            ["ContactCard/query", {"queryState": "q", "ids": ["c1"]}, "c0"],
            ["ContactCard/get", {"state": "c", "list": [
              {"id": "c1", "name": {"full": "Grace"}, "emails": {"e": {"address": "Grace@Example.org"}}}]}, "c1"]]}"""
        const val SENT_FOLDER = """{"methodResponses": [["Mailbox/query", {"queryState": "q", "ids": ["s"]}, "c0"]]}"""
        const val SENT_QUERY =
            """{"methodResponses": [["Email/query", {"queryState": "q", "ids": ["m1", "m2"]}, "c0"]]}"""
        const val M1 = """{"id": "m1", "receivedAt": "2026-10-04T09:00:00Z",
              "to": [{"email": "Radia@example.org"}], "cc": [{"email": "bo@example.org"}],
              "bcc": [{"email": "kay@example.org"}]}"""
        const val M2 = """{"id": "m2", "receivedAt": "2026-10-03T09:00:00Z", "to": [{"email": "ken@example.org"}]}"""
    }
}
