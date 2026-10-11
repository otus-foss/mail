package com.otus.mail.feature.inbox.data

import com.otus.jmap.JmapCapability
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
            val saved = withContext(Dispatchers.IO) { logins.logins() }
            val connection =
                saved.firstOrNull { it.id == message.location.accountId }?.let {
                    connections.connection(
                        it,
                    )
                }
            val accountId = connection?.session()?.accountFor(JmapCapability.MAIL)
            connection != null &&
                accountId != null &&
                message.emailId in
                connection.call(SetMethod(EmailType, accountId, update = mapOf(message.emailId to patch))).updated
        } catch (expected: JmapException) {
            false
        } catch (expected: LoginStoreException) {
            false
        }
