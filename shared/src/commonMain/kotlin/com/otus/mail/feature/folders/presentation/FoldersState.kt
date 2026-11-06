package com.otus.mail.feature.folders.presentation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.otus.mail.core.mailbox.AccountFolders
import com.otus.mail.core.mailbox.MailFolder
import com.otus.mail.feature.folders.domain.FolderOutcome
import com.otus.mail.feature.folders.domain.FolderPlacement
import com.otus.mail.feature.folders.domain.FolderRepository
import com.otus.mail.feature.folders.domain.FolderStorageException
import com.otus.mail.feature.folders.domain.placed
import com.otus.mail.feature.folders.domain.placement
import com.otus.mail.feature.folders.domain.scopeOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Every account's folders, and the changes the user makes to them. One change runs at a time.
 * [loadAccounts] loads the list; app wiring has it find each account's Read later folder too.
 * While the list is empty, a load first shows [loadCached], the device's copy, so the sidebar and
 * its counts show before the server answers. [onDeleted] runs once the server has deleted a folder.
 */
class FoldersState(
    private val repository: FolderRepository,
    private val scope: CoroutineScope,
    private val onDeleted: (MailFolder) -> Unit = {},
    private val loadCached: suspend () -> List<AccountFolders> = { repository.cachedAccounts() },
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

    /**
     * Whether the folders of [accountId], or of every account without one, have loaded without
     * failing, so a folder they lack is gone. An account that failed may still hold it.
     */
    fun settled(accountId: String? = null): Boolean {
        val loaded = if (accountId == null) accounts else listOfNotNull(account(accountId))
        return !loading && loaded.isNotEmpty() && loaded.none { it.failure != null }
    }

    /** The folder with [folderId] in [accountId]'s own folders, or in the shared account [sharedIn]. */
    fun folder(
        accountId: String,
        folderId: String,
        sharedIn: String? = null,
    ): MailFolder? {
        val account = account(accountId)
        return if (sharedIn == null) {
            account?.folder(folderId)
        } else {
            account
                ?.shared
                ?.firstOrNull { it.jmapAccountId == sharedIn }
                ?.folders
                ?.firstOrNull { it.id == folderId }
        }
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
        val placement = placement(folder, parentId, index) ?: return
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
                val outcome = action()
                if (outcome is FolderOutcome.Failed) {
                    onFailed()
                    notice = outcome.reason.message()
                }
                // Wait for the newest load, including refreshes started during this change.
                refresh()
                loads.awaitLatest()
                if (outcome is FolderOutcome.Done) onDone(outcome.folderId)
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
            // The device's copy shows while nothing does yet; loading stays on for the server's.
            if (accounts.isEmpty()) {
                val cached = loadCached()
                if (isLatest() && accounts.isEmpty() && cached.any { it.folders.isNotEmpty() }) accounts = cached
            }
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

/** Where [FoldersState.place] would put [folder], or null when it would do nothing. */
fun FoldersState.placement(
    folder: MailFolder,
    parentId: String?,
    index: Int = Int.MAX_VALUE,
): FolderPlacement? = account(folder.accountId)?.scopeOf(folder)?.placement(folder, parentId, index)
