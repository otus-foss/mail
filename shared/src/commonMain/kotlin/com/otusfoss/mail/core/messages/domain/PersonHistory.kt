package com.otusfoss.mail.core.messages.domain

/** The mail one account has with one person: their [recent] threads and the people [shared] in them. */
data class PersonHistory(
    val recent: List<PersonThread>,
    val shared: List<SharedPerson>,
)

/** A thread with the person, as its newest email shows it. */
data class PersonThread(
    val emailId: String,
    val threadId: String,
    val subject: String,
    val preview: String,
    val received: ReceivedAt,
    val unread: Boolean,
)

/** Someone else in [threads] of the person's threads, the newest of them named [lastSubject]. */
data class SharedPerson(
    val person: MailSender,
    val threads: Int,
    val lastSubject: String,
)

/** Looks up a person's mail on the server. */
fun interface PersonHistories {
    /**
     * The newest threads of [accountId] that [address] sent or received, without Trash and Spam,
     * and the people besides them and the user's [own] addresses; null when the server is out of reach.
     */
    suspend fun of(
        accountId: String,
        address: String,
        own: (String) -> Boolean,
    ): PersonHistory?

    companion object {
        /** How many threads Recent lists. */
        const val RECENT: Int = 3
    }
}
