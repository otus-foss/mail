package com.otus.mail.feature.inbox.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class InboxContentsTest {
    private val hi = MailScopeChoice.Address("design login", "hi@design.ch")
    private val test = MailScopeChoice.Address("design login", "test@design.ch")
    private val company = MailScopeChoice.Address("company login", "hi@company.com")
    private val all = listOf(hi, test, company)

    private fun message(
        accountId: String,
        to: String,
    ) = MailMessage(
        "$accountId $to",
        MailSender("Grace", "grace@example.org"),
        "Hello",
        "",
        ReceivedAt("2026-10-01", "09:00"),
        MailLocation(INBOX_MAILBOX, accountId),
        to = listOf(MailSender("", to)),
    )

    @Test
    fun theDefaultListsEveryAddressFromTheInboxAlone() {
        val contents = InboxContents()
        assertTrue(contents.everyAddress)
        assertEquals(listOf("inbox"), contents.roles)
        assertTrue(contents.shows(message("company login", "hi@company.com")))
    }

    @Test
    fun spamAndTrashJoinTheInboxRoles() {
        assertEquals(listOf("inbox", "junk", "trash"), InboxContents(spam = true, trash = true).roles)
        assertEquals(listOf("inbox", "trash"), InboxContents(trash = true).roles)
    }

    @Test
    fun chosenAddressesShowOnlyTheirOwnAccountsMail() {
        val contents = InboxContents(addresses = listOf(hi))
        assertTrue(contents.shows(message("design login", "HI@design.ch")))
        assertFalse(contents.shows(message("design login", "test@design.ch")))
        assertFalse(contents.shows(message("company login", "hi@design.ch")))
    }

    @Test
    fun leavingOutOneOfEveryAddressKeepsTheOthers() {
        val contents = InboxContents().with(company, on = false, all)
        assertEquals(listOf(hi, test), contents.addresses)
        assertFalse(contents.has(company))
        assertTrue(contents.has(hi))
    }

    @Test
    fun choosingEveryAddressOrNoneShowsEveryAddressAgain() {
        assertTrue(InboxContents(addresses = listOf(hi, test)).with(company, on = true, all).everyAddress)
        assertTrue(InboxContents(addresses = listOf(hi)).with(hi, on = false, all).everyAddress)
    }

    @Test
    fun aRemovedAccountsAddressesDropOut() {
        val contents = InboxContents(addresses = listOf(hi, company)).within(listOf("design login"))
        assertEquals(listOf(hi), contents.addresses)
        assertTrue(InboxContents(addresses = listOf(company)).within(listOf("design login")).everyAddress)
    }

    @Test
    fun savedContentsReadBackAndUnknownValuesReadAsDefaults() {
        val contents = InboxContents(addresses = listOf(hi, company), spam = true)
        assertEquals(contents, InboxContents.decode(InboxContents.encode(contents)))
        assertEquals(InboxContents(), InboxContents.decode("spam=maybe\naddress=broken\nnewer=1"))
        assertEquals(InboxContents(), InboxContents.decode(""))
    }
}
