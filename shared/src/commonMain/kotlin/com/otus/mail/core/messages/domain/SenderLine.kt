package com.otus.mail.core.messages.domain

/** Who a conversation row names. */
sealed interface SenderLine {
    /** "<name> +others": the newest email came from [name]. */
    data class From(
        val name: String,
        val others: Int,
    ) : SenderLine

    /** "You to <name> +others", when the thread's newest email is the user's own. */
    data class YouTo(
        val name: String,
        val others: Int,
    ) : SenderLine
}
