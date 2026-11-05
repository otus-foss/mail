package com.otus.mail.feature.inbox.data

import com.otus.jmap.GetMethod
import com.otus.jmap.JmapConnections
import com.otus.jmap.JmapException
import com.otus.jmap.login.LoginStore
import com.otus.jmap.login.LoginStoreException
import com.otus.mail.core.cache.InMemoryMailCache
import com.otus.mail.core.cache.MailCache
import com.otus.mail.core.mail.ARCHIVE_ROLE
import com.otus.mail.core.mail.INBOX_ROLE
import com.otus.mail.core.mail.MailboxObject
import com.otus.mail.core.mail.MailboxType
import com.otus.mail.feature.inbox.domain.EmailShortcuts
import kotlinx.serialization.json.JsonObject

/**
 * Marks or files one email with an `Email/set` patch (RFC 8621, section 4.6), like the list's own
 * marks and moves: the email changes in [cache] too, and offline the change waits in [queue].
 * Archive reads the account's mailbox roles first (section 2), so it needs the server.
 */
class JmapEmailShortcuts(
    private val connections: JmapConnections,
    private val logins: LoginStore,
    private val cache: MailCache = InMemoryMailCache(),
    private val queue: EmailChangeQueue? = null,
) : EmailShortcuts {
    override suspend fun markRead(
        loginId: String,
        emailId: String,
    ): Boolean = update(loginId, emailId, membershipPatch("keywords", SEEN_KEYWORD, member = true))

    override suspend fun archive(
        loginId: String,
        emailId: String,
    ): Boolean {
        val mailboxes = mailboxes(loginId)
        val archive = mailboxes.firstOrNull { it.role?.lowercase() == ARCHIVE_ROLE } ?: return false
        val inboxes = mailboxes.filter { it.role?.lowercase() == INBOX_ROLE }.map { it.id }
        return update(loginId, emailId, movePatch(inboxes, archive.id))
    }

    private suspend fun update(
        loginId: String,
        emailId: String,
        patch: JsonObject,
    ): Boolean = emailId in updateAccountEmails(connections, logins, loginId, mapOf(emailId to patch), cache, queue)

    /** The login's mailboxes with their roles; none when the login is gone or the server cannot be reached. */
    private suspend fun mailboxes(loginId: String): List<MailboxObject> =
        try {
            val account = mailAccount(connections, logins, loginId) ?: return emptyList()
            account.connection
                .call(GetMethod(MailboxType, account.id, properties = listOf("id", "role")))
                .list
        } catch (expected: JmapException) {
            emptyList()
        } catch (expected: LoginStoreException) {
            emptyList()
        }
}
