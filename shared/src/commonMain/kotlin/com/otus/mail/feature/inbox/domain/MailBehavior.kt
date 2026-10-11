package com.otus.mail.feature.inbox.domain

/** What swiping an inbox row does. A swipe without an available action leaves the row still. */
enum class MessageSwipe(
    val storageValue: String,
) {
    None("none"),
    Read("read"),
    Flag("flag"),
    ReadLater("read_later"),
    Archive("archive"),
    Trash("trash"),
    ;

    companion object {
        fun fromStorageValue(value: String?): MessageSwipe? = entries.firstOrNull { it.storageValue == value }
    }
}

/**
 * How the inbox responds to the user. Swipes are named by layout direction: [startToEnd] is a
 * swipe right in left-to-right languages. [readLater] adds a Read later folder to each account, an
 * action that saves mail there, and its pill. [pills] are the inbox row's pills, in order.
 * [combinedFolders] lists system folders once across accounts.
 */
data class MailBehavior(
    val startToEnd: MessageSwipe = MessageSwipe.Read,
    val endToStart: MessageSwipe = MessageSwipe.Archive,
    val markReadOnOpen: Boolean = true,
    val readLater: Boolean = false,
    val pills: List<InboxPill> = InboxPill.defaults(readLater),
    val combinedFolders: Boolean = false,
) {
    /** What a swipe can be set to; Read later only while it is on. */
    val swipeChoices: List<MessageSwipe>
        get() = MessageSwipe.entries.filter { readLater || it != MessageSwipe.ReadLater }

    /**
     * Turning Read later on adds its pill to the end of the row. Turning it off removes the pill
     * and returns a swipe set to it to that swipe's default.
     */
    fun withReadLater(on: Boolean): MailBehavior {
        val defaults = MailBehavior()

        fun MessageSwipe.orDefault(default: MessageSwipe) = if (!on && this == MessageSwipe.ReadLater) default else this
        return copy(
            readLater = on,
            startToEnd = startToEnd.orDefault(defaults.startToEnd),
            endToStart = endToStart.orDefault(defaults.endToStart),
            pills =
                when {
                    !on -> pills - InboxPill.ReadLater
                    InboxPill.ReadLater in pills -> pills
                    else -> pills + InboxPill.ReadLater
                },
        )
    }
}

/** App-private storage for [MailBehavior]. Unknown or missing values read as the defaults. */
interface MailBehaviorStore {
    fun read(): MailBehavior

    fun write(behavior: MailBehavior)
}
