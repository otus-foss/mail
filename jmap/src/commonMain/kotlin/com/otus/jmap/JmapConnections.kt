package com.otus.jmap

import com.otus.jmap.login.JmapLogin
import com.otus.jmap.login.LoginSecret
import com.otus.jmap.login.LoginStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** One shared [JmapConnection] per saved login. Call [forget] after a login is replaced or removed. */
public class JmapConnections(
    private val client: JmapClient,
    private val logins: LoginStore,
) {
    private val lock = Mutex()
    private val open = mutableMapOf<String, JmapConnection>()

    /** Throws [JmapException.CredentialsUnavailable] when the saved secret is missing or unreadable. */
    public suspend fun connection(login: JmapLogin): JmapConnection =
        lock.withLock {
            open[login.id] ?: create(login).also { open[login.id] = it }
        }

    public suspend fun forget(loginId: String) {
        lock.withLock { open.remove(loginId) }
    }

    private suspend fun create(login: JmapLogin): JmapConnection {
        val secret = withContext(Dispatchers.IO) { logins.secret(login.id) }
        if (secret !is LoginSecret.Found) throw JmapException.CredentialsUnavailable(login.id)
        return JmapConnection(client, login.sessionUrl, login.auth(secret.value), login.trustedOrigins)
    }
}
