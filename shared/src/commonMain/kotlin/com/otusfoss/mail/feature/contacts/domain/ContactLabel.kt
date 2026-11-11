package com.otusfoss.mail.feature.contacts.domain

import com.otusfoss.core.jscontact.ContactContext

/**
 * The label of an address or number. Work and Personal map to the JSContact contexts `work` and
 * `private` (RFC 9553, section 1.5.1); a free-text label, such as "Assistant", is [Custom].
 */
sealed interface ContactLabel {
    val text: String

    data object Work : ContactLabel {
        override val text = "Work"
    }

    data object Personal : ContactLabel {
        override val text = "Personal"
    }

    data object Other : ContactLabel {
        override val text = "Other"
    }

    data class Custom(
        override val text: String,
    ) : ContactLabel

    /** The JSContact contexts that carry this label; a custom one keeps any it had. */
    fun contexts(previous: Map<ContactContext, Boolean>?): Map<ContactContext, Boolean>? =
        when (this) {
            Work -> mapOf(ContactContext.Work to true)
            Personal -> mapOf(ContactContext.Private to true)
            Other -> null
            is Custom -> previous
        }

    /** The free-text label to store, which only a custom label has. */
    val custom: String? get() = (this as? Custom)?.text

    companion object {
        fun of(
            contexts: Map<ContactContext, Boolean>?,
            label: String?,
        ): ContactLabel {
            val on = contexts.orEmpty().filterValues { it }.keys
            return when {
                !label.isNullOrBlank() -> Custom(label)
                ContactContext.Work in on -> Work
                ContactContext.Private in on -> Personal
                else -> Other
            }
        }
    }
}
