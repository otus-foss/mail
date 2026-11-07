package com.otus.mail.core.messages.domain

import kotlin.test.Test
import kotlin.test.assertEquals

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
}
