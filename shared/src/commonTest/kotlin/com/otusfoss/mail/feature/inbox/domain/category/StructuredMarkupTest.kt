package com.otusfoss.mail.feature.inbox.domain.category

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.TimeSource

class StructuredMarkupTest {
    @Test
    fun jsonLdTypesAreFound() {
        val html =
            """
            <html><head><script type="application/ld+json">
            {"@context": "http://schema.org", "@type": "Order", "merchant": {"@type": "Organization"},
             "acceptedOffer": {"@type" : "Offer"}}
            </script></head><body>Thanks</body></html>
            """.trimIndent()
        assertEquals(setOf("Order"), schemaTypes(html), "Types outside the known ones are left out")
    }

    @Test
    fun fullTypeUrlsAndArraysAreFound() {
        val html = """{"@type": ["https://schema.org/ParcelDelivery"]} {"@type":"http://schema.org/Invoice"}"""
        assertEquals(setOf("ParcelDelivery", "Invoice"), schemaTypes(html))
    }

    @Test
    fun microdataTypesAreFound() {
        val html =
            """
            <div itemscope itemtype="http://schema.org/FlightReservation">
              <div itemprop="reservationFor" itemscope itemtype='https://schema.org/Flight'></div>
            </div>
            """.trimIndent()
        assertEquals(setOf("FlightReservation"), schemaTypes(html))
    }

    @Test
    fun textThatOnlyNamesATypeIsNotMarkup() {
        assertEquals(emptySet(), schemaTypes("<p>Your Order has shipped. @type Order</p>"))
    }

    @Test
    fun markupPastTheReadLimitIsIgnored() {
        val late = "x".repeat(MAX_MARKUP_CHARS) + """{"@type": "Order"}"""
        assertEquals(emptySet(), schemaTypes(late))
    }

    @Test
    fun craftedMarkupStaysQuick() {
        val crafted = "\"@type\"" + " ".repeat(50_000) + ":".repeat(50_000) + "itemtype=" + "\"".repeat(90_000)
        val started = TimeSource.Monotonic.markNow()
        assertEquals(emptySet(), schemaTypes(crafted))
        val elapsed = started.elapsedNow().inWholeMilliseconds
        assertTrue(elapsed < 2_000, "Took $elapsed ms")
    }
}
