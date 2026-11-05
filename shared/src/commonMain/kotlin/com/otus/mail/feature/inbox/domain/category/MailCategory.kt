package com.otus.mail.feature.inbox.domain.category

/**
 * Where the Inbox's category pills sort a message. A category is a view over the Inbox: messages
 * stay in their mailboxes. [Primary] always exists and holds everything the classifier is unsure
 * of. The order of the entries is the order of the pills.
 */
enum class MailCategory(
    val storageValue: String,
) {
    Primary("primary"),

    /** Receipts, orders, bookings, billing, shipping, and payments. */
    Transactions("transactions"),

    /** Account, security, and service notices, such as CI results. */
    Updates("updates"),

    /** Social networks and community notifications. */
    Social("social"),

    /** Lists and newsletters the user signed up for, when they do not sell. */
    Newsletters("newsletters"),

    /** Marketing and sales. */
    Promotions("promotions"),
    ;

    companion object {
        /** Null for a value this version does not know. */
        fun fromStorageValue(value: String?): MailCategory? = entries.firstOrNull { it.storageValue == value }
    }
}
