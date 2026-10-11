package com.otus.mail.feature.inbox.domain

/** What swiping an inbox row does. A swipe without an available action leaves the row still. */
enum class MessageSwipe(
    val storageValue: String,
) {
    None("none"),
    Read("read"),
    Flag("flag"),
    Archive("archive"),
    Trash("trash"),
    ;

    companion object {
        fun fromStorageValue(value: String?): MessageSwipe? = entries.firstOrNull { it.storageValue == value }
    }
}

/**
 * How the inbox responds to the user. Swipes are named by layout direction: [startToEnd] is a
 * swipe right in left-to-right languages.
 */
data class MailBehavior(
    val startToEnd: MessageSwipe = MessageSwipe.Read,
    val endToStart: MessageSwipe = MessageSwipe.Archive,
    val markReadOnOpen: Boolean = true,
)

/** App-private storage for [MailBehavior]. Unknown or missing values read as the defaults. */
interface MailBehaviorStore {
    fun read(): MailBehavior

    fun write(behavior: MailBehavior)
}
