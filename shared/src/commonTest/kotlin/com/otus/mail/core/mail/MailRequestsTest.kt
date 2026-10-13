package com.otus.mail.core.mail

import kotlin.test.Test
import kotlin.test.assertEquals

class MailRequestsTest {
    @Test
    fun pointerTokensEscapeTildesBeforeSlashes() {
        assertEquals("a~1b~0c", pointer("a/b~c"))
        // A literal "~1" must not read back as a slash (RFC 6901, section 4).
        assertEquals("~01", pointer("~1"))
        assertEquals("Mf3a-_9", pointer("Mf3a-_9"))
    }
}
