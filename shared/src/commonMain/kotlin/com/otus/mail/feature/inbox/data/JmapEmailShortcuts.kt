package com.otus.mail.feature.inbox.data

import com.otus.jmap.JmapConnections
import com.otus.jmap.JmapException
import com.otus.jmap.login.LoginStore
import com.otus.jmap.login.LoginStoreException
import com.otus.mail.core.cache.InMemoryMailCache
import com.otus.mail.core.cache.MailCache
import com.otus.mail.core.mail.Keywords
import com.otus.mail.core.mail.keywordPatch
import com.otus.mail.core.mail.mailAccount
import com.otus.mail.core.mail.movePatch
import com.otus.mail.core.mailbox.MailFolder
import com.otus.mail.core.mailbox.MailboxRole
import com.otus.mail.core.mailbox.Mailboxes
import com.otus.mail.feature.inbox.domain.EmailShortcuts
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Marks or files one email with an `Email/set` patch (RFC 8621, section 4.6), like the list's own
 * marks and moves: the email changes in [cache] too, and offline the change waits in [queue].
 * Archive, Trash, and Spam find the account's folder by role in the kept folders, or ask the
 * server when the device has none.
 */
class JmapEmailShortcuts(
    private val connections: JmapConnections,
    private val cache: MailCache = InMemoryMailCache(),
    private val queue: EmailChangeQueue? = null,
) : EmailShortcuts {
    override suspend fun markRead(
        loginId: String,
        emailId: String,
    ): Boolean = update(loginId, emailId, keywordPatch(Keywords.SEEN, true))

    override suspend fun flag(
        loginId: String,
        emailId: String,
    ): Boolean = update(loginId, emailId, keywordPatch(Keywords.FLAGGED, true))

    override suspend fun archive(
        loginId: String,
        emailId: String,
    ): Boolean {
        val mailboxes = mailboxes(loginId)
        val archive = mailboxes.withRole(MailboxRole.Archive) ?: return false
        val inboxes = mailboxes.filter { it.role == MailboxRole.Inbox }.map { it.id }
        return update(loginId, emailId, movePatch(inboxes, archive.id))
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
     * Puts the email in the account's mailbox with [role] alone, as the list's Delete and Spam do:
     * it leaves every other mailbox, Read later included.
     */
    private suspend fun discard(
        loginId: String,
        emailId: String,
        role: MailboxRole,
    ): Boolean {
        val bin = mailboxes(loginId).withRole(role) ?: return false
        return update(loginId, emailId, buildJsonObject { put("mailboxIds", buildJsonObject { put(bin.id, true) }) })
    }

    private suspend fun update(
        loginId: String,
        emailId: String,
        patch: JsonObject,
    ): Boolean = emailId in updateAccountEmails(connections, loginId, mapOf(emailId to patch), cache, queue)

    private fun List<MailFolder>.withRole(role: MailboxRole): MailFolder? = firstOrNull { it.role == role }

    /** The login's folders; none when the login is gone or the server cannot be reached. */
    private suspend fun mailboxes(loginId: String): List<MailFolder> =
        try {
            connections.mailAccount(loginId)?.let { Mailboxes(cache).of(it) }.orEmpty()
        } catch (expected: JmapException) {
            emptyList()
        } catch (expected: LoginStoreException) {
            emptyList()
        }
}
