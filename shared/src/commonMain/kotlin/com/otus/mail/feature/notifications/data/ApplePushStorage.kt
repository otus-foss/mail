package com.otus.mail.feature.notifications.data

import com.otus.jmap.push.PushStateStore
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

internal class ApplePushStorage(
    private val host: ApplePushPlatform,
) : PushStateStore {
    override fun read(): Map<String, String> =
        host.read("subscriptions")?.let { Json.decodeFromString(it) } ?: emptyMap()

    override fun write(records: Map<String, String>) {
        check(host.write("subscriptions", Json.encodeToString(records)))
    }

    fun registrations(): Map<String, AppleRegistration> =
        host.read("routes")?.let { Json.decodeFromString(it) } ?: emptyMap()

    fun save(registrations: Map<String, AppleRegistration>) {
        check(host.write("routes", Json.encodeToString(registrations)))
    }
}

@Serializable
internal data class AppleRegistration(
    val route: ApplePushRoute,
    val keyId: String,
    val keys: String,
    val token: String,
)
