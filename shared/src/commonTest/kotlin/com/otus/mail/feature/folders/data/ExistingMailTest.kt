package com.otus.mail.feature.folders.data

import com.otus.jmap.JmapClient
import com.otus.jmap.JmapConnections
import com.otus.jmap.login.InMemoryLoginStore
import com.otus.jmap.login.JmapLogin
import com.otus.mail.feature.folders.domain.AddressField
import com.otus.mail.feature.folders.domain.AddressMatch
import com.otus.mail.feature.folders.domain.FolderRule
import com.otus.mail.feature.folders.domain.HeaderOperator
import com.otus.mail.feature.folders.domain.RuleCondition
import com.otus.mail.feature.folders.domain.TextField
import com.otus.mail.feature.folders.domain.TextOperator
import com.otus.mail.feature.folders.domain.WEEKEND
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ExistingMailTest {
    private val agency = RuleCondition.Address(AddressField.From, AddressMatch.Domain("agency.example"))

    @Test
    fun aRuleBecomesAnEmailQueryFilterInTheInboxAndArchive() {
        val rule =
            FolderRule(
                listOf(
                    agency,
                    RuleCondition.Text(TextField.Subject, TextOperator.Contains, "Invoice", negated = true),
                    RuleCondition.Size(over = true, bytes = 1000),
                    RuleCondition.MailingList("news.example"),
                ),
            )
        assertEquals(
            """{"operator":"AND","conditions":[""" +
                """{"operator":"OR","conditions":[{"inMailbox":"I"},{"inMailbox":"A"}]},""" +
                """{"from":"@agency.example"},{"operator":"NOT","conditions":[{"subject":"Invoice"}]},""" +
                """{"minSize":1001},{"header":["List-Id","<news.example>"]}]}""",
            emailFilter(rule, listOf("I", "A")).toString(),
        )
        val more =
            listOf(
                RuleCondition.Address(AddressField.Cc, AddressMatch.Exact("team@x.example")) to
                    """{"cc":"team@x.example"}""",
                RuleCondition.Text(TextField.Body, TextOperator.Contains, "WS-24") to """{"body":"WS-24"}""",
                RuleCondition.Header("X-Project", HeaderOperator.Contains, "web") to
                    """{"header":["X-Project","web"]}""",
                RuleCondition.Size(over = false, bytes = 500) to """{"maxSize":500}""",
                RuleCondition.Size(over = true, bytes = 0) to """{"minSize":1}""",
                RuleCondition.Size(over = false, bytes = 0) to """{"maxSize":0}""",
                RuleCondition.Size(over = true, bytes = Long.MAX_VALUE - 1) to
                    """{"minSize":9223372036854775807}""",
            )
        more.forEach { (condition, filter) ->
            val expected = """{"operator":"AND","conditions":[{"inMailbox":"I"},$filter]}"""
            assertEquals(expected, emailFilter(FolderRule(condition), listOf("I")).toString())
        }
    }

    @Test
    fun anEmptyMailboxScopeKeepsItsEmptyOrCondition() {
        assertEquals(
            """{"operator":"AND","conditions":[{"operator":"OR","conditions":[]},{"from":"@agency.example"}]}""",
            emailFilter(FolderRule(agency), emptyList()).toString(),
        )
    }

    @Test
    fun conditionsJmapCannotFilterHideTheCount() {
        listOf(
            RuleCondition.When(WEEKEND),
            RuleCondition.Spam(5),
            RuleCondition.Address(AddressField.From, AddressMatch.LocalPart("billing")),
            RuleCondition.Text(TextField.Subject, TextOperator.StartsWith, "[Acme]"),
            RuleCondition.Text(TextField.Subject, TextOperator.Matches, "Invoice #*"),
            RuleCondition.Header("X-Project", HeaderOperator.Is, "website"),
            RuleCondition.Text(TextField.Subject, TextOperator.StartsWith, "[Acme]", negated = true),
        ).forEach { assertNull(emailFilter(FolderRule(listOf(agency, it)), listOf("I")), "$it") }
    }

    @Test
    fun theCountComesFromTheQueryTotalAndMatchesMoveInBatches() =
        runTest {
            val calls = mutableListOf<String>()
            var left = 3
            val json = headersOf(HttpHeaders.ContentType, "application/json")
            val engine =
                MockEngine { request ->
                    if (request.url.encodedPath == "/jmap/session") {
                        return@MockEngine respond(SESSION, HttpStatusCode.OK, json)
                    }
                    val body = (request.body as TextContent).text
                    calls += body
                    val reply =
                        when {
                            "Mailbox/get" in body -> {
                                """["Mailbox/get", {"state": "m", "list": [{"id": "I", "role": "inbox"},
                                {"id": "A", "role": "archive"}, {"id": "P"}]}, "c0"]"""
                            }

                            "calculateTotal" in body -> {
                                """["Email/query", {"ids": ["e1"], "total": 14}, "c0"]"""
                            }

                            "Email/query" in body && "\"inMailbox\":\"I\"" in body -> {
                                val ids = (1..minOf(left, 2)).map { "\"i$left$it\"" }
                                """["Email/query", {"ids": $ids}, "c0"]"""
                            }

                            "Email/query" in body -> {
                                """["Email/query", {"ids": []}, "c0"]"""
                            }

                            else -> {
                                val ids = Regex("\"(i\\d+)\":").findAll(body).map { it.groupValues[1] }.toList()
                                left -= ids.size
                                val updated = ids.joinToString { "\"$it\": null" }
                                """["Email/set", {"newState": "e", "updated": {$updated}}, "c0"]"""
                            }
                        }
                    respond("""{"methodResponses": [$reply]}""", HttpStatusCode.OK, json)
                }
            val login = JmapLogin("ada", "https://mail.example.org/jmap/session", "ada@example.org", "Ada")
            val store = InMemoryLoginStore().apply { save(login, "secret") }
            val repository = JmapFolderRuleRepository(JmapConnections(JmapClient(engine), store))
            assertEquals(14, repository.existingCount("ada", "P", FolderRule(agency)))
            val count = calls.last()
            assertTrue(
                "\"inMailbox\":\"I\"" in count && "\"inMailbox\":\"A\"" in count && "\"operator\":\"OR\"" in count,
            )
            assertEquals(3, repository.moveExisting("ada", "P", FolderRule(agency)))
            val sets = calls.filter { "Email/set" in it }
            assertEquals(2, sets.size)
            assertTrue("\"mailboxIds/I\":null" in sets.first() && "\"mailboxIds/P\":true" in sets.first(), sets.first())
            assertNull(repository.existingCount("ada", "P", FolderRule(RuleCondition.Spam(5))))
        }

    private companion object {
        const val SESSION =
            """{"capabilities": {"urn:ietf:params:jmap:core": {"maxObjectsInSet": 2}, "urn:ietf:params:jmap:mail": {}},
            "accounts": {"d1": {"name": "Ada", "accountCapabilities": {"urn:ietf:params:jmap:mail": {}}}},
            "primaryAccounts": {"urn:ietf:params:jmap:mail": "d1"}, "username": "ada@example.org",
            "apiUrl": "/jmap/", "downloadUrl": "/download/{blobId}", "uploadUrl": "/upload/{accountId}/"}"""
    }
}
