package com.otusfoss.mail.core.mail

import com.otusfoss.core.jmap.JmapCapability
import com.otusfoss.core.jmap.JmapClient
import com.otusfoss.core.jmap.JmapConnections
import com.otusfoss.core.jmap.JmapException
import com.otusfoss.core.jmap.login.InMemoryLoginStore
import com.otusfoss.core.jmap.login.JmapLogin
import com.otusfoss.core.jmap.login.LoginSecret
import com.otusfoss.core.jmap.login.LoginStore
import com.otusfoss.core.jmap.login.LoginStoreException
import com.otusfoss.mail.testing.ADA_LOGIN
import com.otusfoss.mail.testing.CONTACTS_ONLY_SESSION
import com.otusfoss.mail.testing.STALWART_SESSION
import com.otusfoss.mail.testing.stalwartLike
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class MailAccountsTest {
    private val login = ADA_LOGIN

    private fun connections(
        session: String = STALWART_SESSION,
        store: LoginStore = InMemoryLoginStore().apply { save(login, "secret") },
    ) = JmapConnections(JmapClient(stalwartLike(session)), store)

    @Test
    fun aSavedLoginOpensTheAccountThatHoldsItsMail() =
        runTest {
            val account = assertNotNull(connections().mailAccount("ada"))
            assertEquals(login, account.login)
            assertEquals("d333333", account.id)
        }

    @Test
    fun aRemovedLoginHasNoAccount() =
        runTest {
            assertNull(connections().mailAccount("gone"))
        }

    @Test
    fun aLoginWithoutMailIsUnsupportedRatherThanRemoved() =
        runTest {
            val error =
                assertFailsWith<JmapException.Unsupported> { connections(CONTACTS_ONLY_SESSION).mailAccount("ada") }
            assertEquals(JmapCapability.MAIL, error.capability)
        }

    @Test
    fun unreadableLoginsThrowTheStoreFailure() =
        runTest {
            assertFailsWith<LoginStoreException> { connections(store = UnreadableLogins).mailAccount("ada") }
        }

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
