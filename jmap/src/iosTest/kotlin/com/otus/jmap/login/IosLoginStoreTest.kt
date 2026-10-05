package com.otus.jmap.login

import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.pointed
import kotlinx.cinterop.value
import platform.CoreFoundation.CFDictionaryGetValue
import platform.CoreFoundation.CFDictionaryRef
import platform.CoreFoundation.CFRetain
import platform.CoreFoundation.CFTypeRefVar
import platform.Foundation.CFBridgingRelease
import platform.Foundation.CFBridgingRetain
import platform.Foundation.NSData
import platform.Foundation.NSUserDefaults
import platform.Security.errSecAuthFailed
import platform.Security.errSecItemNotFound
import platform.Security.errSecSuccess
import platform.Security.kSecValueData
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs

@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
class IosLoginStoreTest {
    private val suite = "com.otus.jmap.login.tests"
    private val defaults = NSUserDefaults(suiteName = suite).also { it.removePersistentDomainForName(suite) }
    private val native = FakeKeychainAccess()
    private val store = IosLoginStore(defaults, "accounts", "tests", native)
    private val login = JmapLogin("a", "https://example.org/session", "ada", "Ada")

    @AfterTest
    fun cleanUp() {
        defaults.removePersistentDomainForName(suite)
    }

    @Test
    fun replacingCredentialsUpdatesWithoutDeletingOrAdding() {
        store.save(login, "old")
        native.calls.clear()
        store.save(login.copy(name = "New name"), "new")
        assertEquals(listOf("update"), native.calls)
        assertEquals("New name", store.logins().single().name)
        assertEquals("new", assertIs<LoginSecret.Found>(store.secret(login.id)).value)
    }

    @Test
    fun failedUpdatePreservesMetadataAndDoesNotDeleteTheSecret() {
        store.save(login, "old")
        native.calls.clear()
        native.updateStatus = errSecAuthFailed
        assertFailsWith<LoginStoreException> { store.save(login.copy(name = "New name"), "new") }
        assertEquals(listOf("update"), native.calls)
        assertEquals(listOf(login), store.logins())
        assertEquals("old", assertIs<LoginSecret.Found>(store.secret(login.id)).value)
    }

    @Test
    fun missingItemIsAddedAndFailedAddDoesNotPublishLogin() {
        native.updateStatus = errSecItemNotFound
        native.addStatus = errSecAuthFailed
        assertFailsWith<LoginStoreException> { store.save(login, "secret") }
        assertEquals(listOf("update", "add"), native.calls)
        assertEquals(emptyList(), store.logins())
        native.addStatus = errSecSuccess
        store.save(login, "secret")
        assertEquals(listOf(login), store.logins())
    }

    @Test
    fun deletionFailureKeepsLoginAndRetryRemovesIt() {
        store.save(login, "secret")
        native.deleteStatus = errSecAuthFailed
        assertFailsWith<LoginStoreException> { store.remove(login.id) }
        assertEquals(listOf(login), store.logins())
        assertEquals("secret", assertIs<LoginSecret.Found>(store.secret(login.id)).value)
        native.deleteStatus = errSecSuccess
        store.remove(login.id)
        assertEquals(emptyList(), store.logins())
    }

    @Test
    fun missingSecretDoesNotPreventRemoval() {
        store.save(login, "secret")
        native.deleteStatus = errSecItemNotFound
        store.remove(login.id)
        assertEquals(emptyList(), store.logins())
    }
}

@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
private class FakeKeychainAccess : KeychainAccess {
    val calls = mutableListOf<String>()
    var updateStatus = errSecSuccess
    var addStatus = errSecSuccess
    var deleteStatus = errSecSuccess
    private var data: NSData? = null

    override fun read(
        query: CFDictionaryRef?,
        result: CPointer<CFTypeRefVar>?,
    ): Int {
        val stored = data ?: return errSecItemNotFound
        result?.pointed?.value = CFBridgingRetain(stored)
        return errSecSuccess
    }

    override fun update(
        query: CFDictionaryRef?,
        attributes: CFDictionaryRef?,
    ): Int {
        calls += "update"
        return when {
            updateStatus != errSecSuccess -> {
                updateStatus
            }

            data == null -> {
                errSecItemNotFound
            }

            else -> {
                capture(attributes)
                errSecSuccess
            }
        }
    }

    override fun add(attributes: CFDictionaryRef?): Int {
        calls += "add"
        if (addStatus == errSecSuccess) capture(attributes)
        return addStatus
    }

    private fun capture(attributes: CFDictionaryRef?) {
        data = CFBridgingRelease(CFRetain(CFDictionaryGetValue(attributes, kSecValueData))) as NSData
    }

    override fun delete(query: CFDictionaryRef?): Int {
        calls += "delete"
        if (deleteStatus == errSecSuccess) data = null
        return deleteStatus
    }
}
