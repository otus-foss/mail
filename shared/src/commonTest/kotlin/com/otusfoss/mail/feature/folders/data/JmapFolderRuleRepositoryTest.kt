package com.otusfoss.mail.feature.folders.data

import com.otusfoss.core.jmap.JmapClient
import com.otusfoss.core.jmap.JmapConnections
import com.otusfoss.core.jmap.login.InMemoryLoginStore
import com.otusfoss.core.jmap.login.JmapLogin
import com.otusfoss.mail.core.cache.InMemoryMailCache
import com.otusfoss.mail.core.mailbox.Mailboxes
import com.otusfoss.mail.core.messages.data.EmailChangeQueue
import com.otusfoss.mail.feature.folders.domain.AddressField
import com.otusfoss.mail.feature.folders.domain.AddressMatch
import com.otusfoss.mail.feature.folders.domain.FolderRule
import com.otusfoss.mail.feature.folders.domain.RuleAvailability
import com.otusfoss.mail.feature.folders.domain.RuleCondition
import com.otusfoss.mail.feature.folders.domain.RuleFailure
import com.otusfoss.mail.feature.folders.domain.RuleOutcome
import com.otusfoss.mail.feature.folders.domain.RuleSupport
import com.otusfoss.mail.testing.ADA_SECRET_HEADER
import com.otusfoss.mail.testing.UNCHANGED_MAILBOXES
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class JmapFolderRuleRepositoryTest {
    private val login = JmapLogin("ada", "https://mail.example.org/jmap/session", "ada@example.org", "Ada")
    private val fromJana =
        FolderRule(RuleCondition.Address(AddressField.From, AddressMatch.Exact("jana@agency.example")))
    private val handWritten = "require \"vacation\";\nif header :is \"x-spam\" \"yes\" { discard; }\n"
    private val calls = mutableListOf<String>()
    private var uploaded = ""

    /**
     * A fake server: [scripts] answers `SieveScript/get`, [stored] is each script's text, and
     * [validation] is the validate error.
     */
    private fun repository(
        session: String = session(),
        scripts: String = "[]",
        stored: String = "",
        validation: String = "null",
        set: String = """{"newState": "s2", "created": {"rules": {"id": "S9"}}}""",
    ): JmapFolderRuleRepository {
        val json = headersOf(HttpHeaders.ContentType, "application/json")
        val sieve = headersOf(HttpHeaders.ContentType, "application/sieve")
        val engine =
            MockEngine { request ->
                check(request.headers[HttpHeaders.Authorization] == ADA_SECRET_HEADER)
                when (request.url.encodedPath) {
                    "/jmap/session" -> {
                        respond(session, HttpStatusCode.OK, json)
                    }

                    "/download/B1" -> {
                        respond(stored, HttpStatusCode.OK, sieve)
                    }

                    "/upload/d1/" -> {
                        uploaded = (request.body as OutgoingContent.ByteArrayContent).bytes().decodeToString()
                        respond(UPLOADED, HttpStatusCode.OK, json)
                    }

                    else -> {
                        val body = (request.body as TextContent).text
                        calls += body
                        val reply =
                            when {
                                "SieveScript/get" in body -> scriptsReply(scripts)
                                "SieveScript/validate" in body -> validateReply(validation)
                                "SieveScript/set" in body -> """["SieveScript/set", $set, "c0"]"""
                                "Mailbox/changes" in body -> UNCHANGED_MAILBOXES
                                "Mailbox/get" in body -> """["Mailbox/get", {"state": "m", "list": $MAILBOXES}, "c0"]"""
                                else -> error("Unexpected call: $body")
                            }
                        respond("""{"methodResponses": [$reply]}""", HttpStatusCode.OK, json)
                    }
                }
            }
        val store = InMemoryLoginStore().apply { save(login, "secret") }
        val connections = JmapConnections(JmapClient(engine), store)
        val cache = InMemoryMailCache()
        return JmapFolderRuleRepository(
            connections,
            Mailboxes(cache),
            EmailChangeQueue(connections, cache, store = null),
        ) { "+0200" }
    }

    @Test
    fun serversWithoutSieveOrFileintoHaveNoRulesRow() =
        runTest {
            val noSieve = repository(session(sieve = false)).accounts().single()
            assertEquals(RuleAvailability.RulesUnavailable, noSieve.availability)
            val noFiling = repository(session(extensions = """["body"]""")).accounts().single()
            assertEquals(RuleAvailability.RulesUnavailable, noFiling.availability)
            assertTrue(calls.isEmpty(), "Nothing is asked of a server that cannot run rules")
        }

    @Test
    fun theFirstSaveCreatesValidatesAndActivatesOtusMail() =
        runTest {
            val repository = repository()
            val account = repository.accounts().single()
            assertEquals(null, account.script?.scriptId)
            assertEquals(RuleOutcome.Saved, repository.save(account, mapOf("P" to listOf(fromJana))))
            assertTrue(uploaded.startsWith("$REQUIRE\r\n# BEGIN otus-rules\r\n"), uploaded)
            assertTrue("""fileinto :mailboxid "P" "Clients/Projects";""" in uploaded, uploaded)
            val methods = calls.map { Regex("\"(SieveScript/\\w+|Mailbox/get)\"").find(it)?.groupValues?.get(1) }
            assertEquals(listOf("SieveScript/validate", "SieveScript/set"), methods.takeLast(2))
            val set = calls.last()
            listOf(
                "\"create\":{\"rules\":{\"name\":\"Otus Mail\",\"blobId\":\"U1\"}}",
                "\"ifInState\":\"s1\"",
                "\"onSuccessActivateScript\":\"#rules\"",
            ).forEach { assertTrue(it in set, set) }
        }

    @Test
    fun aHandWrittenActiveScriptGetsTheBlockAndKeepsEverythingElse() =
        runTest {
            val scripts = """[{"id": "S1", "name": "Mine", "blobId": "B1", "isActive": true}]"""
            val updated = """{"newState": "s2", "updated": {"S1": null}}"""
            val repository = repository(scripts = scripts, stored = handWritten, set = updated)
            val account = repository.accounts().single()
            assertEquals("Mine", account.script?.scriptName)
            assertFalse(account.script?.hasBlock == true)
            assertEquals(RuleOutcome.Saved, repository.save(account, mapOf("P" to listOf(fromJana))))
            val block = blockText(uploaded).orEmpty()
            assertEquals(handWritten, uploaded.replace(block, "").replace("$REQUIRE\r\n", ""))
            assertTrue(uploaded.startsWith("require \"vacation\";\n$REQUIRE\r\nif header"), uploaded)
            assertTrue(uploaded.endsWith(block), "The block goes last, so the script's own rules run first")
            val set = calls.last()
            assertTrue("\"update\":{\"S1\":{\"blobId\":\"U1\"}}" in set && "onSuccessActivateScript" !in set, set)
        }

    @Test
    fun savedRulesLoadBackInFolderOrder() =
        runTest {
            val block =
                RuleBlock(listOf(BlockFolder("P", "x", listOf(fromJana)), BlockFolder("C", "y", listOf(fromJana))))
            val stored = handWritten + encodeBlock(block, support)
            val scripts = """[{"id": "S1", "name": "Mine", "blobId": "B1", "isActive": true}]"""
            val account = repository(scripts = scripts, stored = stored).accounts().single()
            assertEquals(listOf("C", "P"), account.rules.keys.toList(), "Clients comes before its subfolder")
            assertTrue(account.script?.hasBlock == true)
            assertFalse(account.changedElsewhere)
        }

    @Test
    fun aRefusedScriptKeepsTheOldOneAndSaysWhy() =
        runTest {
            val error = """{"type": "invalidSieve", "description": "line 3: unknown test"}"""
            val refusing = repository(validation = error)
            val outcome = refusing.save(refusing.accounts().single(), mapOf("P" to listOf(fromJana)))
            assertEquals(RuleOutcome.Failed(RuleFailure.Invalid, "line 3: unknown test"), outcome)
            assertFalse(calls.any { "SieveScript/set" in it }, "Nothing replaces the running script")
        }

    @Test
    fun aChangeMadeElsewhereIsRefused() =
        runTest {
            val repository = repository()
            val loaded = repository.accounts().single()
            val stale = loaded.copy(script = loaded.script?.copy(state = "s0"))
            assertEquals(RuleOutcome.Failed(RuleFailure.ChangedElsewhere), repository.save(stale, emptyMap()))
            assertEquals("", uploaded, "Nothing is uploaded")
            // A change between the read and the write fails ifInState.
            val conflicting = repositoryAnswering("""["error", {"type": "stateMismatch"}, "c0"]""")
            assertEquals(
                RuleOutcome.Failed(RuleFailure.ChangedElsewhere),
                conflicting.save(conflicting.accounts().single(), mapOf("P" to listOf(fromJana))),
            )
        }

    @Test
    fun withoutMailboxIdRulesFileByThePath() =
        runTest {
            val repository = repository(session(extensions = """["fileinto"]"""))
            val account = repository.accounts().single()
            assertEquals(RuleSupport(setOf("fileinto")), account.support)
            repository.save(account, mapOf("P" to listOf(fromJana)))
            assertTrue("""    fileinto "Clients/Projects";""" in uploaded, uploaded)
        }

    @Test
    fun anActiveVacationReplyBlocksRules() =
        runTest {
            val scripts = """[{"id": "S4", "name": "vacation", "blobId": "B1", "isActive": true}]"""
            val account = repository(scripts = scripts).accounts().single()
            assertEquals(RuleAvailability.Failed(RuleFailure.VacationActive), account.availability)
            assertNull(account.script)
        }

    private fun repositoryAnswering(setReply: String): JmapFolderRuleRepository {
        val json = headersOf(HttpHeaders.ContentType, "application/json")
        val engine =
            MockEngine { request ->
                when (request.url.encodedPath) {
                    "/jmap/session" -> {
                        respond(session(), HttpStatusCode.OK, json)
                    }

                    "/upload/d1/" -> {
                        respond(UPLOADED, HttpStatusCode.OK, json)
                    }

                    else -> {
                        val body = (request.body as TextContent).text
                        val reply =
                            when {
                                "SieveScript/get" in body -> scriptsReply("[]")
                                "SieveScript/validate" in body -> validateReply("null")
                                "SieveScript/set" in body -> setReply
                                "Mailbox/changes" in body -> UNCHANGED_MAILBOXES
                                else -> """["Mailbox/get", {"state": "m", "list": $MAILBOXES}, "c0"]"""
                            }
                        respond("""{"methodResponses": [$reply]}""", HttpStatusCode.OK, json)
                    }
                }
            }
        val store = InMemoryLoginStore().apply { save(login, "secret") }
        val connections = JmapConnections(JmapClient(engine), store)
        val cache = InMemoryMailCache()
        return JmapFolderRuleRepository(
            connections,
            Mailboxes(cache),
            EmailChangeQueue(connections, cache, store = null),
        ) { "+0200" }
    }

    private val support = RuleSupport(setOf("fileinto", "mailboxid"))

    private fun session(
        sieve: Boolean = true,
        extensions: String = """["fileinto", "mailboxid"]""",
    ): String {
        val capability = if (sieve) """, "urn:ietf:params:jmap:sieve": {"sieveExtensions": $extensions}""" else ""
        return """{"capabilities": {"urn:ietf:params:jmap:core": {}, "urn:ietf:params:jmap:mail": {},
            "urn:ietf:params:jmap:sieve": {}},
            "accounts": {"d1": {"name": "Ada", "accountCapabilities": {"urn:ietf:params:jmap:mail": {}$capability}}},
            "primaryAccounts": {"urn:ietf:params:jmap:mail": "d1"}, "username": "ada@example.org",
            "apiUrl": "/jmap/", "downloadUrl": "/download/{blobId}", "uploadUrl": "/upload/{accountId}/"}"""
    }

    private fun scriptsReply(list: String) = """["SieveScript/get", {"state": "s1", "list": $list}, "c0"]"""

    private fun validateReply(error: String) = """["SieveScript/validate", {"error": $error}, "c0"]"""

    private companion object {
        const val REQUIRE = """require ["fileinto", "mailboxid"]; # otus-rules"""

        const val UPLOADED = """{"blobId": "U1", "type": "application/sieve", "size": 1}"""
        const val MAILBOXES =
            """[{"id": "I", "name": "Inbox", "role": "inbox"}, {"id": "A", "name": "Archive", "role": "archive"},
            {"id": "P", "name": "Projects", "parentId": "C"}, {"id": "C", "name": "Clients"}]"""
    }
}
