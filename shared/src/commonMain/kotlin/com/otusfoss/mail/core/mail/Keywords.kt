package com.otusfoss.mail.core.mail

/** The email keywords Mail reads and sets (RFC 8621, section 4.1.1; RFC 5788). */
object Keywords {
    const val SEEN = "\$seen"
    const val FLAGGED = "\$flagged"
    const val DRAFT = "\$draft"
    const val ANSWERED = "\$answered"
    const val FORWARDED = "\$forwarded"
    const val JUNK = "\$junk"
    const val NOT_JUNK = "\$notjunk"

    /**
     * Marks an email of a muted conversation. A conversation is muted while any of its emails has
     * it, so new mail in it does not notify. Not a registered keyword, so other apps ignore it.
     */
    const val MUTED = "\$muted"
}

internal val EmailObject.isSeen: Boolean
    get() = keywords[Keywords.SEEN] == true

internal val EmailObject.isFlagged: Boolean
    get() = keywords[Keywords.FLAGGED] == true

internal val EmailObject.isDraft: Boolean
    get() = keywords[Keywords.DRAFT] == true

internal val EmailObject.isAnswered: Boolean
    get() = keywords[Keywords.ANSWERED] == true

internal val EmailObject.isForwarded: Boolean
    get() = keywords[Keywords.FORWARDED] == true

internal val EmailObject.isMuted: Boolean
    get() = keywords[Keywords.MUTED] == true
