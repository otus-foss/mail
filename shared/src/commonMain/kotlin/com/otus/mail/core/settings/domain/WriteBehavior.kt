package com.otus.mail.core.settings.domain

/**
 * How Mail writes and sends. [recentFrom] holds the addresses the user last sent from, newest
 * first, which compose offers first and a new message from all accounts sends from.
 * [signatureInReplies] adds the identity's signature to replies and forwards too; new messages
 * always get it. [undoSendSeconds] holds each sent message that long so Undo can stop it, on the
 * server where it can; 0 sends at once. [photoCleaning] decides whether attached photos lose their
 * metadata and file names.
 */
data class WriteBehavior(
    val recentFrom: List<String> = emptyList(),
    val signatureInReplies: Boolean = true,
    val undoSendSeconds: Int = DEFAULT_UNDO_SEND_SECONDS,
    val photoCleaning: PhotoCleaning = PhotoCleaning.Ask,
) {
    /** [address] first among [recentFrom], keeping the [RECENT_FROM_LIMIT] newest. */
    fun sentFrom(address: String): WriteBehavior =
        copy(
            recentFrom =
                listOf(address)
                    .plus(recentFrom.filterNot { it.equals(address, ignoreCase = true) })
                    .take(RECENT_FROM_LIMIT),
        )
}

/** The Undo send delays settings offer, in seconds; 0 turns Undo send off. */
val UNDO_SEND_CHOICES =
    listOf(0, SHORT_UNDO_SEND_SECONDS, MEDIUM_UNDO_SEND_SECONDS, DEFAULT_UNDO_SEND_SECONDS, LONG_UNDO_SEND_SECONDS)

const val DEFAULT_UNDO_SEND_SECONDS = 15
private const val SHORT_UNDO_SEND_SECONDS = 5
private const val MEDIUM_UNDO_SEND_SECONDS = 10
private const val LONG_UNDO_SEND_SECONDS = 30

/** How many addresses [WriteBehavior.recentFrom] keeps: the Send-from list's Recent section. */
const val RECENT_FROM_LIMIT = 2
