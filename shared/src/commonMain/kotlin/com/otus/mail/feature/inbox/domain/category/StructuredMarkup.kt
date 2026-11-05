package com.otus.mail.feature.inbox.domain.category

/**
 * The schema.org types an HTML body declares, in JSON-LD (`"@type": "Order"`) or microdata
 * (`itemtype="https://schema.org/Order"`), among [TRANSACTION_TYPES] and [PROMOTION_TYPES]. Only
 * the first [MAX_MARKUP_CHARS] characters are read, which holds a mail's `<head>` and the start of
 * its body, so a large message costs no more than that. The patterns have no nested repetition, so
 * crafted markup cannot make them backtrack for long.
 */
fun schemaTypes(html: String): Set<String> {
    val head = if (html.length > MAX_MARKUP_CHARS) html.substring(0, MAX_MARKUP_CHARS) else html
    return (JSON_LD_TYPE.findAll(head) + MICRODATA_TYPE.findAll(head))
        .map { it.groupValues[1] }
        .filter { it in KNOWN_TYPES }
        .toSet()
}

/** schema.org types that mark an order, an invoice, a delivery, or a booking. */
val TRANSACTION_TYPES =
    setOf(
        "Order",
        "Invoice",
        "ParcelDelivery",
        "Reservation",
        "FlightReservation",
        "EventReservation",
        "LodgingReservation",
        "TrainReservation",
        "BusReservation",
        "BoatReservation",
        "RentalCarReservation",
        "TaxiReservation",
        "FoodEstablishmentReservation",
    )

/** schema.org types that mark an offer made to sell something. */
val PROMOTION_TYPES = setOf("DiscountOffer", "PromotionCard")

private val KNOWN_TYPES = TRANSACTION_TYPES + PROMOTION_TYPES

const val MAX_MARKUP_CHARS = 200_000

private val JSON_LD_TYPE = Regex(""""@type"\s{0,8}:\s{0,8}\[?\s{0,8}"(?:https?://schema\.org/)?([A-Za-z]{1,40})"""")
private val MICRODATA_TYPE = Regex("""itemtype\s{0,8}=\s{0,8}["']https?://schema\.org/([A-Za-z]{1,40})""")
