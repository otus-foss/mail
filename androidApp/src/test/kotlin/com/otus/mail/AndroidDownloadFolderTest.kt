package com.otus.mail

import com.otus.mail.core.files.DownloadFolderException
import com.otus.mail.core.files.StoredKey
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class AndroidDownloadFolderTest {
    @get:Rule
    val temporary = TemporaryFolder()

    private val key = StoredKey("ada", "report")

    private fun folder() = AndroidDownloadFolder(File(temporary.root, "attachments"))

    @Test
    fun aKeptFileIsFoundAgainByTheNextProcess() =
        runBlocking {
            val pieces = listOf(byteArrayOf(1, 2), byteArrayOf(3))
            val written = folder().write(key, "Q3 report.pdf") { append -> pieces.forEach { append(it) } }
            assertTrue(folder().setKept(key, kept = true))

            val found = folder().find(key)!!
            assertEquals(written.path, found.path)
            assertTrue(found.path.endsWith("/ada/report/Q3 report.pdf"))
            assertEquals(3, found.bytes)
            assertTrue(found.kept)
            assertArrayEquals(byteArrayOf(1, 2, 3), folder().read(key))
        }

    @Test
    fun aFailedWriteLeavesThePreviousFileAndNoPartOfItself() =
        runBlocking {
            val folder = folder()
            folder.write(key, "report.pdf") { append -> append(byteArrayOf(1)) }
            folder.setKept(key, kept = true)

            assertThrows(IllegalStateException::class.java) {
                runBlocking {
                    folder.write(key, "report.pdf") { append ->
                        append(byteArrayOf(9, 9))
                        error("connection lost")
                    }
                }
            }

            val left = File(temporary.root, "attachments/ada/report").list()!!.toSet()
            assertEquals(setOf("report.pdf", ".kept"), left)
            assertArrayEquals(byteArrayOf(1), folder.read(key))
        }

    @Test
    fun aSendersNameCannotLeaveItsFolderOrHideAsAMark() =
        runBlocking {
            val stored = folder().write(key, "../../.kept") { append -> append(byteArrayOf(1)) }

            assertTrue(stored.path.endsWith("/ada/report/_.._.kept"))
            assertFalse(stored.kept)
        }

    @Test
    fun cleaningDropsPartialFilesAndForgettingAGroupDropsItsFiles() =
        runBlocking {
            val folder = folder()
            folder.write(key, "report.pdf") { append -> append(byteArrayOf(1)) }
            folder.write(StoredKey("bob", "photo"), "photo.jpg") { append -> append(byteArrayOf(2)) }
            val stale = File(temporary.root, "attachments/ada/notes").apply { mkdirs() }
            File(stale, ".partial").writeBytes(byteArrayOf(3))

            folder.dropUnfinished()
            folder.deleteGroup("bob")

            assertFalse(stale.exists())
            assertEquals(listOf(key), folder.entries().map { it.key })
            assertNull(folder.find(StoredKey("bob", "photo")))
        }

    @Test
    fun anUnwritableFolderIsReportedAsAStorageFailure() {
        val blocked = File(temporary.root, "attachments").apply { writeBytes(byteArrayOf()) }
        assertThrows(DownloadFolderException::class.java) {
            runBlocking { AndroidDownloadFolder(blocked).write(key, "report.pdf") { append -> append(byteArrayOf(1)) } }
        }
    }
}
