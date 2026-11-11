package com.otusfoss.mail.app

import com.otusfoss.core.accounts.domain.AccountStorageException
import com.otusfoss.core.jmap.login.InMemoryLoginStore
import com.otusfoss.core.jmap.login.JmapLogin
import com.otusfoss.core.jmap.login.LoginStore
import com.otusfoss.core.jmap.login.LoginStoreException
import com.otusfoss.mail.core.cache.CacheKind
import com.otusfoss.mail.core.cache.CacheStorageException
import com.otusfoss.mail.core.cache.InMemoryMailCache
import com.otusfoss.mail.core.cache.MailCache
import com.otusfoss.mail.core.cache.withAccount
import com.otusfoss.mail.core.files.DownloadFolder
import com.otusfoss.mail.core.files.DownloadFolderException
import com.otusfoss.mail.core.files.InMemoryDownloadFolder
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class AccountRemovalRecoveryTest {
    private val login = JmapLogin("a", "https://mail.example.org/jmap", "ada@example.org", "Ada")
    private val saved = InMemoryLoginStore().apply { save(login, "secret") }

    @Test
    fun failedCredentialDeletionRestoresNewWorkButRejectsOldWrites() =
        runTest {
            val store =
                object : LoginStore by saved {
                    override fun remove(id: String) = throw LoginStoreException("Deletion failed")
                }
            val services = MailServices(store)
            val started = CompletableDeferred<Unit>()
            val release = CompletableDeferred<Unit>()
            val request =
                launch {
                    services.cache.withAccount(login.id) { scoped ->
                        withContext(NonCancellable) {
                            started.complete(Unit)
                            release.await()
                            assertFailsWith<CancellationException> {
                                scoped.write(login.id) { put(CacheKind.Email, "old", "stale") }
                            }
                        }
                    }
                }
            started.await()
            assertFailsWith<AccountStorageException> { services.accounts.remove(login.id) }
            assertEquals(listOf(login), saved.logins())
            services.cache.withAccount(login.id) { it.write(login.id) { put(CacheKind.Email, "new", "fresh") } }
            release.complete(Unit)
            request.join()
            assertTrue(request.isCancelled)
            assertEquals(mapOf("new" to "fresh"), services.cache.readAll(login.id, CacheKind.Email))
        }

    @Test
    fun cancellationAfterCacheCleanupLeavesTheRetainedAccountUsable() =
        runTest {
            lateinit var removal: Job
            val memory = InMemoryMailCache()
            val cache =
                object : MailCache by memory {
                    override suspend fun forget(loginId: String) {
                        memory.forget(loginId)
                        removal.cancel()
                    }
                }
            val services = MailServices(saved, stores = MailDeviceStores(cache = cache))
            removal = launch { services.accounts.remove(login.id) }
            removal.join()
            assertTrue(removal.isCancelled)
            assertEquals(listOf(login), saved.logins())
            services.cache.withAccount(login.id) { it.write(login.id) { putValue("state", "new") } }
            assertEquals("new", services.cache.value(login.id, "state"))
        }

    @Test
    fun failedCacheCleanupDoesNotDisableTheRetainedAccount() =
        runTest {
            val memory = InMemoryMailCache()
            val cache =
                object : MailCache by memory {
                    override suspend fun forget(loginId: String) = error("Storage unavailable")
                }
            val services = MailServices(saved, stores = MailDeviceStores(cache = cache))
            assertFailsWith<IllegalStateException> { services.accounts.remove(login.id) }
            assertEquals(listOf(login), saved.logins())
            services.cache.withAccount(login.id) { it.write(login.id) { putValue("state", "new") } }
            assertEquals("new", services.cache.value(login.id, "state"))
        }

    @Test
    fun aCacheStorageFailureDuringRemovalIsAnAccountStorageError() =
        runTest {
            val cache =
                object : MailCache by InMemoryMailCache() {
                    override suspend fun forget(loginId: String) =
                        throw CacheStorageException(IllegalStateException("disk I/O"))
                }
            val services = MailServices(saved, stores = MailDeviceStores(cache = cache))
            val failure = assertFailsWith<AccountStorageException> { services.accounts.remove(login.id) }
            assertTrue(generateSequence<Throwable>(failure) { it.cause }.any { it is CacheStorageException })
            assertEquals(listOf(login), saved.logins())
        }

    @Test
    fun anAttachmentFolderFailureDuringRemovalIsAnAccountStorageError() =
        runTest {
            val downloads =
                object : DownloadFolder by InMemoryDownloadFolder() {
                    override suspend fun deleteGroup(group: String) = throw DownloadFolderException("disk I/O")
                }
            val services = MailServices(saved, stores = MailDeviceStores(downloads = downloads))
            val failure = assertFailsWith<AccountStorageException> { services.accounts.remove(login.id) }
            assertTrue(generateSequence<Throwable>(failure) { it.cause }.any { it is DownloadFolderException })
            assertEquals(listOf(login), saved.logins())
        }

    @Test
    fun cancellationAfterCredentialDeletionDoesNotReactivateTheRemovedAccount() =
        runTest {
            lateinit var removal: Job
            val store =
                object : LoginStore by saved {
                    override fun remove(id: String) {
                        saved.remove(id)
                        removal.cancel()
                    }
                }
            val services = MailServices(store)
            removal = launch { services.accounts.remove(login.id) }
            removal.join()
            assertTrue(removal.isCancelled)
            assertTrue(saved.logins().isEmpty())
            assertFailsWith<CancellationException> { services.cache.withAccount(login.id) { error("Removed") } }
        }

    @Test
    fun unavailableCredentialsDoNotMaskTheRemovalFailureOrRestoreAccess() =
        runTest {
            val store =
                object : LoginStore by saved {
                    override fun remove(id: String) = throw LoginStoreException("Deletion failed")

                    override fun logins(): List<JmapLogin> = throw LoginStoreException("Read failed")
                }
            val services = MailServices(store)
            val failure = assertFailsWith<AccountStorageException> { services.accounts.remove(login.id) }
            assertEquals("Deletion failed", generateSequence<Throwable>(failure) { it.cause }.last().message)
            assertFailsWith<CancellationException> { services.cache.withAccount(login.id) { error("Unknown") } }
        }

    @Test
    fun successfulRemovalKeepsTheAccountInactive() =
        runTest {
            val services = MailServices(saved)
            services.accounts.remove(login.id)
            assertTrue(saved.logins().isEmpty())
            assertFailsWith<CancellationException> { services.cache.withAccount(login.id) { error("Removed") } }
        }
}
