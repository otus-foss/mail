package com.otusfoss.mail.feature.inbox.presentation.components

import com.otusfoss.mail.core.messages.presentation.label
import com.otusfoss.mail.core.settings.presentation.label
import com.otusfoss.mail.feature.inbox.domain.InboxSearch

/** The inboxes beside the accounts in the account pill. No name means every inbox. */
internal class MailboxSummary(
    private val names: List<String>,
) {
    /** Particular inboxes rather than all of them. */
    val specific: Boolean
        get() = names.isNotEmpty()

    /** One name, or the first and how many more. */
    val label: String
        get() =
            when (names.size) {
                0 -> ALL
                1 -> names.single()
                else -> "${names.first()} +${names.size - 1}"
            }

    val description: String
        get() = names.joinToString(", ").ifEmpty { ALL }

    companion object {
        private const val ALL = "All inboxes"

        /** Null on a page without inbox choices, such as a folder's page, whose default has no mailbox. */
        fun of(
            search: InboxSearch,
            default: InboxSearch,
        ): MailboxSummary? =
            if (search.mailboxes.isEmpty() && default.mailboxes.isEmpty()) {
                null
            } else {
                MailboxSummary(search.mailboxes.map { it.name }.distinct())
            }
    }
}
