package com.otus.jmap.login

import android.content.Context
import android.content.SharedPreferences
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.security.GeneralSecurityException
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotNull

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], manifest = Config.NONE)
class AndroidLoginStoreTest {
    private val preferences: SharedPreferences
        get() = RuntimeEnvironment.getApplication().getSharedPreferences("login-test", Context.MODE_PRIVATE)
    private val cipher = TestLoginCipher()
    private val login = JmapLogin("a", "https://example.org/session", "ada", "Ada")
    private val store get() = AndroidLoginStore(preferences, cipher)

    @Before
    fun clear() {
        preferences.edit().clear().commit()
    }

    @Test
    fun saveReplaceAndRemoveSurviveReopeningTheStore() {
        store.save(login, "old")
        store.save(login.copy(name = "Updated"), "new")
        assertEquals("Updated", store.logins().single().name)
        assertEquals("new", assertIs<LoginSecret.Found>(store.secret("a")).value)
        store.remove("a")
        assertEquals(emptyList(), store.logins())
        assertIs<LoginSecret.Missing>(store.secret("a"))
    }

    @Test
    fun encryptionFailurePreservesPreviousCredentials() {
        store.save(login, "old")
        cipher.fail = true
        assertFailsWith<LoginStoreException> { store.save(login.copy(name = "Updated"), "new") }
        assertEquals(listOf(login), store.logins())
        assertEquals("old", assertIs<LoginSecret.Found>(store.secret("a")).value)
    }

    @Test
    fun failedCommitsAreReportedForSaveAndRemoval() {
        val failing =
            object : SharedPreferences by preferences {
                override fun edit(): SharedPreferences.Editor =
                    object : SharedPreferences.Editor by preferences.edit() {
                        override fun putString(
                            key: String?,
                            value: String?,
                        ): SharedPreferences.Editor = this

                        override fun remove(key: String?): SharedPreferences.Editor = this

                        override fun commit() = false
                    }
            }
        val unavailable = AndroidLoginStore(failing, cipher)
        assertFailsWith<LoginStoreException> { unavailable.save(login, "secret") }
        assertFailsWith<LoginStoreException> { unavailable.remove("a") }
    }

    @Test
    fun corruptListIsBackedUpBeforeAReplacementIsSaved() {
        preferences.edit().putString("accounts", "broken").commit()
        assertEquals(emptyList(), store.logins())
        store.save(login, "secret")
        assertEquals("broken", preferences.getString("accounts.unreadable", null))
        assertEquals(listOf(login), store.logins())
        assertNotNull(preferences.getString("secret:a", null))
    }
}

private class TestLoginCipher : LoginCipher {
    var fail = false

    override fun seal(value: String): String {
        if (fail) throw GeneralSecurityException("encryption failed")
        return "sealed:$value"
    }

    override fun open(value: String): LoginSecret = LoginSecret.Found(value.removePrefix("sealed:"))
}
