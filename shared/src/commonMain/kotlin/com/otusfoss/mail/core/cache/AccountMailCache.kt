package com.otusfoss.mail.core.cache

import com.otusfoss.core.jmap.login.LoginLifecycle
import com.otusfoss.core.jmap.login.LoginLifecycleException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Owns account cache generations. Removal cancels current readers and rejects their late writes,
 * including writes from code that finishes in NonCancellable after the same login reconnects.
 */
internal class AccountMailCache(
    private val storage: MailCache,
) : MailCache by storage,
    LoginLifecycle {
    private val lock = Mutex()
    private val accounts = mutableMapOf<String, Account>()

    internal suspend fun <T> inAccount(
        loginId: String,
        operation: suspend (MailCache) -> T,
    ): T {
        val account = account(loginId)
        val job = Job(currentCoroutineContext()[Job])
        try {
            val generation =
                account.lock.withLock {
                    account.requireActive()
                    account.jobs += job
                    account.generation
                }
            return withContext(job) {
                val cache = ScopedCache(loginId, account, generation)
                val result = operation(cache)
                account.lock.withLock { account.requireActive(generation) }
                result
            }
        } finally {
            withContext(NonCancellable) {
                account.lock.withLock { account.jobs -= job }
                job.complete()
            }
        }
    }

    override suspend fun forget(loginId: String) = invalidate(loginId) { storage.forget(loginId) }

    /**
     * Replacing credentials starts a new generation even when the login ID is unchanged. The login ID
     * names the same user on the same server, so the cached mail stays and nothing touches storage.
     */
    override suspend fun connected(loginId: String) = invalidate(loginId, activeAfter = true)

    override suspend fun removing(loginId: String) {
        try {
            forget(loginId)
        } catch (error: CacheStorageException) {
            throw LoginLifecycleException(error)
        }
    }

    /** A failed removal keeps the login, but requests from before removal remain invalid. */
    override suspend fun removeFailed(loginId: String) {
        val account = account(loginId)
        account.lock.withLock { account.active = true }
    }

    /**
     * Starts a new generation, which is active only with [activeAfter], and runs [then] before any
     * later write.
     */
    private suspend fun invalidate(
        loginId: String,
        activeAfter: Boolean = false,
        then: suspend (Account) -> Unit = {},
    ) {
        withContext(NonCancellable) {
            val account = account(loginId)
            account.writes.withLock {
                account.lock.withLock {
                    account.generation += 1
                    account.active = activeAfter
                    account.jobs.forEach { it.cancel(CancellationException("Account changed")) }
                    account.jobs.clear()
                }
                then(account)
            }
        }
    }

    private suspend fun account(loginId: String): Account = lock.withLock { accounts.getOrPut(loginId) { Account() } }

    /**
     * [lock] guards only the fields, for a moment; [writes] is held while a write or an account
     * change reaches the storage, which a read does not wait for.
     */
    private class Account {
        val lock = Mutex()
        val writes = Mutex()
        val jobs = mutableSetOf<Job>()
        var generation = 0L
        var active = true

        fun requireActive(expected: Long = generation) {
            if (!active || expected != generation) throw CancellationException("Account changed")
        }
    }

    private inner class ScopedCache(
        private val loginId: String,
        private val account: Account,
        private val generation: Long,
    ) : MailCache by storage {
        override suspend fun read(
            loginId: String,
            kind: CacheKind,
            ids: Collection<String>,
        ): Map<String, String> = reading(loginId) { storage.read(loginId, kind, ids) }

        override suspend fun readAll(
            loginId: String,
            kind: CacheKind,
        ): Map<String, String> = reading(loginId) { storage.readAll(loginId, kind) }

        override suspend fun value(
            loginId: String,
            key: String,
        ): String? = reading(loginId) { storage.value(loginId, key) }

        override suspend fun write(
            loginId: String,
            changes: CacheWrite.() -> Unit,
        ) = writing(loginId) { storage.write(loginId, changes) }

        override suspend fun forget(loginId: String) {
            require(loginId == this.loginId)
            this@AccountMailCache.forget(loginId)
        }

        /**
         * A read checks the account and then runs without a lock, so it never waits for a write
         * to finish. One that an account change overtakes is dropped when its operation ends.
         */
        private suspend fun <T> reading(
            loginId: String,
            operation: suspend () -> T,
        ): T {
            requireCurrent(loginId)
            return operation()
        }

        /** A write holds the account's writes, so none lands after the account changed. */
        private suspend fun <T> writing(
            loginId: String,
            operation: suspend () -> T,
        ): T =
            account.writes.withLock {
                requireCurrent(loginId)
                operation()
            }

        private suspend fun requireCurrent(loginId: String) {
            require(loginId == this.loginId)
            account.lock.withLock { account.requireActive(generation) }
            currentCoroutineContext().ensureActive()
        }
    }
}

/** Bind before starting network work, and use the supplied cache for every read and write. */
internal suspend fun <T> MailCache.withAccount(
    loginId: String,
    operation: suspend (MailCache) -> T,
): T = if (this is AccountMailCache) inAccount(loginId, operation) else operation(this)
