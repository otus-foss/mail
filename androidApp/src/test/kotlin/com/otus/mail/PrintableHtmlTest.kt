package com.otus.mail

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PrintableHtmlTest {
    @Test
    fun aPrintedMessageLoadsNothingAndHintsNothing() {
        val printed =
            printableHtml(
                "<LINK rel=dns-prefetch href=//tracker.example><link/rel=preconnect href=https://a.example>" +
                    "<p>Hi</p><img src=\"data:image/png;base64,AA\">",
            )
        assertTrue(printed.startsWith("<meta http-equiv=\"Content-Security-Policy\" content=\"default-src 'none';"))
        assertTrue("<meta http-equiv=\"x-dns-prefetch-control\" content=\"off\">" in printed)
        assertFalse(Regex("<link(?=[\\s/>])", RegexOption.IGNORE_CASE).containsMatchIn(printed))
        assertTrue(printed.endsWith("<p>Hi</p><img src=\"data:image/png;base64,AA\">"))
    }

    @Test
    fun otherTagsStartingWithLinkStay() = assertTrue(printableHtml("<linker>").endsWith("<linker>"))
}
