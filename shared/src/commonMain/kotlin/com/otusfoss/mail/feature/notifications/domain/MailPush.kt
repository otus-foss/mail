package com.otusfoss.mail.feature.notifications.domain

/** Which mail changes a push subscription asks the server for (RFC 8620, section 7.2). */
enum class MailPush(
    internal val types: List<String>,
) {
    /**
     * New deliveries only (RFC 8621, section 1.5), where every push shows an alert, as on iOS, whose
     * notification extension cannot tell a delivery from another change.
     */
    Deliveries(listOf("EmailDelivery")),

    /** Deliveries and flag or folder changes, so mail read elsewhere loses its notification. */
    Changes(listOf("EmailDelivery", "Email")),
}
