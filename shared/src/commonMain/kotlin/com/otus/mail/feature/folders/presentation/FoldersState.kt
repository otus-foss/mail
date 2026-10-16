package com.otus.mail.feature.folders.presentation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.otus.mail.feature.folders.domain.AccountFolders
import com.otus.mail.feature.folders.domain.FolderOutcome
import com.otus.mail.feature.folders.domain.FolderRepository
import com.otus.mail.feature.folders.domain.FolderStorageException
import com.otus.mail.feature.folders.domain.MailFolder
import com.otus.mail.feature.folders.domain.placed
import com.otus.mail.feature.folders.domain.placement
import com.otus.mail.feature.folders.domain.scopeOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Every account's folders, and the changes the user makes to them. One change runs at a time.
 * [loadAccounts] loads the list; app wiring has it find each account's Read later folder too.
 * [onDeleted] runs once the server has deleted a folder.
 */
class FoldersState(
    private val repository: FolderRepository,
    private val scope: CoroutineScope,
    private val onDeleted: (MailFolder) -> Unit = {},
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

    /** The folder with [folderId] in [accountId]'s own folders, or in the shared account [sharedIn]. */
    fun folder(
        accountId: String,
        folderId: String,
        sharedIn: String? = null,
    ): MailFolder? {
        val account = account(accountId)
        val shared = account?.shared?.firstOrNull { it.jmapAccountId == sharedIn }
        return if (sharedIn == null) account?.folder(folderId) else shared?.folders?.firstOrNull { it.id == folderId }
    }

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

    /**
     * Moves [folder] to [index] among the folders it can be placed between under [parentId], last by
     * default, and shows it at once. A refused save puts the list back and says why. Nothing
     * happens while another change saves or when the server would refuse the move.
     */
    fun place(
        folder: MailFolder,
        parentId: String?,
        index: Int = Int.MAX_VALUE,
    ) {
        if (busy) return
        val placement = account(folder.accountId)?.scopeOf(folder)?.placement(folder, parentId, index) ?: return
        val before = accounts
        // A load already running read the old order; it must not undo the one shown now.
        loads.invalidate()
        loading = false
        val placed = accounts.map { if (it.accountId == folder.accountId) it.placed(placement) else it }
        accounts = placed
        change(onFailed = { if (accounts === placed) accounts = before }) { repository.place(placement) }
    }

    fun delete(folder: MailFolder) = change({ onDeleted(folder) }) { repository.delete(folder) }

    fun dismissNotice() {
        notice = null
    }

    private fun change(
        onDone: (String) -> Unit = {},
        onFailed: () -> Unit = {},
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
                        onFailed()
                        notice = outcome.reason.message()
                        // A missing folder means the list is stale.
                        refresh()
                        loads.awaitLatest()
                    }
                }
            } catch (expected: FolderStorageException) {
                onFailed()
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
