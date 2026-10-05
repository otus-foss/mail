package com.otus.jmap.login

import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.value
import platform.CoreFoundation.CFDictionaryAddValue
import platform.CoreFoundation.CFDictionaryCreateMutable
import platform.CoreFoundation.CFMutableDictionaryRef
import platform.CoreFoundation.CFRelease
import platform.CoreFoundation.CFTypeRef
import platform.CoreFoundation.CFTypeRefVar
import platform.CoreFoundation.kCFBooleanTrue
import platform.CoreFoundation.kCFTypeDictionaryKeyCallBacks
import platform.CoreFoundation.kCFTypeDictionaryValueCallBacks
import platform.Foundation.CFBridgingRelease
import platform.Foundation.CFBridgingRetain
import platform.Foundation.NSData
import platform.Foundation.NSLock
import platform.Foundation.NSString
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.NSUserDefaults
import platform.Foundation.create
import platform.Foundation.dataUsingEncoding
import platform.Security.errSecItemNotFound
import platform.Security.errSecSuccess
import platform.Security.kSecAttrAccessible
import platform.Security.kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly
import platform.Security.kSecAttrAccount
import platform.Security.kSecAttrService
import platform.Security.kSecClass
import platform.Security.kSecClassGenericPassword
import platform.Security.kSecMatchLimit
import platform.Security.kSecMatchLimitOne
import platform.Security.kSecReturnData
import platform.Security.kSecValueData

/** Logins in user defaults; secrets in the Keychain, only on this device and after first unlock. */
@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
public class IosLoginStore internal constructor(
    private val defaults: NSUserDefaults,
    private val listKey: String,
    private val service: String,
    private val keychain: KeychainAccess,
) : LoginStore {
    public constructor(
        defaults: NSUserDefaults = NSUserDefaults.standardUserDefaults,
        listKey: String = "otus.logins",
        service: String = "otus.login",
    ) : this(defaults, listKey, service, SystemKeychainAccess)

    private val lock = NSLock()

    override fun logins(): List<JmapLogin> = locked { readLogins() }

    override fun secret(id: String): LoginSecret =
        withQuery(id, kSecReturnData to kCFBooleanTrue, kSecMatchLimit to kSecMatchLimitOne) { query ->
            memScoped {
                val result = alloc<CFTypeRefVar>()
                when (keychain.read(query, result.ptr)) {
                    errSecSuccess -> {
                        val data = CFBridgingRelease(result.value) as? NSData
                        data
                            ?.let {
                                NSString
                                    .create(
                                        it,
                                        NSUTF8StringEncoding,
                                    )?.toString()
                            }?.let { LoginSecret.Found(it) }
                            ?: LoginSecret.Unreadable()
                    }

                    errSecItemNotFound -> {
                        LoginSecret.Missing
                    }

                    else -> {
                        LoginSecret.Unreadable()
                    }
                }
            }
        }

    override fun save(
        login: JmapLogin,
        secret: String,
    ): Unit =
        locked {
            val encoded = LoginCodec.encode(readLogins().filterNot { it.id == login.id } + login)
            val data =
                NSString.create(string = secret).dataUsingEncoding(NSUTF8StringEncoding)
                    ?: throw LoginStoreException("The secret could not be encoded.")
            val retained = CFBridgingRetain(data)
            try {
                writeSecret(login.id, retained)
            } finally {
                CFRelease(retained)
            }
            defaults.setObject(encoded, listKey)
        }

    override fun remove(id: String): Unit =
        locked {
            val encoded = LoginCodec.encode(readLogins().filterNot { it.id == id })
            val status = withQuery(id, block = keychain::delete)
            if (status != errSecSuccess && status != errSecItemNotFound) {
                throw LoginStoreException("The Keychain refused deletion (status $status).")
            }
            defaults.setObject(encoded, listKey)
        }

    private fun writeSecret(
        id: String,
        data: CFTypeRef?,
    ) {
        val value = kSecValueData to data
        val accessibility = kSecAttrAccessible to kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly
        val status =
            withKeychainAttributes(value, accessibility) { attributes ->
                withQuery(id) { keychain.update(it, attributes) }
            }
        val saved =
            if (status == errSecItemNotFound) {
                withQuery(id, value, accessibility, block = keychain::add)
            } else {
                status
            }
        if (saved != errSecSuccess) throw LoginStoreException("The Keychain refused the secret (status $saved).")
    }

    /** An unreadable list is copied aside once, so a later save cannot destroy it. */
    private fun readLogins(): List<JmapLogin> {
        val raw = defaults.stringForKey(listKey)
        return when (val decoded = LoginCodec.decode(raw)) {
            is LoginCodec.Decoded.Logins -> {
                decoded.logins
            }

            is LoginCodec.Decoded.Unreadable -> {
                val backup = listKey + LoginCodec.UNREADABLE_SUFFIX
                if (defaults.stringForKey(backup) == null) defaults.setObject(raw, backup)
                emptyList()
            }
        }
    }

    private fun <T> locked(block: () -> T): T {
        lock.lock()
        return try {
            block()
        } finally {
            lock.unlock()
        }
    }

    private fun <T> withQuery(
        id: String,
        vararg extra: Pair<CFTypeRef?, CFTypeRef?>,
        block: (CFMutableDictionaryRef?) -> T,
    ): T {
        val serviceRef = CFBridgingRetain(service)
        val accountRef = CFBridgingRetain(id)
        val query =
            CFDictionaryCreateMutable(null, 0, kCFTypeDictionaryKeyCallBacks.ptr, kCFTypeDictionaryValueCallBacks.ptr)
        CFDictionaryAddValue(query, kSecClass, kSecClassGenericPassword)
        CFDictionaryAddValue(query, kSecAttrService, serviceRef)
        CFDictionaryAddValue(query, kSecAttrAccount, accountRef)
        extra.forEach { (key, value) -> CFDictionaryAddValue(query, key, value) }
        return try {
            block(query)
        } finally {
            CFRelease(query)
            CFRelease(serviceRef)
            CFRelease(accountRef)
        }
    }
}
