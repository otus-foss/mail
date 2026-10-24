package com.otus.mail.feature.notifications.data

/** Swift owns APNs, the shared keychain and CryptoKit. No mail credentials enter the extension. */
interface ApplePushPlatform {
    fun newIdentifier(): String

    fun requestRegistration()

    fun stopRegistration()

    fun read(key: String): String?

    fun write(
        key: String,
        value: String?,
    ): Boolean

    fun createKey(id: String): String?

    fun decrypt(
        id: String,
        payload: String,
    ): String?

    fun removeKey(id: String)

    fun showNewMail()
}
