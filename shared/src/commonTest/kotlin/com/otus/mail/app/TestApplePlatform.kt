package com.otus.mail.app

import com.otus.mail.feature.notifications.data.ApplePushPlatform

internal class TestApplePlatform : ApplePushPlatform {
    val saved = mutableMapOf("token" to "device-token")
    var shown = 0
    var removed = false
    var stopped = false

    override fun createIdentifier() = "12345678-1234-1234-1234-123456789012"

    override fun requestRegistration() = Unit

    override fun stopRegistration() {
        stopped = true
    }

    override fun read(key: String) = saved[key]

    override fun write(
        key: String,
        value: String?,
    ): Boolean {
        if (value == null) saved.remove(key) else saved[key] = value
        return true
    }

    override fun createKey(id: String) = """{"p256dh":"public-key","auth":"auth-secret"}"""

    override fun decrypt(
        id: String,
        payload: String,
    ) = """{"@type":"PushVerification","pushSubscriptionId":"ps1","verificationCode":"verified"}"""

    override fun removeKey(id: String) {
        removed = true
    }

    override fun showNewMail() {
        shown++
    }
}
