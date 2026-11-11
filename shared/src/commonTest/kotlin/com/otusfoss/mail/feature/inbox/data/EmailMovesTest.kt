package com.otusfoss.mail.feature.inbox.data

import com.otusfoss.core.jmap.JmapClient
import com.otusfoss.core.jmap.JmapConnections
import com.otusfoss.core.jmap.login.InMemoryLoginStore
import com.otusfoss.mail.core.mailbox.MailboxRole
import com.otusfoss.mail.core.messages.data.JmapEmailWriter
import com.otusfoss.mail.core.messages.domain.MailLocation
import com.otusfoss.mail.core.messages.domain.MailMessage
import com.otusfoss.mail.core.messages.domain.MailSender
import com.otusfoss.mail.core.messages.domain.ReceivedAt
import com.otusfoss.mail.core.messages.domain.ThreadMember
import com.otusfoss.mail.testing.ADA_LOGIN
import com.otusfoss.mail.testing.CONTACTS_ONLY_SESSION
import com.otusfoss.mail.testing.STALWART_SESSION
import com.otusfoss.mail.testing.conversationOf
import com.otusfoss.mail.testing.emailWriter
import com.otusfoss.mail.testing.stalwartLike
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class EmailMovesTest {
    private val login = ADA_LOGIN
    private val message =
        MailMessage(
            "ada/m1",
            MailSender("Grace", "grace@example.org"),
            "Order",
            "",
            ReceivedAt("2026-10-04", "09:30"),
            MailLocation("Inbox", "Ada", "ada", mailboxId = "a", mailboxRole = MailboxRole.Inbox),
            emailId = "m1",
        )

    private fun mover(
        session: String = STALWART_SESSION,
        reply: (String) -> String,
    ): JmapEmailWriter {
        val store = InMemoryLoginStore().apply { save(login, "secret") }
        return emailWriter(JmapConnections(JmapClient(stalwartLike(session, reply)), store))
    }

    @Test
    fun theMessageLeavesItsMailboxAndJoinsTheFolder() =
        runTest {
            val sent = mutableListOf<String>()
            val moved =
                mover {
                    sent += it
                    """{"methodResponses": [["Email/set", {"newState": "e2", "updated": {"m1": null}}, "c0"]]}"""
                }.move(message, "r", leaving = setOf("a"))
            assertTrue(moved)
            assertTrue(
                "\"update\":{\"m1\":{\"mailboxIds/a\":null,\"mailboxIds/r\":true}}" in sent.single(),
                sent.single(),
            )
        }

    @Test
    fun aMoveCanLeaveSeveralFoldersAndOnlyNamedOnesChange() =
        runTest {
            val sent = mutableListOf<String>()
            val updated = """{"methodResponses": [["Email/set", {"newState": "e2", "updated": {"m1": null}}, "c0"]]}"""
            val mover =
                mover {
                    sent += it
                    updated
                }
            assertTrue(mover.move(message, "t", leaving = setOf("a", "l")))
            assertTrue(mover.setInFolder(message, "l", inFolder = true))
            assertTrue(mover.setInFolder(message, "l", inFolder = false))
            assertTrue(
                "\"update\":{\"m1\":{\"mailboxIds/a\":null,\"mailboxIds/l\":null,\"mailboxIds/t\":true}}" in sent[0],
                sent[0],
            )
            assertTrue("\"update\":{\"m1\":{\"mailboxIds/l\":true}}" in sent[1], sent[1])
            assertTrue("\"update\":{\"m1\":{\"mailboxIds/l\":null}}" in sent[2], sent[2])
        }

    @Test
    fun movingBackReturnsToEveryFolderTheMoveLeft() =
        runTest {
            val sent = mutableListOf<String>()
            val mover =
                mover {
                    sent += it
                    """{"methodResponses": [["Email/set", {"newState": "e2", "updated": {"m1": null}}, "c0"]]}"""
                }
            val inTwo = message.copy(location = message.location.copy(mailboxIds = setOf("a", "l")))
            assertTrue(mover.moveBack(inTwo, "t", returning = setOf("a", "l")))
            val alreadyInTarget = message.copy(location = message.location.copy(mailboxIds = setOf("a", "t")))
            assertTrue(mover.moveBack(alreadyInTarget, "t", returning = setOf("a")))
            assertTrue(
                "\"update\":{\"m1\":{\"mailboxIds/t\":null,\"mailboxIds/a\":true,\"mailboxIds/l\":true}}" in sent[0],
                sent[0],
            )
            assertTrue("\"update\":{\"m1\":{\"mailboxIds/a\":true}}" in sent[1], sent[1])
        }

    @Test
    fun movingAConversationBackReturnsEachEmailToItsOwnFolders() =
        runTest {
            val sent = mutableListOf<String>()
            val mover =
                mover {
                    sent += it
                    """{"methodResponses": [["Email/set", {"newState": "e2",
                       "updated": {"m1": null, "m2": null}}, "c0"]]}"""
                }
            val members = listOf(ThreadMember("m1", setOf("a", "l"), "a"), ThreadMember("m2", setOf("a"), "a"))
            val row = message.copy(conversation = conversationOf(members))
            assertTrue(mover.moveBack(row, "t", returning = setOf("a", "l")))
            val update =
                "\"update\":{\"m1\":{\"mailboxIds/t\":null,\"mailboxIds/a\":true,\"mailboxIds/l\":true}," +
                    "\"m2\":{\"mailboxIds/t\":null,\"mailboxIds/a\":true}}"
            assertTrue(update in sent.single(), sent.single())
        }

    @Test
    fun refilingNamesOnlyTheFoldersThatChange() =
        runTest {
            val sent = mutableListOf<String>()
            val mover =
                mover {
                    sent += it
                    """{"methodResponses": [["Email/set", {"newState": "e2", "updated": {"m1": null}}, "c0"]]}"""
                }
            val before = mapOf("m1" to setOf("a", "l"), "m2" to setOf("r"))
            val after = mapOf("m1" to setOf("a", "r"), "m2" to setOf("r"))
            assertEquals(setOf("m1", "m2"), mover.setMailboxes("ada", before, after), "m2 needs no change")
            val update = "\"update\":{\"m1\":{\"mailboxIds/l\":null,\"mailboxIds/r\":true}}"
            assertTrue(update in sent.single(), sent.single())
        }

    @Test
    fun folderIdsAreEscapedInPatchPaths() =
        runTest {
            val sent = mutableListOf<String>()
            val moved =
                mover {
                    sent += it
                    """{"methodResponses": [["Email/set", {"newState": "e2", "updated": {"m1": null}}, "c0"]]}"""
                }.move(message, "r/1", leaving = setOf("a~b"))
            val patch = "\"update\":{\"m1\":{\"mailboxIds/a~0b\":null,\"mailboxIds/r~11\":true}}"
            assertTrue(moved)
            assertTrue(patch in sent.single(), sent.single())
        }

    @Test
    fun refusalsAndPointlessMovesReportFailure() =
        runTest {
            val refused =
                mover {
                    """{"methodResponses": [["Email/set", {"newState": "e2",
                       "notUpdated": {"m1": {"type": "forbidden"}}}, "c0"]]}"""
                }
            assertFalse(refused.move(message, "r", setOf("a")))
            val idle = mover { error("Nothing is sent") }
            assertFalse(idle.move(message, "a", setOf("a")), "Moving into the same mailbox does nothing")
            assertFalse(idle.move(message.copy(emailId = ""), "r", setOf("a")), "Supplied messages have no server id")
            val gone = message.copy(location = message.location.copy(accountId = "gone"))
            assertEquals(false, idle.move(gone, "r", setOf("a")))
        }

    @Test
    fun aPartlyMovedConversationReportsFailure() =
        runTest {
            val members = listOf("m1", "m2").map { ThreadMember(it, setOf("a"), "a") }
            val row = message.copy(conversation = conversationOf(members))
            val partial =
                mover {
                    """{"methodResponses": [["Email/set", {"newState": "e2",
                "updated": {"m1": null}, "notUpdated": {"m2": {"type": "forbidden"}}}, "c0"]]}"""
                }
            assertFalse(partial.move(row, "r", setOf("a")))
            assertFalse(partial.setInFolder(row, "r", true))
            assertFalse(partial.moveBack(row, "r", setOf("a")))
        }

    @Test
    fun aLoginWithoutMailMovesNothing() =
        runTest {
            val contactsOnly = mover(CONTACTS_ONLY_SESSION) { error("Nothing is sent") }
            assertFalse(contactsOnly.move(message, "r", setOf("a")))
            assertFalse(contactsOnly.setInFolder(message, "r", inFolder = true))
        }
}
