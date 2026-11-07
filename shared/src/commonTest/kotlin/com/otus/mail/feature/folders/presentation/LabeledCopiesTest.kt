package com.otus.mail.feature.folders.presentation

import com.otus.jmap.JmapClient
import com.otus.mail.app.MailServices
import com.otus.mail.core.mailbox.AccountFolders
import com.otus.mail.core.mailbox.MailFolder
import com.otus.mail.core.mailbox.MailboxRole
import com.otus.mail.core.messages.domain.MailLocation
import com.otus.mail.core.messages.domain.MailMessage
import com.otus.mail.core.messages.domain.MailSender
import com.otus.mail.core.messages.domain.ReceivedAt
import com.otus.mail.testing.testEdits
import com.otus.mail.testing.testFolders
import io.ktor.client.engine.mock.MockEngine
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotSame
import kotlin.test.assertSame

/** Labeling a list again, as after an avatar arrives, keeps the copies of messages that did not change. */
@OptIn(ExperimentalCoroutinesApi::class)
class LabeledCopiesTest {
    private val account =
        AccountFolders(
            "ada",
            "Ada",
            listOf(MailFolder("ada", "i", "Inbox", role = MailboxRole.Inbox), MailFolder("ada", "c", "Clients")),
        )

    @Test
    fun anUnchangedMessageKeepsItsLabeledCopy() =
        runTest {
            val services = MailServices(createClient = { JmapClient(MockEngine { error("No server here") }) })
            val folders = testFolders(services) { listOf(account) }
            folders.refresh()
            runCurrent()
            val filing = MessageFiling(services.senderHistory, folders, FolderColorsState(), testEdits(services))
            val copies = LabeledCopies()
            val one = message("one")
            val two = message("two")
            val first = filing.labeled(listOf(one, two), copies)
            assertEquals(listOf("Clients"), first.first().labels.map { it.name })

            val second = filing.labeled(listOf(one, two.copy(subject = "Changed")), copies)
            assertSame(first[0], second[0])
            assertNotSame(first[1], second[1])
            assertEquals("Changed", second[1].subject)
            assertEquals(listOf("Clients"), second[1].labels.map { it.name })
        }

    private fun message(id: String) =
        MailMessage(
            id,
            MailSender("Grace", "grace@example.org"),
            "Plans",
            "",
            ReceivedAt("2026-10-01", "09:00"),
            MailLocation(
                "Inbox",
                "Ada",
                "ada",
                mailboxId = "i",
                mailboxIds = setOf("i", "c"),
                mailboxRole = MailboxRole.Inbox,
            ),
            emailId = id,
        )
}
