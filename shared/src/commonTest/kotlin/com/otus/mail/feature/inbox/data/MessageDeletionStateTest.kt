package com.otus.mail.feature.inbox.data

import com.otus.jmap.JmapClient
import com.otus.jmap.JmapConnections
import com.otus.jmap.login.InMemoryLoginStore
import com.otus.mail.feature.inbox.domain.MailLocation
import com.otus.mail.feature.inbox.domain.MailMessage
import com.otus.mail.feature.inbox.domain.MailSender
import com.otus.mail.feature.inbox.domain.ReceivedAt
import com.otus.mail.testing.ADA_LOGIN
import com.otus.mail.testing.stalwartLike
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MessageDeletionStateTest {
    private val login = ADA_LOGIN
    private val sets = mutableListOf<JsonObject>()
    private var queried = false
    private val message =
        MailMessage(
            "ada/m1",
            MailSender("Grace", "grace@example.org"),
            "Keep the archived copy",
            "",
            ReceivedAt("2026-10-24", "09:30"),
            MailLocation("Trash", "Ada", "ada", mailboxId = "trash", mailboxIds = setOf("trash")),
            emailId = "m1",
        )

    @Test
    fun aCopyArchivedSinceTheInboxLoadedIsRemovedOnlyFromTrash() =
        runTest {
            val removed = cleaner("""{"trash":true,"archive":true}""").delete(listOf(message))
            assertEquals(setOf(message.id), removed)
            val patch =
                sets
                    .single()
                    .getValue("update")
                    .jsonObject
                    .getValue("m1")
                    .jsonObject
            assertEquals(setOf("mailboxIds/trash"), patch.keys)
            assertFalse("destroy" in sets.single())
            assertEquals(
                "fresh",
                sets
                    .single()
                    .getValue("ifInState")
                    .jsonPrimitive.content,
            )
        }

    @Test
    fun concurrentMembershipChangesRejectTheDestroyWithoutRetryingTheStalePlan() =
        runTest {
            val removed = cleaner("""{"trash":true}""", stateMismatch = true).delete(listOf(message))
            assertTrue(removed.isEmpty())
            assertEquals(1, sets.size)
            assertEquals(
                "fresh",
                sets
                    .single()
                    .getValue("ifInState")
                    .jsonPrimitive.content,
            )
            assertEquals(
                listOf("m1"),
                sets
                    .single()
                    .getValue("destroy")
                    .jsonArray
                    .map { it.jsonPrimitive.content },
            )
        }

    @Test
    fun anEmailAlreadyMovedOutOfTrashIsNotDestroyedInItsNewFolder() =
        runTest {
            val removed = cleaner("""{"archive":true}""").delete(listOf(message))
            assertEquals(setOf(message.id), removed)
            assertTrue(sets.isEmpty())
        }

    @Test
    fun emptyFolderAlsoRechecksMembershipAfterEnumeratingTheFolder() =
        runTest {
            val removed = cleaner("""{"trash":true,"archive":true}""").empty("ada", "trash")
            assertTrue(removed.complete)
            assertEquals(setOf("m1"), removed.emailIds)
            assertFalse("destroy" in sets.single())
            assertEquals(
                "fresh",
                sets
                    .single()
                    .getValue("ifInState")
                    .jsonPrimitive.content,
            )
        }

    private fun cleaner(
        mailboxes: String,
        stateMismatch: Boolean = false,
    ): JmapMessageCleaner {
        val engine =
            stalwartLike { body ->
                val calls =
                    Json
                        .parseToJsonElement(body)
                        .jsonObject
                        .getValue("methodCalls")
                        .jsonArray
                val call = calls.first().jsonArray
                when (call[0].jsonPrimitive.content) {
                    "Email/query" -> {
                        queryReply()
                    }

                    "Email/get" -> {
                        """{"methodResponses":[["Email/get",
                    {"state":"fresh","list":[{"id":"m1","mailboxIds":$mailboxes}]},"c0"]]}"""
                    }

                    "Email/set" -> {
                        val arguments = call[1].jsonObject
                        sets += arguments
                        if (stateMismatch) {
                            """{"methodResponses":[["error",{"type":"stateMismatch"},"c0"]]}"""
                        } else {
                            val result =
                                if ("destroy" in
                                    arguments
                                ) {
                                    """"destroyed":["m1"]"""
                                } else {
                                    """"updated":{"m1":null}"""
                                }
                            """{"methodResponses":[["Email/set",{"newState":"next",$result},"c0"]]}"""
                        }
                    }

                    else -> {
                        error("Unexpected call")
                    }
                }
            }
        val store = InMemoryLoginStore().apply { save(login, "secret") }
        return JmapMessageCleaner(JmapConnections(JmapClient(engine), store))
    }

    private fun queryReply(): String {
        val ids = if (queried) "" else """"m1""""
        val emails = if (queried) "" else """{"id":"m1","mailboxIds":{"trash":true}}"""
        val position = if (queried) 1 else 0
        queried = true
        return """{"methodResponses":[
            ["Email/query",{"ids":[$ids],"position":$position},"c0"],
            ["Email/get",{"state":"old","list":[$emails]},"c1"]]}"""
    }
}
