package com.otus.mail.feature.inbox.data

import com.otus.jmap.JmapCapability
import com.otus.jmap.JmapConnection
import com.otus.jmap.JmapConnections
import com.otus.jmap.JmapException
import com.otus.jmap.SetMethod
import com.otus.jmap.login.LoginStore
import com.otus.jmap.login.LoginStoreException
import com.otus.mail.core.mail.EmailType
import com.otus.mail.feature.inbox.domain.MailMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
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

/** A login's connection and the id of its mail account on that server. */
internal class MailAccount(
    val connection: JmapConnection,
    val id: String,
)

/**
 * The mail account of the saved login [loginId]; null when the login is gone or has no mail. It
 * throws what reading logins and the session throw.
 */
internal suspend fun mailAccount(
    connections: JmapConnections,
    logins: LoginStore,
    loginId: String,
): MailAccount? {
    val login = withContext(Dispatchers.IO) { logins.logins() }.firstOrNull { it.id == loginId } ?: return null
    val connection = connections.connection(login)
    return connection.session().accountFor(JmapCapability.MAIL)?.let { MailAccount(connection, it) }
}
