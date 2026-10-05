package com.otus.jmap.login

import com.otus.jmap.JmapAuth
import kotlinx.serialization.Serializable

/**
 * A saved sign-in to one JMAP server. It identifies the server and user, not a JMAP account:
 * each app resolves its own account per capability, so one login serves mail and calendars.
 * It holds no secret; see [LoginStore.secret].
 */
@Serializable
public data class JmapLogin(
    val id: String,
    val sessionUrl: String,
    val username: String,
    val name: String,
    /** Other origins the user approved to receive these credentials. */
    val trustedOrigins: Set<String> = emptySet(),
    val method: AuthMethod = AuthMethod.Basic,
) {
    public fun auth(secret: String): JmapAuth =
        when (method) {
            AuthMethod.Basic -> JmapAuth.Basic(username, secret)
            AuthMethod.Bearer -> JmapAuth.Bearer(secret)
        }
}

public enum class AuthMethod { Basic, Bearer }

public sealed class LoginSecret {
    public class Found(
        internal val value: String,
    ) : LoginSecret() {
        override fun toString(): String = "LoginSecret.Found(***)"
    }

    public data object Missing : LoginSecret()

    /** Present but undecryptable, for example after the platform key was invalidated. */
    public class Unreadable(
        public val cause: Throwable? = null,
    ) : LoginSecret()
}

/** Storage could not complete an operation. Callers should report the failure and allow retry. */
public class LoginStoreException(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause)

/**
 * Saved logins. Platform implementations keep secrets in protected storage, apart from the list.
 * Calls block on storage, so run them off the main thread.
 */
public interface LoginStore {
    public fun logins(): List<JmapLogin>

    public fun secret(id: String): LoginSecret

    /** Saves or replaces [login] and its secret together. Throws [LoginStoreException]. */
    public fun save(
        login: JmapLogin,
        secret: String,
    )

    public fun remove(id: String)
}

/** For tests and previews. Nothing survives the process. */
public class InMemoryLoginStore : LoginStore {
    private val entries = linkedMapOf<String, Pair<JmapLogin, String>>()

    override fun logins(): List<JmapLogin> = entries.values.map { it.first }

    override fun secret(id: String): LoginSecret =
        entries[id]?.let { LoginSecret.Found(it.second) } ?: LoginSecret.Missing

    override fun save(
        login: JmapLogin,
        secret: String,
    ) {
        entries[login.id] = login to secret
    }

    override fun remove(id: String) {
        entries.remove(id)
    }
}
