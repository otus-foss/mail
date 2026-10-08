package com.otus.mail.feature.folders.data

import com.otus.jmap.JmapClient
import com.otus.jmap.JmapConnections
import com.otus.jmap.login.InMemoryLoginStore
import com.otus.jmap.login.JmapLogin
import com.otus.mail.feature.folders.domain.AccountRules
import com.otus.mail.feature.folders.domain.MailRule
import com.otus.mail.feature.folders.domain.RuleAvailability
import com.otus.mail.feature.folders.domain.RuleChange
import com.otus.mail.feature.folders.domain.RuleCondition
import com.otus.mail.feature.folders.domain.RuleFailure
import com.otus.mail.feature.folders.domain.RuleField
import com.otus.mail.feature.folders.domain.RuleOperator
import com.otus.mail.feature.folders.domain.RuleOutcome
import com.otus.mail.testing.ADA_SECRET_HEADER
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
import kotlin.test.assertTrue

class JmapRuleRepositoryTest {
    private val login = JmapLogin("ada", "https://mail.example.org/jmap/session", "ada@example.org", "Ada")
    private val rule =
        MailRule("r1", "Receipts", listOf(RuleCondition(RuleField.From, RuleOperator.Contains, "amazon")), "M7")
    private val calls = mutableListOf<String>()
    private var uploaded = ""

    /** [scripts] answers `SieveScript/get`; [set] is the whole `SieveScript/set` reply; [stored] is our blob. */
    private fun repository(
        session: String = session(),
        scripts: String = "[]",
        state: String = "s1",
        set: String = """["SieveScript/set", {"newState": "s2", "created": {"rules": {"id": "S9"}}}, "c0"]""",
        stored: String = "",
    ): JmapRuleRepository {
        val json = headersOf(HttpHeaders.ContentType, "application/json")
        val engine =
            MockEngine { request ->
                check(request.headers[HttpHeaders.Authorization] == ADA_SECRET_HEADER)
                when (request.url.encodedPath) {
                    "/jmap/session" -> {
                        respond(session, HttpStatusCode.OK, json)
                    }

                    "/download/S1" -> {
                        respond(stored, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/sieve"))
                    }

                    "/upload/d1/" -> {
                        uploaded = (request.body as OutgoingContent.ByteArrayContent).bytes().decodeToString()
                        respond("""{"blobId": "B1", "type": "application/sieve", "size": 1}""", HttpStatusCode.OK, json)
                    }

                    else -> {
                        val body = (request.body as TextContent).text
                        calls += body
                        val reply =
                            if ("SieveScript/get" in body) {
                                """["SieveScript/get", {"state": "$state", "list": $scripts}, "c0"]"""
                            } else {
                                set
                            }
                        respond("""{"methodResponses": [$reply]}""", HttpStatusCode.OK, json)
                    }
                }
            }
        val store = InMemoryLoginStore().apply { save(login, "secret") }
        return JmapRuleRepository(JmapConnections(JmapClient(engine), store), store)
    }

    @Test
    fun serversWithoutSieveOrFolderFilingHaveNoRules() =
        runTest {
            val none = repository(session(sieve = null)).accounts().single()
            assertEquals(RuleAvailability.NoSieve, none.availability)
            val byName = repository(session(extensions = """["fileinto"]""")).accounts().single()
            assertEquals(RuleAvailability.NoFolderRules, byName.availability)
            assertTrue(calls.isEmpty(), "Nothing is asked of a server that cannot run rules")
        }

    @Test
    fun ourScriptIsReadBackAndAnotherActiveScriptIsNamed() =
        runTest {
            val stored = encodeRules(RuleScript(listOf(rule), keep = "roundcube"))
            val account =
                repository(
                    scripts =
                        """[{"id": "S1", "name": "Otus Mail", "blobId": "S1", "isActive": false},
                            {"id": "S2", "name": "vacation", "blobId": "S2", "isActive": true}]""",
                    stored = stored,
                ).accounts().single()
            assertEquals(listOf(rule), account.rules)
            assertEquals("roundcube", account.keep)
            assertTrue(account.installed && !account.running && account.vacationIsOn && !account.changedElsewhere)
            assertEquals("s1", account.version)
        }

    @Test
    fun theFirstSaveCreatesUploadsAndActivatesTheScript() =
        runTest {
            val repository = repository()
            val account = repository.accounts().single()
            val outcome = repository.save(account, RuleChange(listOf(rule), keep = null, run = true))
            assertEquals(RuleOutcome.Saved, outcome)
            assertTrue("fileinto :mailboxid \"M7\" \"INBOX\";" in uploaded, uploaded)
            val set = calls.last()
            listOf(
                "\"create\":{\"rules\":{\"name\":\"Otus Mail\",\"blobId\":\"B1\"}}",
                "\"ifInState\":\"s1\"",
                "\"onSuccessActivateScript\":\"#rules\"",
                "urn:ietf:params:jmap:sieve",
            ).forEach { assertTrue(it in set, set) }
        }

    @Test
    fun turningOffDeactivatesOnlyOurRunningScript() =
        runTest {
            val scripts = """[{"id": "S1", "name": "Otus Mail", "blobId": "S1", "isActive": true}]"""
            val stored = encodeRules(RuleScript(listOf(rule)))
            val noChanges = """["SieveScript/set", {"newState": "s2"}, "c0"]"""
            val repository = repository(scripts = scripts, stored = stored, set = noChanges)
            val account = repository.accounts().single()
            assertEquals(RuleOutcome.Saved, repository.save(account, RuleChange(listOf(rule), null, run = false)))
            val set = calls.last()
            assertTrue("\"update\":{\"S1\":{\"blobId\":\"B1\"}}" in set && "\"onSuccessDeactivateScript\":true" in set)
        }

    @Test
    fun conflictsAndRejectedScriptsAreReported() =
        runTest {
            val stale = AccountRules("ada", "Ada", repository().accounts().single().availability, version = "s0")
            assertEquals(
                RuleOutcome.Failed(RuleFailure.ChangedElsewhere),
                repository().save(stale, RuleChange(listOf(rule), null)),
            )
            val invalid = """{"newState": "s1",
                "notCreated": {"rules": {"type": "invalidSieve", "description": "line 3"}}}"""
            val rejecting = repository(set = """["SieveScript/set", $invalid, "c0"]""")
            assertEquals(
                RuleOutcome.Failed(RuleFailure.Invalid, "line 3"),
                rejecting.save(rejecting.accounts().single(), RuleChange(listOf(rule), null)),
            )
            val racing = repository(set = """["error", {"type": "stateMismatch"}, "c0"]""")
            assertEquals(
                RuleOutcome.Failed(RuleFailure.ChangedElsewhere),
                racing.save(racing.accounts().single(), RuleChange(listOf(rule), null)),
            )
        }

    private fun session(
        sieve: String? = "sieve",
        extensions: String = """["fileinto", "mailboxid", "imap4flags", "include"]""",
    ): String {
        val capability = sieve?.let { """, "urn:ietf:params:jmap:sieve": {"sieveExtensions": $extensions}""" }.orEmpty()
        return """{"capabilities": {"urn:ietf:params:jmap:core": {}, "urn:ietf:params:jmap:mail": {},
            "urn:ietf:params:jmap:sieve": {}},
            "accounts": {"d1": {"name": "Ada", "accountCapabilities": {"urn:ietf:params:jmap:mail": {}$capability}}},
            "primaryAccounts": {"urn:ietf:params:jmap:mail": "d1"}, "username": "ada@example.org",
            "apiUrl": "/jmap/", "downloadUrl": "/download/{blobId}", "uploadUrl": "/upload/{accountId}/"}"""
    }
}
