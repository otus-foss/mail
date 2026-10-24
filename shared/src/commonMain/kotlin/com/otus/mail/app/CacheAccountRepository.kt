package com.otus.mail.app

import com.otus.accounts.domain.AccountRepository
import com.otus.jmap.login.LoginStore
import com.otus.jmap.login.LoginStoreException
import com.otus.mail.core.cache.AccountMailCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

/** Restores cache access when removal leaves the account's credentials in place. */
internal class CacheAccountRepository(
    private val accounts: AccountRepository,
    private val logins: LoginStore,
    private val cache: AccountMailCache,
) : AccountRepository by accounts {
    override suspend fun remove(id: String) {
        var completed = false
        try {
            accounts.remove(id)
            completed = true
        } finally {
            if (!completed) {
                withContext(NonCancellable) {
                    if (retained(id)) cache.restore(id)
                }
            }
        }
    }

    private suspend fun retained(id: String): Boolean =
        withContext(Dispatchers.IO) {
            try {
                logins.logins().any { it.id == id }
            } catch (_: LoginStoreException) {
                // Preserve the removal error and keep access blocked while credentials are unknown.
                false
            }
        }
}
