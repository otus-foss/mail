package com.otus.jmap.login

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.GeneralSecurityException
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** AES-256-GCM with a non-exportable Android Keystore key. Each value gets a fresh provider-chosen IV. */
internal class KeystoreCipher(
    private val keyAlias: String,
) : LoginCipher {
    @Volatile private var key: SecretKey? = null

    /** Throws [GeneralSecurityException] when the Keystore refuses. */
    override fun seal(value: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.ENCRYPT_MODE, key()) }
        val sealed = cipher.doFinal(value.encodeToByteArray())
        return encode(cipher.iv) + SEPARATOR + encode(sealed)
    }

    override fun open(value: String): LoginSecret {
        val parts = value.split(SEPARATOR)
        if (parts.size != 2) return LoginSecret.Unreadable()
        return try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(TAG_BITS, decode(parts[0])))
            LoginSecret.Found(cipher.doFinal(decode(parts[1])).decodeToString())
        } catch (error: GeneralSecurityException) {
            LoginSecret.Unreadable(error)
        } catch (error: IllegalArgumentException) {
            // Malformed Base64 in storage.
            LoginSecret.Unreadable(error)
        }
    }

    private fun key(): SecretKey =
        key ?: run {
            val keyStore = KeyStore.getInstance(KEYSTORE).apply { load(null) }
            (keyStore.getEntry(keyAlias, null) as? KeyStore.SecretKeyEntry)?.secretKey ?: generateKey()
        }.also { key = it }

    private fun generateKey(): SecretKey {
        val spec =
            KeyGenParameterSpec
                .Builder(keyAlias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(KEY_BITS)
                .build()
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE).apply { init(spec) }.generateKey()
    }

    private fun encode(bytes: ByteArray) = Base64.encodeToString(bytes, Base64.NO_WRAP)

    private fun decode(text: String) = Base64.decode(text, Base64.NO_WRAP)

    private companion object {
        const val KEYSTORE = "AndroidKeyStore"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val TAG_BITS = 128
        const val KEY_BITS = 256
        const val SEPARATOR = ":"
    }
}

/** Encryption boundary for platform storage tests. Production uses the Android Keystore. */
internal interface LoginCipher {
    fun seal(value: String): String

    fun open(value: String): LoginSecret
}
