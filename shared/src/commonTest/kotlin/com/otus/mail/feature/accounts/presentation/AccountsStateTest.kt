package com.otus.mail.feature.accounts.presentation

import com.otus.mail.feature.accounts.domain.AccountRepository
import com.otus.mail.feature.accounts.domain.AccountStorageException
import com.otus.mail.feature.accounts.domain.ConnectOutcome
import com.otus.mail.feature.accounts.domain.ConnectedAccount
import com.otus.mail.feature.accounts.domain.JmapAccountDraft
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

@OptIn(ExperimentalCoroutinesApi::class)
class AccountsStateTest {
    @Test
    fun failedInitialLoadCanBeRetried() =
        runTest {
            val repository = AccountStateRepository().apply { failRead = true }
            val state = AccountsState(repository, this)
            runCurrent()
            assertNotNull(state.storageError)
            repository.failRead = false
            state.reload()
            runCurrent()
            assertEquals(listOf(repository.account), state.accounts)
            assertNull(state.storageError)
        }

    @Test
    fun failedRemovalKeepsAccountsAndDoesNotRefreshInboxUntilRetrySucceeds() =
        runTest {
            val repository = AccountStateRepository()
            var changes = 0
            val state = AccountsState(repository, this) { changes++ }
            runCurrent()
            repository.failRemove = true
            state.remove("a")
            runCurrent()
            assertEquals(listOf(repository.account), state.accounts)
            assertNotNull(state.storageError)
            assertEquals(0, changes)
            repository.failRemove = false
            state.remove("a")
            runCurrent()
            assertEquals(emptyList(), state.accounts)
            assertNull(state.storageError)
            assertEquals(1, changes)
        }

    @Test
    fun failedReloadPreservesPreviouslyLoadedAccounts() =
        runTest {
            val repository = AccountStateRepository()
            val state = AccountsState(repository, this)
            runCurrent()
            repository.failRead = true
            state.reload()
            runCurrent()
            assertEquals(listOf(repository.account), state.accounts)
            assertNotNull(state.storageError)
        }
}

private class AccountStateRepository : AccountRepository {
    val account = ConnectedAccount("a", "Ada", "ada", "example.org")
    var failRead = false
    var failRemove = false
    private var saved = listOf(account)

    override suspend fun accounts(): List<ConnectedAccount> {
        if (failRead) throw AccountStorageException(IllegalStateException("read failed"))
        return saved
    }

    override suspend fun connect(draft: JmapAccountDraft): ConnectOutcome = ConnectOutcome.Connected(account)

    override suspend fun remove(id: String) {
        if (failRemove) throw AccountStorageException(IllegalStateException("remove failed"))
        saved = emptyList()
    }
}
