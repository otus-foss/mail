package com.otus.mail.feature.inbox.domain.category

/**
 * What the classifier reads of one message, all of it already on the device: the sender, the
 * subject, the text the device has (the server's preview, or the body once it was opened), the
 * [headers], and the schema.org types found in a downloaded body as [markup].
 */
data class CategorySignals(
    val senderAddress: String,
    val subject: String = "",
    val text: String = "",
    val headers: BulkHeaders = BulkHeaders(),
    val markup: Set<String> = emptySet(),
)

/**
 * The header fields that tell automated and bulk mail apart, as the server parsed them. [listId]
 * (RFC 2919), [listUnsubscribe] (RFC 2369), and [listPost] mark mailing lists; [precedence] is the
 * informal `Precedence` field; [autoSubmitted] is RFC 3834's field; [feedbackId] is the loop id
 * bulk senders add for mailbox providers. [platforms] names the mail platforms whose own header
 * the message carries, such as "Mailchimp".
 */
data class BulkHeaders(
    val listId: String? = null,
    val listUnsubscribe: Boolean = false,
    val listPost: String? = null,
    val precedence: String? = null,
    val autoSubmitted: String? = null,
    val feedbackId: String? = null,
    val platforms: List<String> = emptyList(),
) {
    /** The list header names present, in a fixed order, for a reason's detail. */
    val listFields: List<String>
        get() =
            listOfNotNull(
                "List-Unsubscribe".takeIf { listUnsubscribe },
                "List-Id".takeIf { listId != null },
                precedence
                    ?.trim()
                    ?.lowercase()
                    ?.takeIf { it in LIST_PRECEDENCE }
                    ?.let { "Precedence: $it" },
            )

    /** The message came from a list or a bulk sender. */
    val fromList: Boolean
        get() = listFields.isNotEmpty()

    /** `Auto-Submitted` with any value but `no` (RFC 3834, section 5). */
    val autoSubmittedValue: String?
        get() =
            autoSubmitted
                ?.substringBefore(';')
                ?.trim()
                ?.lowercase()
                ?.takeIf { it.isNotEmpty() && it != "no" }

    /** The automation headers present, in a fixed order, for a reason's detail. */
    val automationFields: List<String>
        get() {
            val submitted = autoSubmittedValue?.let { "Auto-Submitted: $it" }
            return listOfNotNull(submitted, "Feedback-ID".takeIf { feedbackId != null }) + platforms
        }

    private companion object {
        val LIST_PRECEDENCE = setOf("bulk", "list", "junk")
    }
}
