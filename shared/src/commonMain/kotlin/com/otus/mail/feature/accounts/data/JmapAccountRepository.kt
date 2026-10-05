package com.otus.mail.feature.accounts.data

import com.otus.jmap.GetMethod
import com.otus.jmap.JmapAuth
import com.otus.jmap.JmapCapability
import com.otus.jmap.JmapClient
import com.otus.jmap.JmapConnections
import com.otus.jmap.JmapException
import com.otus.jmap.JmapSession
import com.otus.jmap.login.JmapLogin
import com.otus.jmap.login.LoginStore
import com.otus.jmap.login.LoginStoreException
import com.otus.mail.core.mail.MailboxType
import com.otus.mail.feature.accounts.domain.AccountRepository
import com.otus.mail.feature.accounts.domain.AccountStorageException
import com.otus.mail.feature.accounts.domain.ConnectFailure
import com.otus.mail.feature.accounts.domain.ConnectOutcome
import com.otus.mail.feature.accounts.domain.ConnectedAccount
import com.otus.mail.feature.accounts.domain.JmapAccountDraft
import com.otus.mail.feature.accounts.domain.isValidUsername
import io.ktor.http.Url
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext

/** Verifies a server and credentials, then saves the login. Nothing is saved when verification fails. */
class JmapAccountRepository(
    private val client: JmapClient,
    private val connections: JmapConnections,
    private val logins: LoginStore,
) : AccountRepository {
    override suspend fun accounts(): List<ConnectedAccount> = storage { logins.logins().map { it.toConnected() } }

    override suspend fun connect(draft: JmapAccountDraft): ConnectOutcome {
        if (!isValidUsername(draft.username)) return ConnectOutcome.Failed(ConnectFailure.InvalidUsername)
        return try {
            val auth = JmapAuth.Basic(draft.username, draft.password)
            val session = client.session(draft.sessionUrl, auth, draft.trustedOrigins)
            if (verifyMail(session, auth)) save(draft, session) else ConnectOutcome.Failed(ConnectFailure.NoMail)
        } catch (error: JmapException.RedirectedElsewhere) {
            ConnectOutcome.ConfirmHost(error.host, error.origin)
        } catch (error: JmapException) {
            ConnectOutcome.Failed(error.toFailure())
        } catch (expected: LoginStoreException) {
            // Storage did not confirm the save; the user can retry.
            ConnectOutcome.Failed(ConnectFailure.StorageFailed)
        }
    }

    override suspend fun remove(id: String) {
        storage { logins.remove(id) }
        connections.forget(id)
    }

    private suspend fun <T> storage(read: () -> T): T =
        withContext(Dispatchers.IO) {
            try {
                read()
            } catch (error: LoginStoreException) {
                throw AccountStorageException(error)
            }
        }

    /** A readable session can still deny mail; confirm the mail API answers before saving. */
    private suspend fun verifyMail(
        session: JmapSession,
        auth: JmapAuth,
    ): Boolean {
        val accountId = session.accountFor(JmapCapability.MAIL) ?: return false
        client.request(session, auth) { call(GetMethod(MailboxType, accountId, properties = listOf("id"))) }
        return true
    }

    private suspend fun save(
        draft: JmapAccountDraft,
        session: JmapSession,
    ): ConnectOutcome {
        val login =
            withContext(Dispatchers.IO) {
                // Reconnecting the same user on the same server replaces its login instead of adding one.
                val existing =
                    logins.logins().firstOrNull {
                        it.username == draft.username &&
                            it.sessionUrl == draft.sessionUrl
                    }
                JmapLogin(
                    id = existing?.id ?: "${draft.username} ${draft.sessionUrl}",
                    sessionUrl = draft.sessionUrl,
                    username = draft.username,
                    name =
                        session.accountFor(JmapCapability.MAIL)?.let { session.accounts[it]?.name }?.ifBlank { null }
                            ?: draft.username,
                    trustedOrigins = draft.trustedOrigins,
                ).also { logins.save(it, draft.password) }
            }
        connections.forget(login.id)
        return ConnectOutcome.Connected(login.toConnected())
    }
}

internal fun JmapException.toFailure(): ConnectFailure =
    when (this) {
        is JmapException.Unauthorized -> ConnectFailure.WrongCredentials
        is JmapException.Forbidden -> ConnectFailure.NotPermitted
        is JmapException.NotJmap, is JmapException.TooManyRedirects -> ConnectFailure.NotJmap
        is JmapException.Unsupported -> ConnectFailure.NoMail
        is JmapException.Unreachable -> ConnectFailure.Unreachable
        is JmapException.InsecureUrl -> ConnectFailure.Insecure
        else -> ConnectFailure.ServerError
    }

private fun JmapLogin.toConnected() =
    ConnectedAccount(
        id,
        name,
        username,
        runCatching {
            Url(sessionUrl).host
        }.getOrDefault(sessionUrl),
    )
