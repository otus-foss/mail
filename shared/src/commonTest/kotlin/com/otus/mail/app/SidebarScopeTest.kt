package com.otus.mail.app

import com.otus.mail.feature.folders.domain.AccountFolders
import com.otus.mail.feature.folders.domain.MailFolder
import com.otus.mail.feature.folders.domain.sharedFolders
import com.otus.mail.feature.inbox.domain.MailScopeChoice
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
