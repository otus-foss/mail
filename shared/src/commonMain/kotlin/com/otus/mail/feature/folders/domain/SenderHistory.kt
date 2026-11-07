package com.otus.mail.feature.folders.domain

import com.otus.mail.core.messages.domain.MailMessage

/** Where a sender's earlier mail was filed, read from the server rather than stored by the app. */
interface SenderHistory {
    /**
     * The folders of each recent message from the sender of [message] in its account, newest
     * first. Empty when the message is not on a server or the server cannot be read.
     */
    suspend fun recentFolders(message: MailMessage): List<Set<String>>
}

/**
 * The folder of [eligible] that holds the most messages of [history]. A tie goes to the folder of
 * the newer message. Null when no message is in an eligible folder.
 */
fun suggestedFolder(
    history: List<Set<String>>,
    eligible: Set<String>,
): String? =
    history
        .flatMap { folders -> folders.filter { it in eligible } }
        .groupingBy { it }
        .eachCount()
        .maxByOrNull { it.value }
        ?.key
