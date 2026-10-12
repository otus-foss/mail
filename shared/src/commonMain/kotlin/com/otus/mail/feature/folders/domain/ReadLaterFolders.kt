package com.otus.mail.feature.folders.domain

import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** The name a new Read later folder gets, and the name an existing one is recognized by. */
const val READ_LATER_NAME = "Read later"

/** App-private record of each account's Read later folder: mailbox ids by login id. */
interface ReadLaterStore {
    fun read(): Map<String, String>

    fun write(folders: Map<String, String>)
}

/**
 * Each account's Read later folder. It is one of the user's own folders, because JMAP has no role
 * for it (RFC 8621, section 2), so other mail apps show it like any other. Its id is saved per
 * login, so renaming or moving it keeps it. Without a usable saved id, as on a new device, a
 * top-level folder named [READ_LATER_NAME] is used; an account without one gets it created.
 *
 * Create one per process. Its lock then also covers the loads of a recreated activity, so
 * overlapping loads cannot create the folder twice.
 */
class ReadLaterFolders(
    private val repository: FolderRepository,
    private val store: ReadLaterStore? = null,
) {
    private val lock = Mutex()

    /**
     * Every account's folders, with [AccountFolders.readLaterId] set while [enabled] says so. Reads
     * [enabled] again at the end, so a load still running when the switch turns off shows no Read
     * later folder.
     */
    suspend fun accounts(enabled: () -> Boolean): List<AccountFolders> {
        if (!enabled()) return repository.accounts()
        val accounts = lock.withLock { withReadLater(repository.accounts()) }
        return if (enabled()) accounts else accounts.map { it.copy(readLaterId = null) }
    }

    /** [loaded] with each account's Read later folder, created where one is missing and allowed. */
    private suspend fun withReadLater(loaded: List<AccountFolders>): List<AccountFolders> {
        val before = saved()
        val missing =
            loaded.filter {
                it.failure == null && it.limits.mayCreateTopLevel && it.readLaterIn(before) == null
            }
        // The server may apply a create even if a newer load replaces this one, so every create
        // finishes and saves its id.
        val created = withContext(NonCancellable) { missing.mapNotNull { create(it.accountId) } }
        // A replaced load stops here and shows nothing; the newer one shows the folders.
        currentCoroutineContext().ensureActive()
        val accounts = if (created.isEmpty()) loaded else repository.accounts()
        val saved = saved()
        val marked = accounts.map { it.copy(readLaterId = it.readLaterIn(saved)?.id) }
        save(saved, marked)
        return marked
    }

    /** Creates the folder and saves its id at once; null when the server refuses. */
    private suspend fun create(accountId: String): String? {
        val outcome = repository.create(accountId, READ_LATER_NAME, parentId = null)
        if (outcome !is FolderOutcome.Done) return null
        store?.write(saved() + (accountId to outcome.folderId))
        return outcome.folderId
    }

    private fun saved(): Map<String, String> = store?.read().orEmpty()

    /** Keeps the ids of accounts that could not load and forgets accounts that were removed. */
    private fun save(
        saved: Map<String, String>,
        accounts: List<AccountFolders>,
    ) {
        val connected = accounts.map { it.accountId }.toSet()
        val found = accounts.mapNotNull { account -> account.readLaterId?.let { account.accountId to it } }
        val next = saved.filterKeys { it in connected } + found
        if (next != saved) store?.write(next)
    }
}

// Mail in Trash or Junk can be purged with it, and Thunderbird, for one, deletes a folder by moving
// it into Trash.
internal val DISCARD_ROLES = setOf(TRASH_ROLE, JUNK_ROLE)

/**
 * The saved folder while it is still one of the user's own and outside Trash and Junk, or else a
 * top-level folder of the user's named Read later, in any case.
 */
private fun AccountFolders.readLaterIn(saved: Map<String, String>): MailFolder? =
    saved[accountId]
        ?.let(::folder)
        ?.takeIf { folder -> folder.isCustom && ancestors(folders, folder).none { it.role in DISCARD_ROLES } }
        ?: folders.firstOrNull {
            it.isCustom && it.parentId == null &&
                it.name.equals(READ_LATER_NAME, ignoreCase = true)
        }
