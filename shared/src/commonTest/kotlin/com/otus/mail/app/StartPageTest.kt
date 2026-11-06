package com.otus.mail.app

import com.otus.mail.core.mailbox.AccountFolders
import com.otus.mail.core.mailbox.MailFolder
import com.otus.mail.core.mailbox.MailboxRole
import com.otus.mail.feature.inbox.domain.SidebarPin
import kotlin.test.Test
import kotlin.test.assertEquals

class StartPageTest {
    private val ada =
        AccountFolders(
            "ada",
            "Ada",
            listOf(
                MailFolder("ada", "i", "Inbox", role = MailboxRole.Inbox),
                MailFolder("ada", "s", "Sent", role = MailboxRole.Sent),
                MailFolder("ada", "w", "Work"),
                MailFolder("ada", "c", "Clients", parentId = "w"),
            ),
        )

    @Test
    fun aLaunchOpensTheChosenPageBeforeTheFoldersLoad() {
        assertEquals(MailPage.Inbox, launchPage(SidebarPin.Inbox))
        assertEquals(MailPage.Contacts, launchPage(SidebarPin.Contacts))
        assertEquals(MailPage.ReadLater, launchPage(SidebarPin.ReadLater))
        assertEquals(MailPage.Combined(MailboxRole.Sent), launchPage(SidebarPin.Role(MailboxRole.Sent)))
        assertEquals(
            MailPage.Shared(listOf("work", "clients")),
            launchPage(SidebarPin.Folder(listOf("work", "clients"))),
        )
    }

    @Test
    fun aRoleMailDoesNotCombineOpensTheInbox() {
        assertEquals(MailPage.Inbox, launchPage(SidebarPin.Role(MailboxRole.Inbox)))
    }

    @Test
    fun aMailboxSomeAccountHasStaysTheStartPage() {
        val accounts = listOf(ada)
        assertEquals(
            MailPage.Shared(listOf("work", "clients")),
            startPage(SidebarPin.Folder(listOf("work", "clients")), accounts),
        )
        assertEquals(MailPage.Combined(MailboxRole.Sent), startPage(SidebarPin.Role(MailboxRole.Sent), accounts))
        assertEquals(MailPage.Conversations, startPage(SidebarPin.Conversations, accounts))
        assertEquals(MailPage.Contacts, startPage(SidebarPin.Contacts, accounts))
    }

    @Test
    fun aMailboxNoAccountHasFallsBackToTheInbox() {
        val accounts = listOf(ada)
        assertEquals(MailPage.Inbox, startPage(SidebarPin.Folder(listOf("renamed")), accounts))
        assertEquals(MailPage.Inbox, startPage(SidebarPin.Role(MailboxRole.Archive), accounts))
        // Read later is off, so no account has its folder.
        assertEquals(MailPage.Inbox, startPage(SidebarPin.ReadLater, accounts))
    }

    @Test
    fun withoutAccountsOnlyContactsCanStillOpen() {
        assertEquals(MailPage.Inbox, startPage(SidebarPin.Folder(listOf("work")), emptyList()))
        assertEquals(MailPage.Inbox, startPage(SidebarPin.Conversations, emptyList()))
        assertEquals(MailPage.Contacts, startPage(SidebarPin.Contacts, emptyList()))
    }
}
