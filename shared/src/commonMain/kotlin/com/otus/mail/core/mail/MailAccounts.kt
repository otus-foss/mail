package com.otus.mail.core.mail

import com.otus.jmap.JmapCapability
import com.otus.jmap.JmapConnection
import com.otus.jmap.JmapConnections
import com.otus.jmap.JmapException
import com.otus.jmap.JmapSession
import com.otus.jmap.login.JmapLogin
import com.otus.jmap.login.LoginStore
import com.otus.jmap.login.LoginStoreException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext

/** One login's connection and the JMAP account that holds its mail. */
internal class MailAccount(
    val login: JmapLogin,
    val connection: JmapConnection,
    val session: JmapSession,
    val id: String,
)

/**
 * Resolves saved logins to their mail accounts. When the saved logins cannot be read, it throws
 * what [storageFailure] makes of the [LoginStoreException], so a feature can report its own error.
 */
internal class MailAccounts(
    private val connections: JmapConnections,
    private val logins: LoginStore,
    private val storageFailure: (LoginStoreException) -> Exception = { it },
) {
    suspend fun logins(): List<JmapLogin> =
        withContext(Dispatchers.IO) {
            try {
                logins.logins()
            } catch (error: LoginStoreException) {
                throw storageFailure(error)
            }
        }

    /** Throws [JmapException.Unsupported] when the login has no mail account. */
    suspend fun open(login: JmapLogin): MailAccount {
        val connection = connections.connection(login)
        val session = connection.session()
        val id = session.accountFor(JmapCapability.MAIL) ?: throw JmapException.Unsupported(JmapCapability.MAIL)
        return MailAccount(login, connection, session, id)
    }

    /** The account for [loginId], or null once the login was removed. */
    suspend fun open(loginId: String): MailAccount? = logins().firstOrNull { it.id == loginId }?.let { open(it) }
}
