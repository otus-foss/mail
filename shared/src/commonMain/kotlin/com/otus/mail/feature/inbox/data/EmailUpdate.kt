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
 * The mail account of the saved login [loginId], or null once the login was removed. Like
 * [MailAccounts.open], it throws [JmapException.Unsupported] when the login has no mail, and it
 * throws what reading logins and the session throw.
 */
internal suspend fun mailAccount(
    connections: JmapConnections,
    logins: LoginStore,
    loginId: String,
): MailAccount? = MailAccounts(connections, logins).open(loginId)
