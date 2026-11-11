package com.otusfoss.mail.core.messages.domain

/** Which side of mail the filter panel looks people up on: who wrote it, or who received it. */
enum class PeopleField { From, To }

/**
 * A person a search found: [person] as the mail or contact names them, in how many of the found
 * [conversations], and when the newest of them arrived, as an ISO date and time; contacts found
 * no mail and leave it empty.
 */
data class FoundPerson(
    val person: MailSender,
    val conversations: Int = 0,
    val lastReceived: String = "",
)

/** People of mail beyond the loaded rows, which the filter panel's From and To lines offer. */
fun interface PeopleSearch {
    /**
     * Senders or recipients, by [field], of the mail of [logins] (null for every login) that
     * [text] finds, and contacts matching it. Empty when no server can be read.
     */
    suspend fun find(
        field: PeopleField,
        text: String,
        logins: Set<String>?,
    ): List<FoundPerson>
}
