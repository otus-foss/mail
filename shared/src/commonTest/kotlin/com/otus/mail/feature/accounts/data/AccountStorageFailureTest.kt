package com.otus.mail.feature.accounts.data

import com.otus.jmap.JmapClient
import com.otus.jmap.JmapConnections
import com.otus.jmap.login.JmapLogin
import com.otus.jmap.login.LoginSecret
import com.otus.jmap.login.LoginStore
import com.otus.jmap.login.LoginStoreException
import com.otus.mail.feature.accounts.domain.AccountStorageException
import com.otus.mail.feature.accounts.domain.ConnectFailure
import com.otus.mail.feature.accounts.domain.ConnectOutcome
import com.otus.mail.feature.accounts.domain.JmapAccountDraft
import com.otus.mail.feature.inbox.data.JmapMailSource
import com.otus.mail.feature.inbox.domain.InboxStorageException
import com.otus.mail.testing.stalwartLike
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class AccountStorageFailureTest {
    private val store = FailingLoginStore()
    private val client =
        JmapClient(
            stalwartLike {
                """{"methodResponses":[["Mailbox/get",{"list":[],"state":"s"},"c0"]]}"""
            },
        )
    private val connections = JmapConnections(client, store)
    private val repository = JmapAccountRepository(client, connections, store)

    @Test
    fun accountReadsAndRemovalsExposeDomainErrors() =
        runTest {
            store.failRead = true
            assertFailsWith<AccountStorageException> { repository.accounts() }
            assertFailsWith<AccountStorageException> { repository.remove("a") }
        }

    @Test
    fun failedCredentialSaveReportsStorageFailure() =
        runTest {
            val draft = JmapAccountDraft("https://mail.example.org/.well-known/jmap", "ada@example.org", "secret")
            assertEquals(ConnectOutcome.Failed(ConnectFailure.StorageFailed), repository.connect(draft))
        }

    @Test
    fun inboxAccountReadFailureExposesDomainError() =
        runTest {
            store.failRead = true
            assertFailsWith<InboxStorageException> { JmapMailSource(connections, store).inbox() }
        }
}

private class FailingLoginStore : LoginStore {
    var failRead = false

    override fun logins(): List<JmapLogin> {
        if (failRead) throw LoginStoreException("read failed")
        return emptyList()
    }

    override fun secret(id: String): LoginSecret = LoginSecret.Missing

    override fun save(
        login: JmapLogin,
        secret: String,
    ): Unit = throw LoginStoreException("save failed")

    override fun remove(id: String): Unit = throw LoginStoreException("remove failed")
}
