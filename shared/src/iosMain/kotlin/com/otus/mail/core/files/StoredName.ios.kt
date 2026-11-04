package com.otus.mail.core.files

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.usePinned
import platform.CoreCrypto.CC_SHA256
import platform.CoreCrypto.CC_SHA256_DIGEST_LENGTH

@OptIn(ExperimentalForeignApi::class)
actual fun storedName(value: String): String {
    val bytes = value.encodeToByteArray()
    val digest = ByteArray(CC_SHA256_DIGEST_LENGTH)
    bytes.usePinned { input ->
        digest.usePinned { output ->
            CC_SHA256(
                if (bytes.isEmpty()) null else input.addressOf(0),
                bytes.size.toUInt(),
                output.addressOf(0).reinterpret(),
            )
        }
    }
    return digest.toHexString()
}
