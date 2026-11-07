package com.otus.mail.feature.compose.domain

import com.otus.mail.core.mailbox.MailboxRole
import com.otus.mail.core.messages.domain.ComposeRequest
import com.otus.mail.core.messages.domain.Draft
import com.otus.mail.core.messages.domain.MailLocation
import com.otus.mail.core.messages.domain.MailMessage
import com.otus.mail.core.messages.domain.MailSender
import com.otus.mail.core.messages.domain.OriginalMessage
import com.otus.mail.core.messages.domain.ReceivedAt
import com.otus.mail.core.messages.domain.accountId
import com.otus.mail.core.messages.domain.original
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DraftStartTest {
    private val ada = MailSender("Ada Lovelace", "ada@example.com")
    private val me = MailSender("Me", "me@example.com")
    private val bob = MailSender("Bob", "bob@example.com")
    private val cy = MailSender("Cy", "cy@example.com")
    private val original =
        OriginalMessage(
            accountId = "a1",
            emailId = "m1",
            subject = "Lunch",
            from = ada,
            to = listOf(me, bob),
            cc = listOf(cy, MailSender("", "ME@example.com")),
            sentLabel = "2026-10-11 12:30",
            body = "Noon?\n\nAda",
            messageIds = listOf("<m1@example.com>"),
            references = listOf("<m0@example.com>"),
        )
    private val own = listOf("me@example.com")

    private fun reply(
        message: OriginalMessage = original,
        all: Boolean = false,
    ) = ComposeRequest.Reply(message, all).startDraft("a1", own)

    @Test
    fun aNewMessageStartsBlankInItsAccount() {
        assertEquals(Draft("a2"), ComposeRequest.New().startDraft("a2", own))
    }

    @Test
    fun aReplyGoesToTheSenderAndStaysInTheThread() {
        val draft = reply()
        assertEquals(listOf(ada), draft.to)
        assertEquals(emptyList(), draft.cc)
        assertEquals("Re: Lunch", draft.subject)
        assertEquals(listOf("<m1@example.com>"), draft.inReplyTo)
        assertEquals(listOf("<m0@example.com>", "<m1@example.com>"), draft.references)
    }

    @Test
    fun aReplyQuotesTheOriginalAfterAnEmptyBody() {
        val expected = "On 2026-10-11 12:30, Ada Lovelace wrote:\n> Noon?\n>\n> Ada"
        assertEquals(expected, reply().quote)
        assertEquals("", reply().body)
    }

    @Test
    fun aSubjectThatIsAlreadyAReplyKeepsItsPrefix() {
        listOf("Re: Lunch", "RE: Lunch", "re : Lunch").forEach {
            assertEquals(it, reply(original.copy(subject = it)).subject)
        }
    }

    @Test
    fun replyAllAddsEveryoneElseOnceAndLeavesOutTheAccount() {
        val draft = reply(original.copy(cc = original.cc + bob), all = true)
        assertEquals(listOf(ada, bob), draft.to)
        assertEquals(listOf(cy), draft.cc)
    }

    @Test
    fun answeringOwnMailGoesToItsRecipients() {
        val sent = original.copy(from = me, to = listOf(bob), cc = listOf(cy))
        assertEquals(listOf(bob), reply(sent).to)
        val all = reply(sent, all = true)
        assertEquals(listOf(bob), all.to)
        assertEquals(listOf(cy), all.cc)
    }

    @Test
    fun aFollowUpGoesToTheOriginalRecipientsFromTheAddressThatSentIt() {
        // Sent from an alias the account doesn't list, so a reply would go back to it.
        val alias = MailSender("Me", "alias@example.com")
        val sent = original.copy(from = alias, to = listOf(bob, me), cc = listOf(cy))
        val draft = ComposeRequest.FollowUp(sent).startDraft("a1", own)
        assertEquals(listOf(bob), draft.to)
        assertEquals(listOf(cy), draft.cc)
        assertEquals("Re: Lunch", draft.subject)
        assertEquals(listOf("<m1@example.com>"), draft.inReplyTo)
        assertEquals(alias, ComposeRequest.FollowUp(sent).preferredIdentity(listOf(me, alias)))
    }

    @Test
    fun aForwardCarriesTheOriginalUnderAHeader() {
        val draft = ComposeRequest.Forward(original).startDraft("a1", own)
        assertEquals("Fwd: Lunch", draft.subject)
        assertEquals(emptyList(), draft.to)
        assertEquals(emptyList(), draft.inReplyTo)
        val expected =
            listOf(
                "---------- Forwarded message ----------",
                "From: Ada Lovelace <ada@example.com>",
                "Date: 2026-10-11 12:30",
                "Subject: Lunch",
                "To: Me <me@example.com>, Bob <bob@example.com>",
                "Cc: Cy <cy@example.com>, ME@example.com",
                "",
                "Noon?\n\nAda",
            ).joinToString("\n")
        assertEquals(expected, draft.quote)
        assertEquals(
            "FW: Lunch",
            ComposeRequest.Forward(original.copy(subject = "FW: Lunch")).startDraft("a1", own).subject,
        )
    }

    @Test
    fun aQuoteOrForwardOfACutOffBodySaysTheRestIsMissing() {
        val received = ReceivedAt("2026-10-11", "12:30")
        val message =
            MailMessage(
                "a1/m1",
                ada,
                "Lunch",
                "Noon?",
                received,
                MailLocation("Inbox", "Work", "a1", mailboxRole = MailboxRole.Inbox),
            )
        val cut = message.copy(bodyTruncated = true).original()
        val note = "[The rest of the original message is not included.]"
        assertEquals("On 2026-10-11 12:30, Ada Lovelace wrote:\n> Noon?\n> $note", reply(cut).quote)
        assertTrue(
            ComposeRequest
                .Forward(cut)
                .startDraft("a1", own)
                .quote
                .endsWith("\n\nNoon?\n$note"),
        )
        assertFalse(note in reply(message.original()).quote, "A complete body gets no note")
    }

    @Test
    fun theAnswerComesFromTheIdentityTheOriginalWentTo() {
        val work = MailSender("Me", "work@example.com")
        val identities = listOf(work, me)
        assertEquals(me, ComposeRequest.Reply(original, all = false).preferredIdentity(identities))
        assertEquals(work, ComposeRequest.New().preferredIdentity(identities))
        assertEquals(null, ComposeRequest.New().preferredIdentity(emptyList()))
    }

    @Test
    fun aNewMessageComesFromItsAccountOrElseTheAddressUsedLast() {
        val home = MailSender("Me", "me@home.example", "a2")
        val work = MailSender("Me", "me@work.example", "a1")
        val identities = listOf(work, home)
        assertEquals(home, ComposeRequest.New("a2").preferredIdentity(identities, recent = listOf(work.address)))
        assertEquals(
            work,
            ComposeRequest.New().preferredIdentity(identities, recent = listOf("gone@x.example", work.address)),
        )
        assertEquals(work, ComposeRequest.New().preferredIdentity(identities))
    }

    @Test
    fun aReplyPrefersTheAddressItWentToInTheOriginalsAccount() {
        val elsewhere = MailSender("Me", "me@example.com", "a2")
        val here = MailSender("Me", "me@example.com", "a1")
        assertEquals(here, ComposeRequest.Reply(original, all = false).preferredIdentity(listOf(elsewhere, here)))
    }
}
