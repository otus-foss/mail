package com.otus.mail.core.mailbox

/**
 * The mailbox roles Mail gives a place of their own (RFC 8621, section 2), in sidebar order.
 * [wire] is the role as JMAP spells it. [title] replaces each server's own name, which differs
 * between servers, such as Stalwart's "Junk Mail" for [Junk].
 */
enum class MailboxRole(
    val wire: String,
    val title: String,
) {
    Inbox("inbox", "Inbox"),
    Drafts("drafts", "Drafts"),
    Sent("sent", "Sent"),
    Scheduled("scheduled", "Scheduled"),
    Archive("archive", "Archive"),
    Junk("junk", "Spam"),
    Trash("trash", "Trash"),
    ;

    /** Drafts and Scheduled, whose mail nobody received yet, so it is never unread. */
    val holdsUnsent: Boolean
        get() = this == Drafts || this == Scheduled

    /**
     * Whether a list of this folder shows conversations. Scheduled lists each email apart, since
     * each is a send of its own with its own time, and the folder counts emails.
     */
    val listsConversations: Boolean
        get() = this != Scheduled

    /** Drafts, Scheduled, and Sent hold the user's own mail, which never looks read. */
    val holdsOwnMail: Boolean
        get() = holdsUnsent || this == Sent

    /** Trash and Spam, where discarded mail goes. */
    val isBin: Boolean
        get() = this == Junk || this == Trash

    companion object {
        /** The system folders the sidebar shows once for every account, in sidebar order. */
        val combined: List<MailboxRole> = entries - Inbox

        /** The role [wire] names, in any case; null for no role or one Mail has no place for. */
        fun of(wire: String?): MailboxRole? = wire?.lowercase()?.let { role -> entries.firstOrNull { it.wire == role } }
    }
}

/** Each combined role that some account has, with every account's folder of that role. */
fun combinedFolders(accounts: List<AccountFolders>): Map<MailboxRole, List<MailFolder>> {
    val folders = accounts.flatMap { it.folders }
    return MailboxRole.combined
        .associateWith { role -> folders.filter { it.role == role } }
        .filterValues { it.isNotEmpty() }
}
