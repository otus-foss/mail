package com.otus.mail.core.categories.domain

/**
 * Where the Inbox's category pills sort a message. A category is a view over the Inbox: messages
 * stay in their mailboxes. [Primary] always exists and holds everything the classifier is unsure
 * of. The order of the entries is the order of the pills. Each category rests on signals that
 * rules tell apart reliably; splitting Newsletters from Promotions, or Social from Notifications,
 * waits for the classifier that learns from corrections (#239).
 */
enum class MailCategory(
    val storageValue: String,
) {
    Primary("primary"),

    /** Receipts, orders, bookings, bills, deliveries, and payments. */
    Transactions("transactions"),

    /** Automated mail that does not sell: account and security alerts, CI, and social networks. */
    Notifications("notifications"),

    /** All other bulk mail: newsletters and promotions. */
    Newsletters("newsletters"),
    ;

    companion object {
        /** Null for a value this version does not know. */
        fun fromStorageValue(value: String?): MailCategory? = entries.firstOrNull { it.storageValue == value }
    }
}
