package com.otus.mail.feature.inbox.domain

import kotlin.test.Test
import kotlin.test.assertEquals

class SignatureTest {
    private val logo = SignatureImage("image/png", "iVBORw0KGgo=", 150)

    @Test
    fun aSignatureSplitsIntoItsTextAndImagesAndJoinsBack() {
        val parts = SignatureParts("<p>Alex Rivera</p>", listOf(logo))
        assertEquals(parts, parseSignature(parts.html()))
    }

    @Test
    fun theStartOfASignatureReadsOnOneLine() {
        val signature = Signature(text = "Alex Rivera\n\nProduct lead · Company A\ncompanya.com")
        assertEquals("Alex Rivera · Product lead · Company A", signature.preview)
        assertEquals("", Signature().preview)
    }
}
