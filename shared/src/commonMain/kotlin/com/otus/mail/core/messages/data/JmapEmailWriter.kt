package com.otus.mail.core.messages.data

import com.otus.jmap.JmapConnections
import com.otus.jmap.JmapException
import com.otus.jmap.jmapPatch
import com.otus.jmap.login.LoginStoreException
import com.otus.mail.core.cache.MailCache
import com.otus.mail.core.mail.Keywords
import com.otus.mail.core.mail.keywordPatch
import com.otus.mail.core.mail.mailAccount
import com.otus.mail.core.mail.mailboxPatch
import com.otus.mail.core.mail.movePatch
import com.otus.mail.core.mailbox.MailFolder
import com.otus.mail.core.mailbox.MailboxRole
import com.otus.mail.core.mailbox.Mailboxes
import com.otus.mail.core.messages.domain.Deletion
import com.otus.mail.core.messages.domain.EmailWriter
import com.otus.mail.core.messages.domain.MailMessage
import com.otus.mail.core.messages.domain.MessageFlag
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Every email change as `Email/set` patches (RFC 8621, sections 4.1.1 and 4.6) through [queue],
 * which shows each one in [cache] and keeps it until the server has it. Marks set or clear one
 * keyword; a cleared keyword is removed, because keyword values must be true. Moves name only the
 * mailboxes that change, so a message keeps any other mailbox it is in. Deleting for good goes
 * straight to the server; see [EmailRemoval]. By-id changes find the account's folder by role in
 * [mailboxes].
 */
class JmapEmailWriter(
    private val connections: JmapConnections,
    cache: MailCache,
    private val queue: EmailChangeQueue,
    private val mailboxes: Mailboxes,
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

    override suspend fun moveBack(
        message: MailMessage,
        folderId: String,
        returning: Set<String>,
    ): Boolean {
        val back = returning - folderId - ""
        if (back.isEmpty()) return false
        val patch =
            jmapPatch {
                if (folderId !in message.location.mailboxIds) remove("mailboxIds", folderId)
                back.forEach { set(JsonPrimitive(true), "mailboxIds", it) }
            }
        return queue.updateRow(message, patch)
    }

    override suspend fun setInFolder(
        message: MailMessage,
        folderId: String,
        inFolder: Boolean,
    ): Boolean = queue.updateRow(message, mailboxPatch(folderId, inFolder))

    /** Sends each email's whole `mailboxIds`, so one call files a conversation whose emails differ. */
    override suspend fun setMailboxes(
        accountId: String,
        mailboxes: Map<String, Set<String>>,
    ): Set<String> =
        update(
            accountId,
            mailboxes.mapValues { (_, boxes) ->
                buildJsonObject { put("mailboxIds", JsonObject(boxes.associateWith { JsonPrimitive(true) })) }
            },
        )

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
        return update(message.location.accountId, patches).containsAll(patches.keys)
    }

    override suspend fun delete(messages: List<MailMessage>): Set<String> =
        messages
            .filter { it.emailId.isNotEmpty() && it.location.mailboxId.isNotEmpty() }
            .groupBy { it.location.accountId }
            .flatMap { (loginId, group) ->
                val stored = group.flatMap { it.listedMembers() }.distinctBy { it.emailId }
                val removed = removal.remove(loginId) { stored }.emailIds
                group.filter { row -> removed.containsAll(row.listedMembers().map { it.emailId }) }.map { it.id }
            }.toSet()

    override suspend fun empty(
        accountId: String,
        folderId: String,
    ): Deletion = removal.remove(accountId) { folderEmails(folderId) }

    override suspend fun isMuted(message: MailMessage): Boolean = muting.isMuted(message)

    override suspend fun setMuted(
        message: MailMessage,
        muted: Boolean,
    ): Boolean {
        val ids = muting.emailIds(message).ifEmpty { return false }
        val patch = keywordPatch(Keywords.MUTED, muted)
        return update(message.location.accountId, ids.associateWith { patch }).containsAll(ids)
    }

    override suspend fun markRead(
        loginId: String,
        emailId: String,
    ): Boolean = updateOne(loginId, emailId, keywordPatch(Keywords.SEEN, true))

    override suspend fun flag(
        loginId: String,
        emailId: String,
    ): Boolean = updateOne(loginId, emailId, keywordPatch(Keywords.FLAGGED, true))

    /** Leaves only the inbox, like the list's Archive. */
    override suspend fun archive(
        loginId: String,
        emailId: String,
    ): Boolean {
        val folders = folders(loginId)
        val archive = folders.withRole(MailboxRole.Archive) ?: return false
        val inboxes = folders.filter { it.role == MailboxRole.Inbox }.map { it.id }
        return updateOne(loginId, emailId, movePatch(inboxes, archive.id))
    }

    override suspend fun trash(
        loginId: String,
        emailId: String,
    ): Boolean = discard(loginId, emailId, MailboxRole.Trash)

    override suspend fun reportSpam(
        loginId: String,
        emailId: String,
    ): Boolean = discard(loginId, emailId, MailboxRole.Junk)

    /**
     * Applies [patches] to [loginId]'s emails by Email id; returns the ones that changed or wait for
     * the server.
     */
    internal suspend fun update(
        loginId: String,
        patches: Map<String, JsonObject>,
    ): Set<String> = queue.update(loginId, patches)

    /**
     * Puts the email in the account's mailbox with [role] alone, as the list's Delete and Spam do:
     * it leaves every other mailbox, Read later included.
     */
    private suspend fun discard(
        loginId: String,
        emailId: String,
        role: MailboxRole,
    ): Boolean {
        val bin = folders(loginId).withRole(role) ?: return false
        return updateOne(loginId, emailId, buildJsonObject { put("mailboxIds", buildJsonObject { put(bin.id, true) }) })
    }

    private suspend fun updateOne(
        loginId: String,
        emailId: String,
        patch: JsonObject,
    ): Boolean = emailId in update(loginId, mapOf(emailId to patch))

    private fun List<MailFolder>.withRole(role: MailboxRole): MailFolder? = firstOrNull { it.role == role }

    /** The login's folders; none when the login is gone or the server cannot be reached. */
    private suspend fun folders(loginId: String): List<MailFolder> =
        try {
            connections.mailAccount(loginId)?.let { mailboxes.of(it) }.orEmpty()
        } catch (expected: JmapException) {
            emptyList()
        } catch (expected: LoginStoreException) {
            emptyList()
        }
}

internal val MessageFlag.keyword: String
    get() =
        when (this) {
            MessageFlag.Read -> Keywords.SEEN
            MessageFlag.Flagged -> Keywords.FLAGGED
        }
