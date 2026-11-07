package com.otus.mail.core.messages.domain

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
    fun anImageWidthOutOfRangeIsClampedInsteadOfFailing() {
        val html = """<p>Alex</p><div><img src="data:image/png;base64,iVBORw0KGgo=" width="99999999999"></div>"""
        assertEquals(listOf(SignatureImages.MAX_WIDTH), parseSignature(html).images.map { it.width })
        assertEquals(listOf(1), parseSignature(html.replace("99999999999", "0")).images.map { it.width })
    }

    @Test
    fun theStartOfASignatureReadsOnOneLine() {
        val signature = Signature(text = "Alex Rivera\n\nProduct lead · Company A\ncompanya.com")
        assertEquals("Alex Rivera · Product lead · Company A", signature.preview)
        assertEquals("", Signature().preview)
    }
}
