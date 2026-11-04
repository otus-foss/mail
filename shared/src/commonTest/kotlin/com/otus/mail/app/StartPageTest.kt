package com.otus.mail.app

import com.otus.mail.core.mail.INBOX_ROLE
import com.otus.mail.core.mail.SENT_ROLE
import com.otus.mail.feature.folders.domain.AccountFolders
import com.otus.mail.feature.folders.domain.CombinedFolder
import com.otus.mail.feature.folders.domain.MailFolder
import com.otus.mail.feature.inbox.domain.SidebarPin
import kotlin.test.Test
import kotlin.test.assertEquals

class StartPageTest {
    private val ada =
        AccountFolders(
            "ada",
            "Ada",
            listOf(
                MailFolder("ada", "i", "Inbox", role = INBOX_ROLE),
                MailFolder("ada", "s", "Sent", role = SENT_ROLE),
                MailFolder("ada", "w", "Work"),
                MailFolder("ada", "c", "Clients", parentId = "w"),
            ),
        )

    @Test
    fun aLaunchOpensTheChosenPageBeforeTheFoldersLoad() {
        assertEquals(MailPage.Inbox, launchPage(SidebarPin.Inbox))
        assertEquals(MailPage.Contacts, launchPage(SidebarPin.Contacts))
        assertEquals(MailPage.ReadLater, launchPage(SidebarPin.ReadLater))
        assertEquals(MailPage.Combined(CombinedFolder.Sent), launchPage(SidebarPin.Role(SENT_ROLE)))
        assertEquals(
            MailPage.Shared(listOf("work", "clients")),
            launchPage(SidebarPin.Folder(listOf("work", "clients"))),
        )
    }

    @Test
    fun aRoleMailDoesNotCombineOpensTheInbox() {
        assertEquals(MailPage.Inbox, launchPage(SidebarPin.Role("important")))
    }

    @Test
    fun aMailboxSomeAccountHasStaysTheStartPage() {
        val accounts = listOf(ada)
        assertEquals(
            MailPage.Shared(listOf("work", "clients")),
            startPage(SidebarPin.Folder(listOf("work", "clients")), accounts),
        )
        assertEquals(MailPage.Combined(CombinedFolder.Sent), startPage(SidebarPin.Role(SENT_ROLE), accounts))
        assertEquals(MailPage.Conversations, startPage(SidebarPin.Conversations, accounts))
        assertEquals(MailPage.Contacts, startPage(SidebarPin.Contacts, accounts))
    }

    @Test
    fun aMailboxNoAccountHasFallsBackToTheInbox() {
        val accounts = listOf(ada)
        assertEquals(MailPage.Inbox, startPage(SidebarPin.Folder(listOf("renamed")), accounts))
        assertEquals(MailPage.Inbox, startPage(SidebarPin.Role("archive"), accounts))
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
