package com.otus.mail.feature.inbox.data

import com.otus.jmap.JmapConnections
import com.otus.jmap.JmapException
import com.otus.jmap.login.LoginStore
import com.otus.jmap.login.LoginStoreException
import com.otus.mail.core.cache.CacheKind
import com.otus.mail.core.cache.InMemoryMailCache
import com.otus.mail.core.cache.MailCache
import com.otus.mail.core.mail.EMAIL_PROPERTIES
import com.otus.mail.core.mail.EmailObject
import com.otus.mail.core.mail.ThreadObject
import com.otus.mail.core.mail.mailboxes
import com.otus.mail.core.mail.objects
import com.otus.mail.feature.inbox.domain.ConversationMessages
import com.otus.mail.feature.inbox.domain.MailMessage
import com.otus.mail.feature.inbox.domain.mailboxName
import kotlinx.datetime.TimeZone
import kotlin.time.Instant

/**
 * Reads every email of a message's thread with the row properties (RFC 8621, sections 3 and 4.2),
 * so each becomes a page of the Read view. Offline, it falls back to the thread and emails the
 * list saved in [cache], which may lack some of them.
 */
class JmapConversationMessages(
    private val connections: JmapConnections,
    private val logins: LoginStore,
    private val cache: MailCache = InMemoryMailCache(),
    private val zone: () -> TimeZone = { TimeZone.currentSystemDefault() },
) : ConversationMessages {
    override suspend fun messages(message: MailMessage): List<MailMessage>? {
        val loginId = message.location.accountId
        // A supplied message has no server id, so it stands alone.
        val emails = if (message.emailId.isEmpty()) emptyList() else emails(message) ?: return null
        val mailboxes = cache.mailboxes(loginId)
        val names = mailboxes.associate { it.id to mailboxName(it.name, it.role) }
        // Deleted and spam mail stay out of the pages, as in the list, unless the row itself is there.
        val binned = mailboxes.filter { it.role in BINS }.map { it.id }.toSet()
        val hidden = binned.takeUnless { message.location.mailboxId in it }.orEmpty()
        return emails
            .filter { it.keywords[DRAFT_KEYWORD] != true }
            .filterNot { it.boxes.isNotEmpty() && hidden.containsAll(it.boxes) }
            .sortedByDescending { Instant.parseOrNull(it.receivedAt) }
            .map { it.page(message, names) }
            .ifEmpty { listOf(message.copy(conversation = null)) }
    }

    /** The thread's emails from the server, or from the device when the server cannot be reached. */
    private suspend fun emails(message: MailMessage): List<EmailObject>? =
        try {
            mailAccount(connections, logins, message.location.accountId)
                ?.threadEmails(message.emailId, EMAIL_PROPERTIES)
        } catch (expected: JmapException) {
            cached(message)
        } catch (expected: LoginStoreException) {
            null
        }

    /** The emails the list saved for the row's thread; null when it saved none. */
    private suspend fun cached(message: MailMessage): List<EmailObject>? {
        val loginId = message.location.accountId
        val thread =
            message.conversation?.threadId?.let {
                cache.objects(loginId, CacheKind.Thread, ThreadObject.serializer(), listOf(it))[it]
            } ?: return null
        return cache
            .objects(loginId, CacheKind.Email, EmailObject.serializer(), thread.emailIds)
            .values
            .toList()
            .ifEmpty { null }
    }

    /**
     * This email as a page of [row]'s conversation. It stays in the row's listed mailbox when it is
     * in it, and otherwise names its own first mailbox, so Delete and Move act on where it is.
     */
    private fun EmailObject.page(
        row: MailMessage,
        names: Map<String, String>,
    ): MailMessage {
        val listed = row.location.mailboxId.takeIf { it in boxes } ?: boxes.firstOrNull().orEmpty()
        val mailbox = if (listed == row.location.mailboxId) row.location.mailbox else names[listed].orEmpty()
        val page =
            toMessage(
                row.location.accountId,
                row.location.account,
                mailbox,
                zone(),
                row.location.accountAddress,
                listed,
            )
        // The list already found the row sender's photo; pages from the same address reuse it.
        val sender = if (page.sender.address.equals(row.sender.address, ignoreCase = true)) row.sender else page.sender
        return page.copy(sender = sender, location = page.location.copy(accountAvatar = row.location.accountAvatar))
    }
}

private val BINS = setOf("trash", "junk")
