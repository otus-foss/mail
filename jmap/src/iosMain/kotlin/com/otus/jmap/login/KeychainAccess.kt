package com.otus.jmap.login

import kotlinx.cinterop.CPointer
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.ptr
import platform.CoreFoundation.CFDictionaryAddValue
import platform.CoreFoundation.CFDictionaryCreateMutable
import platform.CoreFoundation.CFDictionaryRef
import platform.CoreFoundation.CFRelease
import platform.CoreFoundation.CFTypeRef
import platform.CoreFoundation.CFTypeRefVar
import platform.CoreFoundation.kCFTypeDictionaryKeyCallBacks
import platform.CoreFoundation.kCFTypeDictionaryValueCallBacks
import platform.Security.SecItemAdd
import platform.Security.SecItemCopyMatching
import platform.Security.SecItemDelete
import platform.Security.SecItemUpdate

/** Native boundary, injectable to exercise Keychain failures without changing device security. */
@OptIn(ExperimentalForeignApi::class)
internal interface KeychainAccess {
    fun read(
        query: CFDictionaryRef?,
        result: CPointer<CFTypeRefVar>?,
    ): Int

    fun update(
        query: CFDictionaryRef?,
        attributes: CFDictionaryRef?,
    ): Int

    fun add(attributes: CFDictionaryRef?): Int

    fun delete(query: CFDictionaryRef?): Int
}

@OptIn(ExperimentalForeignApi::class)
internal object SystemKeychainAccess : KeychainAccess {
    override fun read(
        query: CFDictionaryRef?,
        result: CPointer<CFTypeRefVar>?,
    ) = SecItemCopyMatching(query, result)

    override fun update(
        query: CFDictionaryRef?,
        attributes: CFDictionaryRef?,
    ) = SecItemUpdate(query, attributes)

    override fun add(attributes: CFDictionaryRef?) = SecItemAdd(attributes, null)

    override fun delete(query: CFDictionaryRef?) = SecItemDelete(query)
}

@OptIn(ExperimentalForeignApi::class)
internal fun <T> withKeychainAttributes(
    vararg values: Pair<CFTypeRef?, CFTypeRef?>,
    block: (CFDictionaryRef?) -> T,
): T {
    val attributes =
        CFDictionaryCreateMutable(null, 0, kCFTypeDictionaryKeyCallBacks.ptr, kCFTypeDictionaryValueCallBacks.ptr)
    values.forEach { (key, value) -> CFDictionaryAddValue(attributes, key, value) }
    return try {
        block(attributes)
    } finally {
        CFRelease(attributes)
    }
}
