package com.otus.jmap.login

import android.content.Context
import android.content.SharedPreferences
import java.security.GeneralSecurityException

/**
 * Logins in app-private preferences; secrets sealed by [KeystoreCipher]. Keep app backup disabled
 * so neither leaves the device. Each change is one synchronous commit.
 */
public class AndroidLoginStore internal constructor(
    private val preferences: SharedPreferences,
    private val cipher: LoginCipher,
) : LoginStore {
    public constructor(
        context: Context,
        preferencesName: String = "otus_logins",
        keyAlias: String = "otus.login.secrets",
    ) : this(
        context.applicationContext.getSharedPreferences(preferencesName, Context.MODE_PRIVATE),
        KeystoreCipher(keyAlias),
    )

    private val lock = Any()

    override fun logins(): List<JmapLogin> = synchronized(lock) { readLogins() }

    override fun secret(id: String): LoginSecret =
        preferences.getString(secretKey(id), null)?.let(cipher::open) ?: LoginSecret.Missing

    override fun save(
        login: JmapLogin,
        secret: String,
    ): Unit =
        synchronized(lock) {
            val sealed =
                try {
                    cipher.seal(secret)
                } catch (error: GeneralSecurityException) {
                    throw LoginStoreException("The secret could not be encrypted.", error)
                }
            val logins = readLogins().filterNot { it.id == login.id } + login
            commit { putString(LOGINS_KEY, LoginCodec.encode(logins)).putString(secretKey(login.id), sealed) }
        }

    override fun remove(id: String): Unit =
        synchronized(lock) {
            val logins = readLogins().filterNot { it.id == id }
            commit { putString(LOGINS_KEY, LoginCodec.encode(logins)).remove(secretKey(id)) }
        }

    /** An unreadable list is copied aside once, so a later save cannot destroy it. */
    private fun readLogins(): List<JmapLogin> {
        val raw = preferences.getString(LOGINS_KEY, null)
        return when (val decoded = LoginCodec.decode(raw)) {
            is LoginCodec.Decoded.Logins -> {
                decoded.logins
            }

            is LoginCodec.Decoded.Unreadable -> {
                val backup = LOGINS_KEY + LoginCodec.UNREADABLE_SUFFIX
                if (!preferences.contains(backup)) commit { putString(backup, raw) }
                emptyList()
            }
        }
    }

    private fun commit(change: SharedPreferences.Editor.() -> SharedPreferences.Editor) {
        if (!preferences.edit().change().commit()) throw LoginStoreException("Login storage could not be written.")
    }

    private fun secretKey(id: String) = "secret:$id"

    private companion object {
        const val LOGINS_KEY = "accounts"
    }
}
