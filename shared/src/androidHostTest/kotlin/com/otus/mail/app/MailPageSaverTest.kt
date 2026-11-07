package com.otus.mail.app

import androidx.compose.runtime.saveable.SaverScope
import com.otus.mail.core.mailbox.MailboxRole
import com.otus.mail.core.messages.domain.ComposeRequest
import com.otus.mail.feature.contacts.domain.CardKey
import kotlin.reflect.KClass
import kotlin.test.Test
import kotlin.test.assertEquals

class MailPageSaverTest {
    @Test
    fun everyPageComesBackAsItWasSaved() {
        pages.forEach { page ->
            val expected = if (page is MailPage.Compose) page.parent else page
            assertEquals(expected, MailPageSaver.restore(saved(page)), "$page")
        }
    }

    /** A page left out of [MailPage.fixed] saves fine but restores as the Inbox. */
    @Test
    fun theFixedListHoldsEveryPageWithoutArguments() {
        val objects = kinds(MailPage.Fixed::class).mapNotNull { it.objectInstance }.toSet()
        assertEquals(objects, MailPage.fixed.toSet())
    }

    @Test
    fun everyKindOfPageHasASample() {
        val missing = kinds(MailPage::class).filter { kind -> pages.none { it::class == kind } }
        assertEquals(emptyList(), missing.map { it.simpleName })
    }

    private fun saved(page: MailPage): List<String> = with(MailPageSaver) { SaverScope { true }.save(page) }.orEmpty()

    /** The classes that can be a [MailPage], through every sealed interface between. */
    private fun kinds(root: KClass<out MailPage>): Set<KClass<out MailPage>> =
        root.sealedSubclasses
            .flatMap { if (it.isSealed) kinds(it) else setOf(it) }
            .toSet()

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
