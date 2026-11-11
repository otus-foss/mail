package com.otusfoss.mail.app

import com.otusfoss.mail.core.mailbox.AccountFolders
import com.otusfoss.mail.core.mailbox.MailFolder
import com.otusfoss.mail.core.mailbox.sharedFolders
import com.otusfoss.mail.core.messages.domain.MailScopeChoice
import com.otusfoss.mail.core.messages.domain.inScope
import kotlin.test.Test
import kotlin.test.assertEquals

class SidebarScopeTest {
    private val work = AccountFolders("a1", "Work", listOf(MailFolder("a1", "f1", "Clients")))
    private val home = AccountFolders("a2", "Home", listOf(MailFolder("a2", "f2", "Recipes")))

    @Test
    fun allMailListsEveryAccountsFolders() {
        val names = sharedFolders(listOf(work, home).inScope(MailScopeChoice.All)).map { it.name }
        assertEquals(listOf("Clients", "Recipes"), names)
    }

    @Test
    fun anAccountOrAddressListsOnlyItsAccountsFolders() {
        assertEquals(listOf(home), listOf(work, home).inScope(MailScopeChoice.Account("a2")))
        assertEquals(listOf(work), listOf(work, home).inScope(MailScopeChoice.Address("a1", "me@work.example")))
    }
}
