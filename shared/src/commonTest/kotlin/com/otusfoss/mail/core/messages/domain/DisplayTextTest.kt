package com.otusfoss.mail.core.messages.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class DisplayTextTest {
    @Test
    fun aRightToLeftOverrideCannotReverseASenderName() {
        assertEquals("lapyap", "\u202Elapyap".displayLine())
    }

    @Test
    fun everyBidiControlIsRemoved() {
        val controls = "\u061C\u200E\u200F\u202A\u202B\u202C\u202D\u202E\u2066\u2067\u2068\u2069"
        assertEquals("PayPal", "Pay${controls}Pal".displayLine())
    }

    @Test
    fun controlCharactersAreRemovedAndWhitespaceCollapsesOnOneLine() {
        assertEquals("Invoice due today", " Invoice\u0000\u0007 due\r\n\ttoday\u0085 ".displayLine())
        assertEquals("a b", "a\u2028b".displayLine())
    }

    @Test
    fun aBodyKeepsItsLinesButLosesHiddenCharacters() {
        assertEquals("Hi,\n\nSee you\tsoon\n", "Hi,\r\n\r\nSee\u0000 you\tsoon\u202E\r".displayText())
    }

    @Test
    fun ordinaryTextIsUnchanged() {
        assertEquals("Grüße, 李 · مرحبا", "Grüße, 李 · مرحبا".displayLine())
    }

    @Test
    fun aCleanAddressOnlyLosesSurroundingWhitespace() {
        assertEquals("Ada@Example.org", " Ada@Example.org\n".displayAddress())
        assertTrue("Ada@Example.org".isUsableAddress())
    }

    @Test
    fun anEscapedAddressCannotMatchOneWrittenWithTheEscapeAsText() {
        val hidden = "ada@corp\u200E.example.com".displayAddress()
        val typed = "ada@corp\\u{200E}.example.com".displayAddress()

        assertNotEquals(hidden, typed)
        assertFalse(typed.isUsableAddress())
    }

    @Test
    fun charactersThatDrawNothingShowAsEscapes() {
        listOf('\u200B', '\u2060', '\uFEFF', '\u00AD').forEach { invisible ->
            val address = "ada@corp$invisible.example.com"

            assertNotEquals("ada@corp.example.com", address.displayAddress())
            assertTrue(address.displayAddress().all { it.code in 0x20..0x7E })
            assertFalse(address.isUsableAddress())
        }
    }

    @Test
    fun innerWhitespaceMakesAnAddressUnusable() {
        assertEquals("ada\\u{0020}@example.org", "ada @example.org".displayAddress())
        assertFalse("".isUsableAddress())
    }
}
