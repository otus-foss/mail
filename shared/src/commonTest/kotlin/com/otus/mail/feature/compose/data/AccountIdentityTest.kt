package com.otus.mail.feature.compose.data

import com.otus.mail.core.mail.EmailObject
import com.otus.mail.core.mailbox.MailboxRole
import com.otus.mail.core.messages.data.receivedAt
import com.otus.mail.core.messages.data.toMessage
import com.otus.mail.core.messages.domain.MailLocation
import com.otus.mail.core.messages.domain.MailboxScope
import com.otus.mail.feature.inbox.domain.InboxSearch
import com.otus.mail.feature.inbox.domain.searchMessages
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals

class AccountIdentityTest {
    private val email = EmailObject(id = "same-email-id", receivedAt = "2026-10-01T12:00:00Z")
    private val messages =
        listOf(
            email.toMessage(
                MailLocation("Inbox", "Ada", "work", "ada@work.example", mailboxRole = MailboxRole.Inbox),
                TimeZone.UTC,
            ),
            email.toMessage(
                MailLocation("Inbox", "Ada", "personal", "ada@home.example", mailboxRole = MailboxRole.Inbox),
                TimeZone.UTC,
            ),
        )

    @Test
    fun sameNamedAccountsRemainIndependentInAccountAndMailboxFilters() {
        assertEquals(
            listOf("work/same-email-id"),
            searchMessages(messages, InboxSearch(accounts = setOf("work"))).map {
                it.id
            },
        )
        val mailbox = InboxSearch(mailboxes = setOf(MailboxScope("personal", "Inbox", MailboxRole.Inbox)))
        assertEquals(listOf("personal/same-email-id"), searchMessages(messages, mailbox).map { it.id })
        assertEquals(2, searchMessages(messages, InboxSearch()).size)
        assertEquals(listOf("Ada", "Ada"), messages.map { it.location.account })
    }

    @Test
    fun renamingAnAccountDoesNotChangeSavedFilterIdentity() {
        val renamed =
            email.toMessage(
                MailLocation("Inbox", "New name", "work", mailboxRole = MailboxRole.Inbox),
                TimeZone.UTC,
            )
        assertEquals(listOf(renamed), searchMessages(listOf(renamed), InboxSearch(accounts = setOf("work"))))
    }
}
