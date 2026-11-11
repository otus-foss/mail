package com.otusfoss.mail.core.messages.data

import com.otusfoss.core.jmap.JmapConnections
import com.otusfoss.core.jmap.JmapException
import com.otusfoss.core.jmap.jmapPatch
import com.otusfoss.core.jmap.login.LoginStoreException
import com.otusfoss.mail.core.cache.CacheStorageException
import com.otusfoss.mail.core.cache.MailCache
import com.otusfoss.mail.core.mail.Keywords
import com.otusfoss.mail.core.mail.keywordPatch
import com.otusfoss.mail.core.mail.mailAccount
import com.otusfoss.mail.core.mail.mailboxPatch
import com.otusfoss.mail.core.mail.movePatch
import com.otusfoss.mail.core.mail.refilePatch
import com.otusfoss.mail.core.mailbox.MailFolder
import com.otusfoss.mail.core.mailbox.MailboxRole
import com.otusfoss.mail.core.mailbox.Mailboxes
import com.otusfoss.mail.core.messages.domain.Deletion
import com.otusfoss.mail.core.messages.domain.EmailWriter
import com.otusfoss.mail.core.messages.domain.MailMessage
import com.otusfoss.mail.core.messages.domain.MessageFlag
import com.otusfoss.mail.core.messages.domain.memberMailboxes
import kotlinx.serialization.json.JsonPrimitive

/**
 * Every email change as `Email/set` patches (RFC 8621, sections 4.1.1 and 4.6) through [queue],
 * which shows each one in [cache] and keeps it until the server has it. Marks set or clear one
 * keyword; a cleared keyword is removed, because keyword values must be true. Moves name only the
 * mailboxes that change, so a message keeps any other mailbox it is in. Deleting for good first
 * sends the waiting changes and stops while any still wait, so it never destroys a message the
 * user moved out of the folder offline, then goes straight to the server; see [EmailRemoval].
 */
class JmapEmailWriter(
    connections: JmapConnections,
    cache: MailCache,
    private val queue: EmailChangeQueue,
) : EmailWriter {
    private val removal = EmailRemoval(connections, cache)
    private val muting = ConversationMuting(connections, cache)

    override suspend fun mark(
        messages: List<MailMessage>,
        flag: MessageFlag,
        on: Boolean,
    ): Set<String> = queue.updateRows(messages, keywordPatch(flag.keyword, on))

    override suspend fun move(
        message: MailMessage,
        folderId: String,
        leaving: Set<String>,
    ): Boolean {
        val left = leaving - folderId - ""
        if (left.isEmpty()) return false
        return queue.updateRow(message, movePatch(left, folderId))
    }

    /**
     * Each email of the row goes back only to the folders of [returning] it was in before the move,
     * so a conversation whose emails were in different folders does not end up in all of them.
     */
    override suspend fun moveBack(
        message: MailMessage,
        folderId: String,
        returning: Set<String>,
    ): Boolean {
        val back = returning - folderId - ""
        if (back.isEmpty()) return false
        val patches =
            message.memberMailboxes
                .mapValues { (_, before) ->
                    jmapPatch {
                        if (folderId !in before) remove("mailboxIds", folderId)
                        back.intersect(before.ifEmpty { back }).forEach { set(JsonPrimitive(true), "mailboxIds", it) }
                    }
                }.ifEmpty { return false }
        return queue.update(message.location.accountId, patches).containsAll(patches.keys)
    }

    override suspend fun setInFolder(
        message: MailMessage,
        folderId: String,
        inFolder: Boolean,
    ): Boolean = queue.updateRow(message, mailboxPatch(folderId, inFolder))

    /**
     * Sends each email its own patch, so one call files a conversation whose emails differ. Emails
     * already where they belong send nothing and count as done.
     */
    override suspend fun setMailboxes(
        accountId: String,
        before: Map<String, Set<String>>,
        after: Map<String, Set<String>>,
    ): Set<String> {
        val patches =
            after
                .mapValues { (emailId, boxes) -> refilePatch(before[emailId].orEmpty(), boxes) }
                .filterValues { it.isNotEmpty() }
        val unchanged = after.keys - patches.keys
        return if (patches.isEmpty()) unchanged else queue.update(accountId, patches) + unchanged
    }

    /** Each listed email of the row leaves the Spam folder it is in; with keywords, `$notjunk` replaces `$junk`. */
    override suspend fun notSpam(
        message: MailMessage,
        inboxId: String,
        keywords: Boolean,
    ): Boolean {
        val shown = message.location.mailboxId
        if (shown.isEmpty() || shown == inboxId || message.emailId.isEmpty()) return false
        val patches =
            message.listedMembers().associate { member ->
                member.emailId to
                    jmapPatch {
                        include(movePatch(listOf(member.folderId), inboxId))
                        if (keywords) {
                            remove("keywords", Keywords.JUNK)
                            set(JsonPrimitive(true), "keywords", Keywords.NOT_JUNK)
                        }
                    }
            }
        return queue.update(message.location.accountId, patches).containsAll(patches.keys)
    }

    override suspend fun delete(messages: List<MailMessage>): Set<String> =
        messages
            .filter { it.emailId.isNotEmpty() && it.location.mailboxId.isNotEmpty() }
            .groupBy { it.location.accountId }
            .flatMap { (loginId, group) ->
                val stored = group.flatMap { it.listedMembers() }.distinctBy { it.emailId }
                val removed = queue.afterSending(loginId) { removal.remove(loginId) { stored } }?.emailIds.orEmpty()
                group.filter { row -> removed.containsAll(row.listedMembers().map { it.emailId }) }.map { it.id }
            }.toSet()

    override suspend fun empty(
        accountId: String,
        folderId: String,
    ): Deletion =
        queue.afterSending(accountId) { removal.remove(accountId) { folderEmails(folderId) } }
            ?: Deletion(emptySet(), complete = false)

    override suspend fun isMuted(message: MailMessage): Boolean = muting.isMuted(message)

    override suspend fun setMuted(
        message: MailMessage,
        muted: Boolean,
    ): Boolean {
        val ids = muting.emailIds(message).ifEmpty { return false }
        val patch = keywordPatch(Keywords.MUTED, muted)
        return queue.update(message.location.accountId, ids.associateWith { patch }).containsAll(ids)
    }
}

internal val MessageFlag.keyword: String
    get() =
        when (this) {
            MessageFlag.Read -> Keywords.SEEN
            MessageFlag.Flagged -> Keywords.FLAGGED
        }
