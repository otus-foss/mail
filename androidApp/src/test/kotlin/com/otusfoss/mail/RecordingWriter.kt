package com.otusfoss.mail

import com.otusfoss.mail.core.messages.domain.Deletion
import com.otusfoss.mail.core.messages.domain.EmailWriter
import com.otusfoss.mail.core.messages.domain.MailMessage
import com.otusfoss.mail.core.messages.domain.MessageFlag

/** A server that takes every change and only records which emails each was for. */
internal class RecordingWriter : EmailWriter {
    val marks = mutableListOf<Pair<String, MessageFlag>>()
    val moved = mutableListOf<String>()

    /** Each email's mailboxes the last refile sent, by Email id. */
    var refiled: Map<String, Set<String>>? = null

    override suspend fun mark(
        messages: List<MailMessage>,
        flag: MessageFlag,
        on: Boolean,
    ): Set<String> {
        messages.forEach { marks += it.emailId to flag }
        return messages.map { it.id }.toSet()
    }

    override suspend fun move(
        message: MailMessage,
        folderId: String,
        leaving: Set<String>,
    ): Boolean {
        moved += message.emailId
        return true
    }

    override suspend fun moveBack(
        message: MailMessage,
        folderId: String,
        returning: Set<String>,
    ) = true

    override suspend fun setInFolder(
        message: MailMessage,
        folderId: String,
        inFolder: Boolean,
    ) = true

    override suspend fun setMailboxes(
        accountId: String,
        mailboxes: Map<String, Set<String>>,
    ): Set<String> {
        refiled = mailboxes
        return mailboxes.keys
    }

    override suspend fun notSpam(
        message: MailMessage,
        inboxId: String,
        keywords: Boolean,
    ) = true

    override suspend fun delete(messages: List<MailMessage>) = messages.map { it.id }.toSet()

    override suspend fun empty(
        accountId: String,
        folderId: String,
    ) = Deletion(emptySet(), complete = true)

    override suspend fun isMuted(message: MailMessage) = false

    override suspend fun setMuted(
        message: MailMessage,
        muted: Boolean,
    ) = true
}
