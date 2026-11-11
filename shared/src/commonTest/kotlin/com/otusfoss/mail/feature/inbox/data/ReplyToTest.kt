package com.otusfoss.mail.feature.inbox.data

import com.otusfoss.mail.core.mail.EmailObject
import com.otusfoss.mail.core.messages.data.receivedAt
import com.otusfoss.mail.core.messages.data.toMessage
import com.otusfoss.mail.core.messages.domain.ComposeRequest
import com.otusfoss.mail.core.messages.domain.MailLocation
import com.otusfoss.mail.core.messages.domain.original
import com.otusfoss.mail.feature.compose.domain.startDraft
import kotlinx.datetime.TimeZone
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

class ReplyToTest {
    private val email =
        Json.decodeFromString<EmailObject>(
            """{
            "id": "m", "receivedAt": "2026-10-24T12:00:00Z",
            "from": [{"email": "sender@example.com"}],
            "replyTo": [{"email": "team@example.com"}, {"email": "other@example.com"}],
            "to": [{"email": "me@example.com"}, {"email": "team@example.com"}],
            "cc": [{"email": "cc@example.com"}]
        }""",
        )

    @Test
    fun serverReplyAddressesReachTheReplyDraft() {
        val original = email.toMessage(MailLocation("Inbox", "A", "a"), TimeZone.UTC).original()
        val draft = ComposeRequest.Reply(original, all = false).startDraft("a", listOf("me@example.com"))
        assertEquals(listOf("team@example.com", "other@example.com"), draft.to.map { it.address })
        assertEquals(emptyList(), draft.cc)
    }

    @Test
    fun replyAllDeduplicatesReplyAddressesAndOmitsOwnAddress() {
        val original = email.toMessage(MailLocation("Inbox", "A", "a"), TimeZone.UTC).original()
        val draft = ComposeRequest.Reply(original, all = true).startDraft("a", listOf("me@example.com"))
        assertEquals(listOf("team@example.com", "other@example.com"), draft.to.map { it.address })
        assertEquals(listOf("cc@example.com"), draft.cc.map { it.address })
    }

    @Test
    fun missingReplyToFallsBackToTheSender() {
        val original = email.copy(replyTo = null).toMessage(MailLocation("Inbox", "A", "a"), TimeZone.UTC).original()
        val draft = ComposeRequest.Reply(original, all = false).startDraft("a", emptyList())
        assertEquals(listOf("sender@example.com"), draft.to.map { it.address })
    }

    @Test
    fun followUpKeepsTheOriginalRecipients() {
        val original = email.toMessage(MailLocation("Sent", "A", "a"), TimeZone.UTC).original()
        val draft = ComposeRequest.FollowUp(original).startDraft("a", listOf("sender@example.com"))
        assertEquals(listOf("me@example.com", "team@example.com"), draft.to.map { it.address })
    }
}
