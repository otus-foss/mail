package com.otus.mail.feature.inbox.data

import com.otus.jmap.JmapClient
import com.otus.jmap.JmapConnections
import com.otus.jmap.login.InMemoryLoginStore
import com.otus.mail.feature.inbox.domain.Draft
import com.otus.mail.feature.inbox.domain.DraftException
import com.otus.mail.feature.inbox.domain.DraftFailure
import com.otus.mail.feature.inbox.domain.MailSender
import com.otus.mail.feature.inbox.domain.SendIdentity
import com.otus.mail.testing.ADA_LOGIN
import com.otus.mail.testing.CONTACTS_ONLY_SESSION
import com.otus.mail.testing.STALWART_SESSION
import com.otus.mail.testing.SUBMISSION_SESSION
import com.otus.mail.testing.stalwartLike
import kotlinx.coroutines.test.runTest
import kotlinx.io.IOException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class JmapDraftRepositoryTest {
    private val login = ADA_LOGIN
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
        return JmapDraftRepository(JmapConnections(JmapClient(engine), store), store) { "m${++saves}@otus.mail" }
    }

    private var saves = 0

    /** Answers the `Email/set` that creates a draft with [created], and any other one with [destroyed]. */
    private fun server(
        created: String = """"created": {"draft": {"id": "e9"}}""",
        destroyed: String = emailSet(""""destroyed": ["old"]"""),
    ) = repository { body ->
        when {
            "Mailbox/query" in body -> """{"methodResponses": [["Mailbox/query", {"ids": ["dr"]}, "c0"]]}"""
            "\"create\"" in body -> emailSet(created)
            "Email/set" in body -> destroyed
            else -> error("Unexpected call: $body")
        }
    }

    private fun emailSet(outcome: String) =
        """{"methodResponses": [["Email/set", {"newState": "e2", $outcome}, "c0"]]}"""

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
    fun aSaveCreatesTheDraftInTheDraftsFolderAndThenDestroysItsOldCopy() =
        runTest {
            val saved = server().save(reply)
            assertEquals("e9", saved.getOrThrow())
            assertTrue("\"filter\":{\"role\":\"drafts\"}" in calls.first(), "The Drafts folder is found by role")
            val (set, destroy) = calls.drop(1).map(::method)
            assertTrue("destroy" !in set, "The old copy stays until the new one is stored")
            assertEquals(setOf("accountId", "destroy"), destroy.keys)
            assertEquals(listOf("old"), destroy.getValue("destroy").jsonArray.map { it.toString().trim('"') })
            val expected =
                """{"mailboxIds": {"dr": true}, "keywords": {"${'$'}draft": true, "${'$'}seen": true},
                "from": [{"name": "Ada Lovelace", "email": "ada@example.org"}],
                "to": [{"email": "grace@example.org"}], "subject": "Re: Compiler draft",
                "messageId": ["m1@otus.mail"], "inReplyTo": ["m1@example.org"], "references": ["m0@example.org", "m1@example.org"],
                "bodyValues": {"text": {"value": "Looks good."}},
                "textBody": [{"partId": "text", "type": "text/plain"}]}"""
            assertEquals(Json.parseToJsonElement(expected), set.getValue("create").jsonObject.getValue("draft"))
        }

    @Test
    fun aFirstSaveDestroysNothingAndLeavesEmptyHeadersOut() =
        runTest {
            val saved = server(""""created": {"draft": {"id": "e1"}}""").save(Draft(accountId = "ada"))
            assertEquals("e1", saved.getOrThrow())
            val set = method(calls.single { "Email/set" in it })
            assertTrue("destroy" !in set)
            val email =
                set
                    .getValue("create")
                    .jsonObject
                    .getValue("draft")
                    .jsonObject
            assertEquals(setOf("mailboxIds", "keywords", "subject", "messageId", "bodyValues", "textBody"), email.keys)
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
    fun aRejectedSaveReportsWhyAndKeepsTheOldCopy() =
        runTest {
            val refused = server(""""notCreated": {"draft": {"type": "forbidden"}}""").save(reply)
            assertEquals(DraftFailure.NotPermitted, refused.failure())
            val overQuota = server(""""notCreated": {"draft": {"type": "overQuota"}}""").save(reply)
            assertEquals(DraftFailure.ServerProblem, overQuota.failure())
            assertTrue(calls.none { "destroy" in it }, "Without a new copy, the old one is not destroyed")
        }

    @Test
    fun aStoredSaveSucceedsWhenItsOldCopyCannotBeDestroyed() =
        runTest {
            val kept = server(destroyed = emailSet(""""notDestroyed": {"old": {"type": "forbidden"}}"""))
            assertEquals("e9", kept.save(reply).getOrThrow())
            val failing = server(destroyed = """{"methodResponses": [["error", {"type": "serverFail"}, "c0"]]}""")
            assertEquals("e9", failing.save(reply).getOrThrow())
            assertEquals(2, calls.count { "destroy" in it })
        }

    @Test
    fun discardDestroysTheCopyAndAcceptsOneAlreadyGone() =
        runTest {
            val gone =
                server(
                    destroyed = emailSet(""""notDestroyed": {"old": {"type": "notFound"}}"""),
                ).discard("ada", "old")
            assertTrue(gone.isSuccess)
            assertEquals(
                listOf("old"),
                method(calls.last()).getValue("destroy").jsonArray.map { it.toString().trim('"') },
            )
            val refused =
                server(
                    destroyed = emailSet(""""notDestroyed": {"old": {"type": "forbidden"}}"""),
                ).discard("ada", "old")
            assertEquals(DraftFailure.NotPermitted, refused.failure())
        }

    @Test
    fun aCopyWhoseSaveLostItsAnswerIsFoundAndDestroyedByTheNextSave() =
        runTest {
            var lost = true
            val drafts =
                repository { body ->
                    when {
                        "Mailbox/query" in body -> """{"methodResponses": [["Mailbox/query", {"ids": ["dr"]}, "c0"]]}"""
                        lost && "\"create\"" in body -> throw IOException("The answer was lost")
                        "\"create\"" in body -> emailSet(""""created": {"draft": {"id": "e2"}}""")
                        "Email/query" in body -> """{"methodResponses": [["Email/query", $FOUND, "c0"]]}"""
                        else -> emailSet(""""destroyed": ["e1"]""")
                    }
                }
            assertEquals(DraftFailure.Unreachable, drafts.save(reply.copy(emailId = null)).failure())
            lost = false
            assertEquals("e2", drafts.save(reply.copy(emailId = null)).getOrThrow())
            val query = method(calls.single { "Email/query" in it }).getValue("filter").toString()
            assertTrue("m1@otus.mail" in query && "\"inMailbox\":\"dr\"" in query, query)
            assertEquals(
                listOf("e1"),
                method(calls.last()).getValue("destroy").jsonArray.map { it.jsonPrimitive.content },
            )

            calls.clear()
            drafts.save(reply.copy(emailId = null)).getOrThrow()
            assertTrue(calls.none { "Email/query" in it }, "A copy found once is not looked for again")
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
                    SendIdentity("i1", MailSender("Ada Lovelace", "ada@example.org", "ada")),
                    SendIdentity("i2", MailSender("team@example.org", "team@example.org", "ada")),
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
        /** The lost copy `e1` and the new copy `e2`, which share nothing but the Drafts folder. */
        const val FOUND = """{"queryState": "q", "ids": ["e1", "e2"], "position": 0}"""
    }
}
