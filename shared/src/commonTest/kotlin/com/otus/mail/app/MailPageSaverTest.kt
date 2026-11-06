package com.otus.mail.app

import androidx.compose.runtime.saveable.SaverScope
import com.otus.mail.core.mailbox.MailboxRole
import com.otus.mail.core.messages.domain.ComposeRequest
import com.otus.mail.feature.contacts.domain.CardKey
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MailPageSaverTest {
    @Test
    fun everyPageComesBackAsItWasSaved() {
        assertTrue(pages.all(::sampled))
        pages.forEach { page ->
            val expected = if (page is MailPage.Compose) page.parent else page
            assertEquals(expected, MailPageSaver.restore(saved(page)), "$page")
        }
    }

    private fun saved(page: MailPage): List<String> = with(MailPageSaver) { SaverScope { true }.save(page) }.orEmpty()

    /** Fails to compile until a new kind of page has a sample in [pages]. */
    private fun sampled(page: MailPage): Boolean =
        when (page) {
            is MailPage.Fixed, is MailPage.Compose, is MailPage.SignatureEditor, is MailPage.FolderDetail,
            is MailPage.Folder, is MailPage.Combined, is MailPage.Shared, is MailPage.Contact,
            is MailPage.ContactEditor,
            -> pages.any { it::class == page::class }
        }

    private val pages =
        MailPage.fixed +
            listOf(
                MailPage.Compose(ComposeRequest.New(), parent = MailPage.Folder("acc", "f1")),
                MailPage.SignatureEditor("acc", "id-1"),
                MailPage.FolderDetail("acc", "f1"),
                MailPage.FolderDetail("acc", "f1", sharedIn = "shared"),
                MailPage.Folder("acc", "f1"),
                MailPage.Combined(MailboxRole.Junk),
                MailPage.Shared(listOf("work", "clients")),
                MailPage.Contact(CardKey("login", "acc", "card")),
                MailPage.ContactEditor(),
                MailPage.ContactEditor(CardKey("login", "acc", "card")),
            )
}
