package com.otusfoss.mail.feature.read.data

import com.otusfoss.mail.core.files.InMemoryDownloadFolder
import com.otusfoss.mail.core.mailbox.MailboxRole
import com.otusfoss.mail.core.messages.domain.MailAttachment
import com.otusfoss.mail.core.messages.domain.MailLocation
import com.otusfoss.mail.core.messages.domain.MailMessage
import com.otusfoss.mail.core.messages.domain.MailSender
import com.otusfoss.mail.core.messages.domain.ReceivedAt
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class AttachmentCacheTest {
    private var now = 1_000L
    private val folder = InMemoryDownloadFolder { now }
    private val server = FakeServer()

    private fun retention(budgetBytes: Long = 1_000) = AttachmentRetention(folder, budgetBytes)

    private fun cache(budgetBytes: Long = 1_000) = AttachmentCache(server, folder, retention(budgetBytes))

    @Test
    fun aFileStreamsToTheDeviceOnceWithProgress() =
        runTest {
            val cache = cache()
            val progress = mutableListOf<Long>()

            val stored = assertNotNull(cache.fetch(ada, report) { progress += it })
            cache.fetch(ada, report)

            assertEquals(listOf(4L, 8L, 10L), progress)
            assertEquals(10, stored.size)
            assertEquals("report.pdf", stored.name)
            assertEquals(1, server.streams)
            assertContentEquals(server.bytes(report), cache.bytes(ada, report))
        }

    @Test
    fun aFailedOrOversizedDownloadLeavesNothingAndIsTriedAgain() =
        runTest {
            val cache = cache()
            server.failing = true
            assertNull(cache.fetch(ada, report))
            server.failing = false
            server.extra = 1
            assertNull(cache.fetch(ada, report))
            assertTrue(folder.entries().isEmpty())

            server.extra = 0
            assertNotNull(cache.fetch(ada, report))
            assertEquals(3, server.streams)
        }

    @Test
    fun cancellingADownloadLeavesNothing() =
        runTest {
            val cache = cache()
            server.stall = true
            val download = launch { cache.fetch(ada, report) }
            runCurrent()
            download.cancel()
            download.join()

            assertTrue(folder.entries().isEmpty())
        }

    @Test
    fun twoCallersOfOneFileDownloadItOnce() =
        runTest {
            val cache = cache()
            server.gate = CompletableDeferred()
            val thumbnail = async { cache.bytes(ada, photo) }
            val opened = async { cache.fetch(ada, photo) }
            runCurrent()
            server.gate?.complete(Unit)

            assertNotNull(thumbnail.await())
            assertNotNull(opened.await())
            assertEquals(1, server.streams)
        }

    @Test
    fun keptFilesStayWhileTheLeastRecentlyUsedGoOverTheBudget() =
        runTest {
            val cache = cache(budgetBytes = 15)
            assertTrue(assertNotNull(cache.keep(ada, report)).kept)
            listOf(photo, notes, slides).forEach { file ->
                now += 1
                cache.fetch(ada, file)
            }

            // Photo, 6 bytes, went once Notes and Slides came to 7 + 8 = 15 bytes.
            assertTrue(cache.isKept(ada, report))
            assertEquals(setOf(10L, 7L, 8L), folder.entries().map { it.bytes }.toSet())
        }

    @Test
    fun trimmingDropsUnkeptFilesUnusedForAWhile() =
        runTest {
            val cache = cache()
            cache.keep(ada, report)
            cache.fetch(ada, photo)
            now = 5_000
            cache.fetch(ada, notes)

            assertTrue(retention().trim(unusedBeforeMillis = 2_000))

            assertEquals(setOf(10L, 7L), folder.entries().map { it.bytes }.toSet())
            assertTrue(cache.isKept(ada, report))
        }

    @Test
    fun removingAKeptFileDeletesItAndAnotherLoginKeepsItsOwn() =
        runTest {
            val cache = cache()
            cache.keep(ada, report)
            cache.keep(bob, report)

            assertTrue(cache.remove(ada, report))
            assertFalse(cache.isKept(ada, report))
            retention().forget("bob")

            assertTrue(folder.entries().isEmpty())
            assertEquals(2, server.streams)
        }

    /** Sends each file's bytes in pieces of four; see each switch. */
    private class FakeServer : AttachmentStream {
        var streams = 0
        var failing = false
        var stall = false
        var extra = 0
        var gate: CompletableDeferred<Unit>? = null

        fun bytes(file: MailAttachment) = ByteArray(file.size.toInt()) { it.toByte() }

        override suspend fun stream(
            message: MailMessage,
            file: MailAttachment,
            receive: suspend (ByteArray) -> Unit,
        ): Boolean {
            streams += 1
            gate?.await()
            if (failing) return false
            val bytes = bytes(file) + ByteArray(extra)
            bytes.toList().chunked(PIECE).forEach { piece ->
                receive(piece.toByteArray())
                if (stall) awaitCancellation()
            }
            return true
        }
    }

    private companion object {
        const val PIECE = 4
        val report = MailAttachment("report.pdf", "application/pdf", 10, "b-report")
        val photo = MailAttachment("photo.jpg", "image/jpeg", 6, "b-photo")
        val notes = MailAttachment("notes.txt", "text/plain", 7, "b-notes")
        val slides = MailAttachment("slides.key", "application/octet-stream", 8, "b-slides")
        val ada = message("ada")
        val bob = message("bob")

        fun message(login: String) =
            MailMessage(
                "$login/m1",
                MailSender("Grace", "grace@example.org"),
                "Files",
                "",
                ReceivedAt("2026-11-04", "09:00"),
                MailLocation("Inbox", login, mailboxRole = MailboxRole.Inbox),
            )
    }
}
