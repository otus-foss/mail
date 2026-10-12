package com.otus.mail.feature.inbox.data

import com.otus.jmap.JmapClient
import com.otus.jmap.JmapConnections
import com.otus.jmap.login.InMemoryLoginStore
import com.otus.jmap.login.JmapLogin
import com.otus.mail.feature.inbox.domain.Draft
import com.otus.mail.feature.inbox.domain.DraftException
import com.otus.mail.feature.inbox.domain.DraftFailure
import com.otus.mail.feature.inbox.domain.MailSender
import com.otus.mail.testing.CONTACTS_ONLY_SESSION
import com.otus.mail.testing.STALWART_SESSION
import com.otus.mail.testing.stalwartLike
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class JmapDraftRepositoryTest {
    private val login = JmapLogin("ada", "https://mail.example.org/.well-known/jmap", "ada@example.org", "Ada")
    private val calls = mutableListOf<String>()

    private fun repository(
        session: String = STALWART_SESSION,
        api: (String) -> String,
    ): JmapDraftRepository {
        val store = InMemoryLoginStore().apply { save(login, "secret") }
        val engine =
            stalwartLike(session) { body ->
                calls += body
                api(body)
            }
        return JmapDraftRepository(JmapConnections(JmapClient(engine), store), store)
    }

    private fun server(emailSet: String) =
        repository { body ->
            when {
                "Mailbox/query" in body -> """{"methodResponses": [["Mailbox/query", {"ids": ["dr"]}, "c0"]]}"""
                "Email/set" in body -> """{"methodResponses": [["Email/set", {"newState": "e2", $emailSet}, "c0"]]}"""
                else -> error("Unexpected call: $body")
            }
        }

    private val reply =
        Draft(
            accountId = "ada",
            from = MailSender("Ada Lovelace", "ada@example.org"),
            to = listOf(MailSender("grace@example.org", "grace@example.org")),
            subject = "Re: Compiler draft",
            body = "Looks good.",
            inReplyTo = listOf("m1@example.org"),
            references = listOf("m0@example.org", "m1@example.org"),
            emailId = "old",
        )

    @Test
    fun aSaveCreatesTheDraftInTheDraftsFolderAndDestroysItsOldCopy() =
        runTest {
            val saved =
                server(""""created": {"draft": {"id": "e9"}}, "destroyed": ["old"]""").save(reply)
            assertEquals("e9", saved.getOrThrow())
            assertTrue("\"filter\":{\"role\":\"drafts\"}" in calls.first(), "The Drafts folder is found by role")
            val set = method(calls.last())
            assertEquals(listOf("old"), set.getValue("destroy").jsonArray.map { it.toString().trim('"') })
            val expected =
                """{"mailboxIds": {"dr": true}, "keywords": {"${'$'}draft": true, "${'$'}seen": true},
                "from": [{"name": "Ada Lovelace", "email": "ada@example.org"}],
                "to": [{"email": "grace@example.org"}], "subject": "Re: Compiler draft",
                "inReplyTo": ["m1@example.org"], "references": ["m0@example.org", "m1@example.org"],
                "bodyValues": {"body": {"value": "Looks good."}},
                "textBody": [{"partId": "body", "type": "text/plain"}]}"""
            assertEquals(Json.parseToJsonElement(expected), set.getValue("create").jsonObject.getValue("draft"))
        }

    @Test
    fun aFirstSaveDestroysNothingAndLeavesEmptyHeadersOut() =
        runTest {
            val saved = server(""""created": {"draft": {"id": "e1"}}""").save(Draft(accountId = "ada"))
            assertEquals("e1", saved.getOrThrow())
            val set = method(calls.last())
            assertTrue("destroy" !in set)
            val email =
                set
                    .getValue("create")
                    .jsonObject
                    .getValue("draft")
                    .jsonObject
            assertEquals(setOf("mailboxIds", "keywords", "subject", "bodyValues", "textBody"), email.keys)
        }

    @Test
    fun aSaveWithoutADraftsFolderFailsBeforeCreatingAnything() =
        runTest {
            val saved =
                repository { """{"methodResponses": [["Mailbox/query", {"ids": []}, "c0"]]}""" }.save(reply)
            assertEquals(DraftFailure.NoDraftsFolder, saved.failure())
            assertTrue(calls.none { "Email/set" in it })
        }

    @Test
    fun aRefusedSaveReportsWhy() =
        runTest {
            val saved =
                server(
                    """"notCreated": {"draft": {"type": "forbidden"}}, "notDestroyed": {"old": {"type": "notFound"}}""",
                ).save(reply)
            assertEquals(DraftFailure.NotPermitted, saved.failure())
        }

    @Test
    fun discardDestroysTheCopyAndAcceptsOneAlreadyGone() =
        runTest {
            val gone = server(""""notDestroyed": {"old": {"type": "notFound"}}""").discard("ada", "old")
            assertTrue(gone.isSuccess)
            assertEquals(
                listOf("old"),
                method(calls.last()).getValue("destroy").jsonArray.map { it.toString().trim('"') },
            )
            val refused = server(""""notDestroyed": {"old": {"type": "forbidden"}}""").discard("ada", "old")
            assertEquals(DraftFailure.NotPermitted, refused.failure())
        }

    @Test
    fun identitiesComeFromIdentityGetWhenTheAccountCanSubmit() =
        runTest {
            val identities =
                repository(SUBMISSION_SESSION) { body ->
                    check("Identity/get" in body && "urn:ietf:params:jmap:submission" in body)
                    """{"methodResponses": [["Identity/get", {"state": "i", "list": [
                        {"id": "i1", "name": "Ada Lovelace", "email": "ada@example.org"},
                        {"id": "i2", "name": "", "email": "team@example.org"}]}, "c0"]]}"""
                }.identities("ada")
            assertEquals(
                listOf(
                    MailSender("Ada Lovelace", "ada@example.org", "ada"),
                    MailSender("team@example.org", "team@example.org", "ada"),
                ),
                identities,
            )
        }

    @Test
    fun identitiesAreEmptyWithoutSubmissionOrWhenTheServerFails() =
        runTest {
            assertEquals(emptyList(), repository { error("No call without submission") }.identities("ada"))
            val failing =
                repository(SUBMISSION_SESSION) {
                    """{"methodResponses": [["error", {"type": "serverFail"}, "c0"]]}"""
                }
            assertEquals(emptyList(), failing.identities("ada"))
            assertEquals(emptyList(), failing.identities("nobody"))
        }

    @Test
    fun aLoginWithoutMailHasNoIdentitiesAndKeepsNoDrafts() =
        runTest {
            val contactsOnly = repository(CONTACTS_ONLY_SESSION) { error("No call without mail") }
            assertEquals(emptyList(), contactsOnly.identities("ada"))
            assertEquals(DraftFailure.ServerProblem, contactsOnly.save(reply).failure())
            assertEquals(DraftFailure.ServerProblem, contactsOnly.discard("ada", "old").failure())
        }

    private fun method(body: String): JsonObject =
        Json
            .parseToJsonElement(body)
            .jsonObject
            .getValue("methodCalls")
            .jsonArray
            .single()
            .jsonArray[1]
            .jsonObject

    private fun Result<*>.failure(): DraftFailure? = (exceptionOrNull() as? DraftException)?.failure

    private companion object {
        val SUBMISSION_SESSION =
            STALWART_SESSION
                .replace(
                    "\"urn:ietf:params:jmap:mail\": {}}",
                    "\"urn:ietf:params:jmap:mail\": {}, \"urn:ietf:params:jmap:submission\": {}}",
                )
    }
}
