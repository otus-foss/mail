package com.otus.mail.core.mail

import com.otus.jmap.JmapCapability
import com.otus.jmap.JmapClient
import com.otus.jmap.JmapConnections
import com.otus.jmap.JmapException
import com.otus.jmap.login.InMemoryLoginStore
import com.otus.jmap.login.JmapLogin
import com.otus.jmap.login.LoginSecret
import com.otus.jmap.login.LoginStore
import com.otus.jmap.login.LoginStoreException
import com.otus.mail.testing.CONTACTS_ONLY_SESSION
import com.otus.mail.testing.STALWART_SESSION
import com.otus.mail.testing.stalwartLike
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class MailAccountsTest {
    private val login = JmapLogin("ada", "https://mail.example.org/.well-known/jmap", "ada@example.org", "Ada")

    private fun accounts(
        session: String = STALWART_SESSION,
        store: LoginStore = InMemoryLoginStore().apply { save(login, "secret") },
        storageFailure: (LoginStoreException) -> Exception = { it },
    ) = MailAccounts(JmapConnections(JmapClient(stalwartLike(session)), store), store, storageFailure)

    @Test
    fun aSavedLoginOpensTheAccountThatHoldsItsMail() =
        runTest {
            val account = assertNotNull(accounts().open("ada"))
            assertEquals(login, account.login)
            assertEquals("d333333", account.id)
        }

    @Test
    fun aRemovedLoginHasNoAccount() =
        runTest {
            assertNull(accounts().open("gone"))
        }

    @Test
    fun aLoginWithoutMailIsUnsupportedRatherThanRemoved() =
        runTest {
            val error = assertFailsWith<JmapException.Unsupported> { accounts(CONTACTS_ONLY_SESSION).open("ada") }
            assertEquals(JmapCapability.MAIL, error.capability)
        }

    @Test
    fun unreadableLoginsThrowWhatTheFeatureReports() =
        runTest {
            assertFailsWith<LoginStoreException> { accounts(store = UnreadableLogins).open("ada") }
            assertFailsWith<StorageUnavailable> {
                accounts(store = UnreadableLogins, storageFailure = ::StorageUnavailable).open("ada")
            }
        }

    private class StorageUnavailable(
        cause: Throwable,
    ) : Exception("Saved accounts are unavailable.", cause)

    private object UnreadableLogins : LoginStore {
        override fun logins(): List<JmapLogin> = throw LoginStoreException("The saved logins cannot be read.")

        override fun secret(id: String) = LoginSecret.Missing

        override fun save(
            login: JmapLogin,
            secret: String,
        ) = Unit

        override fun remove(id: String) = Unit
    }
}
