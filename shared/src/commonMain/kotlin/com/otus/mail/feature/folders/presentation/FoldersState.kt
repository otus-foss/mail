package com.otus.mail.feature.folders.presentation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.otus.mail.feature.folders.domain.AccountFolders
import com.otus.mail.feature.folders.domain.FolderOutcome
import com.otus.mail.feature.folders.domain.FolderRepository
import com.otus.mail.feature.folders.domain.FolderStorageException
import com.otus.mail.feature.folders.domain.MailFolder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Every account's folders, and the changes the user makes to them. One change runs at a time.
 * [loadAccounts] loads the list; app wiring has it find each account's Read later folder too.
 */
class FoldersState(
    private val repository: FolderRepository,
    private val scope: CoroutineScope,
    private val loadAccounts: suspend () -> List<AccountFolders>,
) {
    var accounts by mutableStateOf(emptyList<AccountFolders>())
        private set
    var loading by mutableStateOf(false)
        private set

    /** Why the last change or load failed, worded for the user; null when it worked. */
    var notice by mutableStateOf<String?>(null)
        private set
    var busy by mutableStateOf(false)
        private set
    private val loads = LatestLoad(scope)

    fun account(accountId: String): AccountFolders? = accounts.firstOrNull { it.accountId == accountId }

    fun folder(
        accountId: String,
        folderId: String,
    ): MailFolder? = account(accountId)?.folder(folderId)

    fun refresh() {
        loads.start(::load)
    }

    /** [onCreated] receives the new folder's id once the list includes it. */
    fun create(
        accountId: String,
        name: String,
        parentId: String?,
        onCreated: (String) -> Unit = {},
    ) = change(onCreated) { repository.create(accountId, name, parentId) }

    fun rename(
        folder: MailFolder,
        name: String,
    ) = change { repository.rename(folder, name) }

    fun move(
        folder: MailFolder,
        parentId: String?,
    ) = change { repository.move(folder, parentId) }

    fun delete(folder: MailFolder) = change { repository.delete(folder) }

    fun dismissNotice() {
        notice = null
    }

    private fun change(
        onDone: (String) -> Unit = {},
        action: suspend () -> FolderOutcome,
    ) {
        if (busy) return
        busy = true
        notice = null
        scope.launch {
            try {
                when (val outcome = action()) {
                    is FolderOutcome.Done -> {
                        // Waits for the newest load, even a later refresh's, so the list has the change.
                        refresh()
                        loads.awaitLatest()
                        onDone(outcome.folderId)
                    }

                    is FolderOutcome.Failed -> {
                        notice = outcome.reason.message()
                        // A missing folder means the list is stale.
                        refresh()
                        loads.awaitLatest()
                    }
                }
            } catch (expected: FolderStorageException) {
                notice = STORAGE_NOTICE
            } finally {
                busy = false
            }
        }
    }

    private suspend fun load(isLatest: () -> Boolean) {
        loading = true
        try {
            val loaded = loadAccounts()
            if (isLatest()) accounts = loaded
        } catch (expected: FolderStorageException) {
            if (isLatest()) notice = STORAGE_NOTICE
        } finally {
            // A replaced load can finish after the newer one started; only the newest ends loading.
            if (isLatest()) loading = false
        }
    }
}

internal const val STORAGE_NOTICE = "Couldn't load saved accounts. Try again in Settings."
