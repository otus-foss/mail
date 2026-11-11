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
import com.otusfoss.mail.core.mailbox.MailFolder
import com.otusfoss.mail.core.mailbox.MailboxRole
import com.otusfoss.mail.core.mailbox.Mailboxes
import com.otusfoss.mail.core.messages.domain.Deletion
import com.otusfoss.mail.core.messages.domain.EmailByIdWriter
import com.otusfoss.mail.core.messages.domain.MailMessage
import com.otusfoss.mail.core.messages.domain.MessageFlag
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * The changes of one email by id as `Email/set` patches through [queue], as [JmapEmailWriter]
 * makes them. They find the account's folder by role among the server's folders now, or the kept
 * ones offline.
 */
class JmapEmailByIdWriter(
    private val connections: JmapConnections,
    private val queue: EmailChangeQueue,
    private val mailboxes: Mailboxes,
) : EmailByIdWriter {
    override suspend fun markRead(
        loginId: String,
        emailId: String,
    ): Boolean = updateOne(loginId, emailId, keywordPatch(Keywords.SEEN, true))

    override suspend fun flag(
        loginId: String,
        emailId: String,
    ): Boolean = updateOne(loginId, emailId, keywordPatch(Keywords.FLAGGED, true))

    /** Takes the email out of the Inbox and into Archive; any other mailbox, such as Read later, stays. */
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
     * Puts the email in the account's mailbox with [role] alone, as the list's Delete and Spam do:
     * it leaves every other mailbox, Read later included. It names each known mailbox instead of
     * replacing `mailboxIds`, so it merges with other waiting changes as queued patches must.
     */
    private suspend fun discard(
        loginId: String,
        emailId: String,
        role: MailboxRole,
    ): Boolean {
        val folders = folders(loginId)
        val bin = folders.withRole(role) ?: return false
        return updateOne(loginId, emailId, movePatch(folders.map { it.id }, bin.id))
    }

    private suspend fun updateOne(
        loginId: String,
        emailId: String,
        patch: JsonObject,
    ): Boolean = emailId in queue.update(loginId, mapOf(emailId to patch))

    private fun List<MailFolder>.withRole(role: MailboxRole): MailFolder? = firstOrNull { it.role == role }

    /**
     * The login's folders on the server now, since another client may have added or re-roled one
     * since the last sync; the kept ones when the server cannot be reached, so the change still
     * waits in the queue. None when the login is gone, or neither the server nor the device can say.
     */
    private suspend fun folders(loginId: String): List<MailFolder> =
        try {
            val account = connections.mailAccount(loginId) ?: return emptyList()
            try {
                mailboxes.sync(account)
            } catch (expected: JmapException) {
                mailboxes.cached(loginId)
            }
        } catch (expected: JmapException) {
            emptyList()
        } catch (expected: LoginStoreException) {
            emptyList()
        } catch (expected: CacheStorageException) {
            emptyList()
        }
}
