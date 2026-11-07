package com.otus.mail.feature.inbox.data

import com.otus.jmap.JmapClient
import com.otus.jmap.JmapConnections
import com.otus.jmap.login.InMemoryLoginStore
import com.otus.mail.core.messages.domain.Deletion
import com.otus.mail.core.messages.domain.MailLocation
import com.otus.mail.core.messages.domain.MailMessage
import com.otus.mail.core.messages.domain.MailSender
import com.otus.mail.core.messages.domain.ReceivedAt
import com.otus.mail.testing.ADA_LOGIN
import com.otus.mail.testing.STALWART_SESSION
import com.otus.mail.testing.emailWriter
import com.otus.mail.testing.stalwartLike
import kotlinx.coroutines.test.runTest
import kotlinx.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Deleting for good waits for the moves and marks the user made offline. */
class EmptyAfterQueuedMoveTest {
    private val calls = mutableListOf<String>()
    private var offline = true

    private val writer =
        emailWriter(
            JmapConnections(
                JmapClient(
                    stalwartLike(STALWART_SESSION) { body ->
                        if (offline) throw IOException("offline")
                        calls += body
                        error("unexpected call while the test is offline: $body")
                    },
                ),
                InMemoryLoginStore().apply { save(ADA_LOGIN, "secret") },
            ),
        )

    private val restored =
        MailMessage(
            "ada/m1",
            MailSender("Grace", "grace@example.org"),
            "Order",
            "",
            ReceivedAt("2026-10-04", "09:30"),
            MailLocation("Trash", "Ada", "ada", mailboxId = "t", mailboxIds = setOf("t")),
            emailId = "m1",
        )

    @Test
    fun emptyingTrashWhileAMoveOutOfItWaitsDestroysNothing() =
        runTest {
            assertTrue(writer.move(restored, "in", leaving = setOf("t")), "the move waits and shows")
            val emptied = writer.empty("ada", "t")
            assertEquals(Deletion(emptySet(), complete = false), emptied)
            assertFalse(calls.any { "destroy" in it }, "nothing was destroyed: $calls")
        }
}
