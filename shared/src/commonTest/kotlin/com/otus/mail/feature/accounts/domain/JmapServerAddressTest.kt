package com.otus.mail.feature.accounts.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class JmapServerAddressTest {
    @Test
    fun bareHostUsesTheWellKnownDiscoveryPath() {
        assertEquals(
            JmapServerAddress.Valid("https://mail.example.com/.well-known/jmap"),
            parseJmapServer("  Mail.Example.com  "),
        )
        assertEquals(
            JmapServerAddress.Valid("https://mail.example.com:8443/.well-known/jmap"),
            parseJmapServer("https://mail.example.com:8443/"),
        )
    }

    @Test
    fun explicitPathIsUsedAsTheSessionUrl() {
        assertEquals(
            JmapServerAddress.Valid("https://example.com/jmap/session"),
            parseJmapServer("https://example.com/jmap/session/"),
        )
    }

    @Test
    fun plainHttpAndMalformedInputAreRejected() {
        assertEquals(JmapServerAddress.Empty, parseJmapServer("   "))
        assertEquals(JmapServerAddress.Insecure, parseJmapServer("http://mail.example.com"))
        assertEquals(JmapServerAddress.Invalid, parseJmapServer("ftp://mail.example.com"))
        assertEquals(JmapServerAddress.Invalid, parseJmapServer("mail example.com"))
        assertEquals(JmapServerAddress.Invalid, parseJmapServer("https://"))
        assertEquals(JmapServerAddress.Invalid, parseJmapServer("-bad-.example.com"))
    }

    @Test
    fun draftNeverPrintsItsPassword() {
        assertFalse("hunter2" in JmapAccountDraft("https://a.example/.well-known/jmap", "me", "hunter2").toString())
    }
}
