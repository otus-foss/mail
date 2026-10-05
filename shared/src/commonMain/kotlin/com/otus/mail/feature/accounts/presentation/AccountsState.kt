package com.otus.mail.feature.accounts.presentation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.otus.mail.feature.accounts.domain.AccountRepository
import com.otus.mail.feature.accounts.domain.AccountStorageException
import com.otus.mail.feature.accounts.domain.ConnectFailure
import com.otus.mail.feature.accounts.domain.ConnectOutcome
import com.otus.mail.feature.accounts.domain.ConnectedAccount
import com.otus.mail.feature.accounts.domain.JmapAccountDraft
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

sealed interface ConnectStatus {
    data object Idle : ConnectStatus

    data object Connecting : ConnectStatus

    data class Connected(
        val name: String,
    ) : ConnectStatus

    data class Failed(
        val reason: ConnectFailure,
    ) : ConnectStatus

    /** Sign-in would go to [host]; the user decides whether to trust it. */
    data class ConfirmHost(
        val host: String,
    ) : ConnectStatus
}

/** Screen state for connected accounts. [onChange] runs after an account is added or removed. */
class AccountsState(
    private val repository: AccountRepository,
    private val scope: CoroutineScope,
    private val onChange: () -> Unit = {},
) {
    var accounts by mutableStateOf(emptyList<ConnectedAccount>())
        private set
    var status by mutableStateOf<ConnectStatus>(ConnectStatus.Idle)
        private set

    var storageError by mutableStateOf<String?>(null)
        private set

    /** Held in memory only while the user decides about another host. */
    private var pending: Pair<JmapAccountDraft, String>? = null

    init {
        reload()
    }

    fun connect(draft: JmapAccountDraft) {
        if (status == ConnectStatus.Connecting) return
        status = ConnectStatus.Connecting
        pending = null
        scope.launch {
            val outcome = repository.connect(draft)
            status =
                when (outcome) {
                    is ConnectOutcome.Connected -> {
                        ConnectStatus.Connected(outcome.account.name)
                    }

                    is ConnectOutcome.Failed -> {
                        ConnectStatus.Failed(outcome.reason)
                    }

                    is ConnectOutcome.ConfirmHost -> {
                        pending = draft to outcome.origin
                        ConnectStatus.ConfirmHost(outcome.host)
                    }
                }
            if (outcome is ConnectOutcome.Connected) {
                onChange()
                loadAccounts()
            }
        }
    }

    /** Retries the pending sign-in, trusting only the host the user just approved. */
    fun trustHost() {
        val (draft, origin) = pending ?: return
        connect(draft.copy(trustedOrigins = draft.trustedOrigins + origin))
    }

    fun cancelHost() {
        pending = null
        status = ConnectStatus.Idle
    }

    fun reload() {
        scope.launch { loadAccounts() }
    }

    private suspend fun loadAccounts() {
        try {
            accounts = repository.accounts()
            storageError = null
        } catch (expected: AccountStorageException) {
            storageError = "Couldn't load saved accounts. Try again."
        }
    }

    fun remove(id: String) {
        scope.launch {
            try {
                repository.remove(id)
                onChange()
                loadAccounts()
            } catch (expected: AccountStorageException) {
                storageError = "Couldn't remove the account. Try removing it again."
            }
        }
    }
}
