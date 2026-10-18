package com.otus.mail.feature.inbox.data

import com.otus.jmap.JmapClient
import com.otus.jmap.JmapConnections
import com.otus.jmap.login.InMemoryLoginStore
import com.otus.jmap.login.JmapLogin
import com.otus.mail.testing.STALWART_SESSION
import com.otus.mail.testing.stalwartLike
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class JmapKnownPeopleTest {
    private val login = JmapLogin("ada", "https://mail.example.org/.well-known/jmap", "ada@example.org", "Ada")
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
                    else -> SENT_MAIL
                }
            }
        return JmapKnownPeople(JmapConnections(JmapClient(engine), store), store)
    }

    @Test
    fun contactsAndEveryoneTheUserWroteToAreKnownInLowercase() =
        runTest {
            val known = people().addresses("ada")
            assertEquals(setOf("grace@example.org", "radia@example.org", "bo@example.org", "kay@example.org"), known)
            assertTrue(requests.any { "\"inMailbox\":\"s\"" in it && "\"limit\":500" in it }, requests.toString())
        }

    @Test
    fun theSetIsKeptForLaterOpens() =
        runTest {
            val people = people(STALWART_SESSION)
            people.addresses("ada")
            val asked = requests.size
            assertEquals(setOf("radia@example.org", "bo@example.org", "kay@example.org"), people.addresses("ada"))
            assertEquals(asked, requests.size, "The second open asks nothing")
        }

    private companion object {
        const val CARDS = """{"methodResponses": [
            ["ContactCard/query", {"queryState": "q", "ids": ["c1"]}, "c0"],
            ["ContactCard/get", {"state": "c", "list": [
              {"id": "c1", "name": {"full": "Grace"}, "emails": {"e": {"address": "Grace@Example.org"}}}]}, "c1"]]}"""
        const val SENT_FOLDER = """{"methodResponses": [["Mailbox/query", {"queryState": "q", "ids": ["s"]}, "c0"]]}"""
        const val SENT_MAIL = """{"methodResponses": [
            ["Email/query", {"queryState": "q", "ids": ["m1"]}, "c0"],
            ["Email/get", {"state": "e", "list": [{"id": "m1", "receivedAt": "2026-10-04T09:00:00Z",
              "to": [{"email": "Radia@example.org"}], "cc": [{"email": "bo@example.org"}],
              "bcc": [{"email": "kay@example.org"}]}]}, "c1"]]}"""
    }
}
