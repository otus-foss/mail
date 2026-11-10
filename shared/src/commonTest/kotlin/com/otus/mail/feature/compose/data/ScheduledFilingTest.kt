package com.otus.mail.feature.compose.data

import com.otus.mail.core.mailbox.MailFolder
import com.otus.mail.core.mailbox.MailboxRole
import com.otus.mail.testing.FakeSubmission
import com.otus.mail.testing.FakeSubmissionServer
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

class ScheduledFilingTest {
    @Test
    fun mailAnotherClientScheduledMovesFromSentToScheduledButHeldSendsStay() =
        runTest {
            val server =
                FakeSubmissionServer(
                    copies =
                        mapOf(
                            "web" to setOf("se"),
                            "held" to setOf("se"),
                            "edge" to setOf("se"),
                            "past" to setOf("se"),
                        ),
                    submissions =
                        listOf(
                            pending("web", NOW + 2.days),
                            pending("held", NOW + 32.seconds),
                            pending("edge", NOW + 62.seconds),
                            pending("past", NOW + 63.seconds),
                        ),
                )
            server.file(scheduled = "sc")
            assertEquals(mapOf("web" to moved("se", "sc"), "past" to moved("se", "sc")), server.updates.toMap())
        }

    @Test
    fun aScheduledCopyMakesTheScheduledFolderWhenThereIsNone() =
        runTest {
            val server = FakeSubmissionServer(mapOf("web" to setOf("dr")), listOf(pending("web", NOW + 2.days)))
            server.file(scheduled = null)
            assertEquals(mapOf("web" to moved("dr", "made")), server.updates.toMap())
        }

    @Test
    fun nothingScheduledMakesNoFolderAndMovesNothing() =
        runTest {
            val server = FakeSubmissionServer(mapOf("held" to setOf("se")), listOf(pending("held", NOW + 20.seconds)))
            server.file(scheduled = null)
            assertTrue(server.folderChanges.isEmpty() && server.updates.isEmpty())
        }

    @Test
    fun anEmptyFolderNamedScheduledGetsTheRoleInsteadOfASecondFolder() =
        runTest {
            val server = FakeSubmissionServer(mapOf("web" to setOf("se")), listOf(pending("web", NOW + 2.days)))
            val own = MailFolder("ada", "own", "Scheduled")
            ScheduledFiling().file(server.account(), FOLDERS + own, NOW)
            assertEquals(
                Json.parseToJsonElement("""{"own": {"role": "scheduled"}}"""),
                server.folderChanges.single()["update"],
            )
            assertEquals(mapOf("web" to moved("se", "own")), server.updates.toMap())
        }

    @Test
    fun aFolderTheServerRefusedIsNotAskedForAgain() =
        runTest {
            val server =
                FakeSubmissionServer(
                    mapOf("web" to setOf("se")),
                    listOf(pending("web", NOW + 2.days)),
                    refuseFolders = true,
                )
            val filing = ScheduledFiling()
            filing.file(server.account(), FOLDERS, NOW)
            filing.file(server.account(), FOLDERS, NOW)
            assertEquals(1, server.folderChanges.size)
            assertTrue(server.updates.isEmpty(), "The copy stays in Sent")
        }

    @Test
    fun aCopyLeavesScheduledOnceItsSendIsOver() =
        runTest {
            val copies = listOf("sent", "canceled", "gone", "waits", "again", "stored").associateWith { setOf("sc") }
            val server =
                FakeSubmissionServer(
                    copies,
                    listOf(
                        FakeSubmission("sent", "final", NOW),
                        FakeSubmission("canceled", "canceled", NOW + 2.days),
                        pending("waits", NOW + 10.seconds),
                        FakeSubmission("again", "canceled", NOW),
                        pending("again", NOW + 10.seconds),
                    ),
                    drafts = setOf("stored"),
                )
            server.file(scheduled = "sc")
            val expected =
                mapOf(
                    "sent" to moved("sc", "se"),
                    "canceled" to
                        Json.parseToJsonElement(
                            """{"mailboxIds/sc": null, "mailboxIds/dr": true, "keywords/${'$'}draft": true}""",
                        ),
                    "gone" to moved("sc", "se"),
                )
            assertEquals(expected, server.updates.toMap(), "A pending send and a draft Mail has not submitted stay")
        }

    @Test
    fun aCutShortAnswerTakesNoCopyForGone() =
        runTest {
            val copies = mapOf("sent" to setOf("sc"), "gone" to setOf("sc"))
            val server = FakeSubmissionServer(copies, listOf(FakeSubmission("sent", "final", NOW)), submissionLimit = 1)
            server.file(scheduled = "sc")
            assertTrue(server.updates.isEmpty())
        }

    @Test
    fun aCopyAnOlderMailLeftInDraftsGoesToSentOrBecomesADraft() =
        runTest {
            val copies = listOf("sent", "canceled", "gone").associateWith { setOf("dr") }
            val submissions = listOf(FakeSubmission("sent", "final", NOW), FakeSubmission("canceled", "canceled", NOW))
            val server = FakeSubmissionServer(copies, submissions)
            server.file(scheduled = "sc")
            val draft = Json.parseToJsonElement("""{"keywords/${'$'}draft": true}""")
            assertEquals(mapOf("sent" to moved("dr", "se"), "canceled" to draft), server.updates.toMap())
        }

    private suspend fun FakeSubmissionServer.file(scheduled: String?) =
        account().fileScheduled(drafts = "dr", scheduled = scheduled, sent = "se", now = NOW) { "made" }

    private fun pending(
        emailId: String,
        at: Instant,
    ) = FakeSubmission(emailId, "pending", at)

    private fun moved(
        from: String,
        to: String,
    ): JsonElement = Json.parseToJsonElement("""{"mailboxIds/$from": null, "mailboxIds/$to": true}""")

    private companion object {
        val NOW = Instant.parse("2026-11-10T12:00:00Z")
        val FOLDERS =
            listOf(
                MailFolder("ada", "dr", "Drafts", role = MailboxRole.Drafts),
                MailFolder("ada", "se", "Sent", role = MailboxRole.Sent),
            )
    }
}
