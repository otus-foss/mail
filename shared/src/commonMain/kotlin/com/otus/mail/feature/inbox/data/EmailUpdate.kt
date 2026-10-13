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
 * Applies [patch] to [message] with one `Email/set` update (RFC 8621, section 4.6) in the mail
 * account of the message's login. False when the server refused it or could not be reached.
 */
internal suspend fun updateEmail(
    connections: JmapConnections,
    logins: LoginStore,
    message: MailMessage,
    patch: JsonObject,
): Boolean =
    message.emailId.isNotEmpty() &&
        try {
            val account = mailAccount(connections, logins, message.location.accountId)
            account != null &&
                message.emailId in
                account.connection
                    .call(SetMethod(EmailType, account.id, update = mapOf(message.emailId to patch)))
                    .updated
        } catch (expected: JmapException) {
            false
        } catch (expected: LoginStoreException) {
            false
        }

/**
 * Applies [patch] to each of [messages] with `Email/set` updates: per account, one call for each
 * batch of at most the session's `maxObjectsInSet` messages. Accounts are asked at the same time, so
 * a slow one holds up only its own messages. Returns the [MailMessage.id]s the server updated; the
 * others were refused, have no server id, or their account could not be reached.
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
                    val ids = group.map { it.emailId }.distinct()
                    val updated = updateAccountEmails(connections, logins, loginId, ids, patch)
                    group.filter { it.emailId in updated }.map { it.id }
                }
            }.awaitAll()
            .flatten()
            .toSet()
    }

/** The [emailIds] of [loginId] that [patch] updated, batch by batch. */
private suspend fun updateAccountEmails(
    connections: JmapConnections,
    logins: LoginStore,
    loginId: String,
    emailIds: List<String>,
    patch: JsonObject,
): Set<String> {
    val updated = mutableSetOf<String>()
    // A failed batch keeps the ones before it, so the caller knows which changed.
    return try {
        val account = mailAccount(connections, logins, loginId) ?: return emptySet()
        emailIds.chunked(account.session.setBatch).forEach { batch ->
            val result =
                account.connection.call(
                    SetMethod(EmailType, account.id, update = batch.associateWith { patch }),
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
