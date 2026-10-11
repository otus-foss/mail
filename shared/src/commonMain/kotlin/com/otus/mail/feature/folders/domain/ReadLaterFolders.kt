package com.otus.mail.feature.folders.domain

import kotlinx.coroutines.NonCancellable
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
 * Each account's Read later folder while [enabled] says so. It is one of the user's own folders,
 * because JMAP has no role for it (RFC 8621, section 2), so other mail apps show it like any other.
 * Its id is saved per login, so renaming it keeps it. Without a saved id, as on a new device, a
 * top-level folder named [READ_LATER_NAME] is used; an account without one gets it created.
 */
class ReadLaterFolders(
    private val repository: FolderRepository,
    private val store: ReadLaterStore? = null,
    private val enabled: () -> Boolean,
) {
    // Overlapping refreshes must not create the folder twice.
    private val lock = Mutex()

    /** Every account's folders, with [AccountFolders.readLaterId] set while Read later is on. */
    suspend fun accounts(): List<AccountFolders> {
        if (!enabled()) return repository.accounts()
        return lock.withLock {
            val saved = store?.read().orEmpty()
            val loaded = repository.accounts()
            val missing =
                loaded.filter {
                    it.failure == null && it.limits.mayCreateTopLevel &&
                        it.readLaterIn(saved) == null
                }
            // A creation the server may already have received finishes even if its refresh is replaced.
            val created =
                withContext(NonCancellable) {
                    missing.map { repository.create(it.accountId, READ_LATER_NAME, parentId = null) }
                }
            val accounts = if (created.any { it is FolderOutcome.Done }) repository.accounts() else loaded
            accounts.map { it.copy(readLaterId = it.readLaterIn(saved)?.id) }.also { save(saved, it) }
        }
    }

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

/** The saved folder, or else a top-level folder of the user's named Read later, in any case. */
private fun AccountFolders.readLaterIn(saved: Map<String, String>): MailFolder? =
    saved[accountId]?.let(::folder)
        ?: folders.firstOrNull {
            it.isCustom && it.parentId == null &&
                it.name.equals(READ_LATER_NAME, ignoreCase = true)
        }
