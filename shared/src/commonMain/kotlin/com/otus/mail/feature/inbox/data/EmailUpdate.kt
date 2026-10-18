package com.otus.mail.feature.inbox.data

import com.otus.jmap.JmapConnections
import com.otus.jmap.JmapException
import com.otus.jmap.SetMethod
import com.otus.jmap.login.LoginStore
import com.otus.jmap.login.LoginStoreException
import com.otus.mail.core.mail.EmailType
import com.otus.mail.core.mail.MailAccount
import com.otus.mail.core.mail.MailAccounts
import com.otus.mail.feature.inbox.domain.MailMessage
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.JsonObject

/**
 * Applies [patch] to [message], and to every email of its conversation, with `Email/set` updates
 * (RFC 8621, section 4.6) in the mail account of the message's login. False when the server did
 * not update the row email or could not be reached.
 */
internal suspend fun updateEmail(
    connections: JmapConnections,
    logins: LoginStore,
    message: MailMessage,
    patch: JsonObject,
): Boolean =
    message.emailId.isNotEmpty() &&
        message.emailId in
        updateAccountEmails(
            connections,
            logins,
            message.location.accountId,
            message.memberIds.associateWith { patch },
        )

/**
 * Applies [patch] to each of [messages] and the emails of their conversations with `Email/set`
 * updates: per account, one call for each batch of at most the session's `maxObjectsInSet` emails.
 * Accounts are asked at the same time, so a slow one holds up only its own messages. Returns the
 * [MailMessage.id]s whose row email the server updated; the others were refused, have no server id,
 * or their account could not be reached.
 */
internal suspend fun updateEmails(
    connections: JmapConnections,
    logins: LoginStore,
    messages: List<MailMessage>,
    patch: JsonObject,
): Set<String> =
    coroutineScope {
        messages
            .filter { it.emailId.isNotEmpty() }
            .groupBy { it.location.accountId }
            .map { (loginId, group) ->
                async {
                    val patches = group.flatMap { it.memberIds }.associateWith { patch }
                    val updated = updateAccountEmails(connections, logins, loginId, patches)
                    group.filter { it.emailId in updated }.map { it.id }
                }
            }.awaitAll()
            .flatten()
            .toSet()
    }

/**
 * The emails an action on this row changes: every listed email of its conversation, or only its
 * own email when it stands alone.
 */
internal val MailMessage.memberIds: List<String>
    get() = (conversation?.members?.map { it.emailId } ?: listOf(emailId)).filter { it.isNotEmpty() }.distinct()

/** The emails of [loginId] that their [patches] updated, batch by batch. */
internal suspend fun updateAccountEmails(
    connections: JmapConnections,
    logins: LoginStore,
    loginId: String,
    patches: Map<String, JsonObject>,
): Set<String> {
    val updated = mutableSetOf<String>()
    // A failed batch keeps the ones before it, so the caller knows which changed.
    return try {
        val account = mailAccount(connections, logins, loginId) ?: return emptySet()
        patches.entries.chunked(account.session.setBatch).forEach { batch ->
            val result =
                account.connection.call(
                    SetMethod(EmailType, account.id, update = batch.associate { it.key to it.value }),
                )
            updated += result.updated.keys
        }
        updated
    } catch (expected: JmapException) {
        updated
    } catch (expected: LoginStoreException) {
        updated
    }
}

/**
 * The mail account of the saved login [loginId], or null once the login was removed. Like
 * [MailAccounts.open], it throws [JmapException.Unsupported] when the login has no mail, and it
 * throws what reading logins and the session throw.
 */
internal suspend fun mailAccount(
    connections: JmapConnections,
    logins: LoginStore,
    loginId: String,
): MailAccount? = MailAccounts(connections, logins).open(loginId)
