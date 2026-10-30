package com.otus.mail.feature.inbox.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AuthenticationResultsTest {
    private val server = "mx.example.net; dkim=pass header.d=shop.com; spf=pass smtp.mailfrom=shop.com"
    private val host = "mail.example.net"

    private fun passOf(vararg fields: String) = dmarcPassDomain(fields.toList(), "news@shop.com", host)

    @Test
    fun aDmarcPassForTheFromDomainFromTheUsersServerCounts() {
        assertEquals("shop.com", passOf("$server; dmarc=pass (p=REJECT) header.from=Shop.com policy.dmarc=reject"))
        assertEquals("shop.com", passOf("mail.example.net 1; dmarc=pass header.from=shop.com"))
    }

    @Test
    fun aFieldFromAnotherServerDoesNotCount() {
        assertNull(passOf("evil.example; dmarc=pass header.from=shop.com"))
        assertNull(passOf("example.net.evil.com; dmarc=pass header.from=shop.com"))
        assertNull(dmarcPassDomain(listOf("$server; dmarc=pass header.from=shop.com"), "news@shop.com", ""))
    }

    @Test
    fun onlyTheTopmostFieldCounts() {
        val forged = "mx.example.net; dmarc=pass header.from=shop.com"
        assertNull(passOf("$server; dmarc=fail header.from=shop.com", forged))
        assertNull(passOf(server, forged))
    }

    @Test
    fun aPassForAnotherDomainOrAnyFailDoesNotCount() {
        assertNull(passOf("$server; dmarc=pass header.from=other.com"))
        assertNull(passOf("$server; dmarc=pass header.from=shop.com; dmarc=fail header.from=shop.com"))
    }

    @Test
    fun resultsInsideQuotedStringsOrCommentsDoNotCount() {
        assertNull(passOf("mx.example.net; spf=pass smtp.mailfrom=\"a; dmarc=pass header.from=shop.com; b\"@evil.com"))
        assertNull(passOf("mx.example.net; spf=pass (x; dmarc=pass header.from=shop.com) smtp.mailfrom=evil.com"))
        assertNull(passOf("mx.example.net; dmarc=pass header.from=evil.com reason=\"x header.from=shop.com y\""))
    }
}
