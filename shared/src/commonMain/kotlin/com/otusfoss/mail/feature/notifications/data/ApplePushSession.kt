package com.otusfoss.mail.feature.notifications.data

import com.otusfoss.core.jmap.PushKeys
import com.otusfoss.core.jmap.PushMessage
import com.otusfoss.core.jmap.push.PushEndpoint
import com.otusfoss.core.jmap.push.PushStatus
import com.otusfoss.core.jmap.push.PushSubscriptions
import com.otusfoss.mail.feature.notifications.domain.MailPush
import com.otusfoss.mail.feature.notifications.domain.PushDeliveryStatus
import io.ktor.client.HttpClient
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlin.io.encoding.Base64

internal class ApplePushSession<S : PushServices>(
    private val host: ApplePushPlatform,
    baseUrl: String,
    http: HttpClient,
    createServices: () -> S,
) {
    private val storage = ApplePushStorage(host)
    val bridge = baseUrl.takeIf { it.isNotBlank() }?.let { ApplePushBridge(it, http) }
    val services: S by lazy(createServices)
    private val subscriptions: PushSubscriptions by lazy {
        val saved = host.read("device")
        val deviceId = saved ?: host.createIdentifier()
        if (saved == null) check(host.write("device", deviceId))
        services.pushSubscriptions(storage, "ios-$deviceId", MailPush.Deliveries) { login ->
            val registration = storage.registrations()[login]
            if (registration != null) checkNotNull(bridge).active(registration.route, value = false)
        }
    }
    private val lock = Mutex()

    /** The logins of the last sync that left none waiting for verification; null while one waits. */
    private var settledLogins: List<String>? = null
    var results = emptyMap<String, PushDeliveryStatus>()
    var registrationError = false
    var token: String? = null
    var enabled: Boolean
        get() = host.read("enabled") == "true"
        set(value) {
            check(host.write("enabled", value.toString()))
        }

    suspend fun forget(loginId: String) {
        lock.withLock { remove(loginId) }
    }

    private suspend fun remove(loginId: String) {
        val registration = storage.registrations()[loginId]
        if (registration != null) {
            // Revocation must succeed before deleting the local capability used to stop alerts.
            try {
                bridge?.delete(registration.route)
            } catch (_: ApplePushRouteExpired) {
                // Already revoked.
            }
            host.removeKey(registration.keyId)
            storage.save(storage.registrations() - loginId)
        }
        subscriptions.forget(loginId)
        results = results - loginId
    }

    suspend fun syncSafely(): Boolean = guarded { sync() }

    /**
     * The foreground check: syncs only while a login waits for verification, after a failed sync, or
     * when the logins changed. A settled device sends the bridge nothing until the next sync.
     */
    suspend fun checkSafely(): Boolean =
        guarded {
            if (services.notificationLogins().map { it.id } != settledLogins) sync()
        }

    private suspend fun guarded(work: suspend () -> Unit): Boolean =
        lock.withLock {
            try {
                work()
                true
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                settledLogins = null
                results = services.logins().associate { it.id to PushDeliveryStatus.Unavailable }
                false
            }
        }

    private suspend fun sync() {
        settledLogins = null
        val push = subscriptions
        val client = bridge ?: return
        val logins = services.notificationLogins().map { it.id }
        storage
            .registrations()
            .keys
            .filter { !enabled || it !in logins }
            .forEach { remove(it) }
        val deviceToken = (token ?: host.read("token")).takeIf { enabled && !registrationError }
        if (deviceToken != null) syncLogins(logins, deviceToken, client, push)
        // Turning pushes on, a new device token, or the next foreground activation syncs again.
        val waiting = deviceToken != null && PushDeliveryStatus.Connecting in results.values
        settledLogins = logins.takeUnless { waiting }
    }

    private suspend fun syncLogins(
        logins: List<String>,
        deviceToken: String,
        client: ApplePushBridge,
        push: PushSubscriptions,
    ) {
        logins.forEach { login ->
            val old = storage.registrations()[login]
            if (old != null && old.token != deviceToken) remove(login)
            val registration = storage.registrations()[login] ?: register(login, deviceToken, client)
            try {
                client.pending(registration.route)
                push.setEndpoint(
                    login,
                    PushEndpoint(
                        client.endpoint(registration.route),
                        Json.decodeFromString<PushKeys>(registration.keys),
                    ),
                )
            } catch (_: ApplePushRouteExpired) {
                storage.save(storage.registrations() - login)
                host.removeKey(registration.keyId)
                push.clearEndpoint(login)
            }
        }
        push.sync()
        storage.registrations().forEach { (login, registration) -> receive(login, registration, client, push) }
        push.sync()
        val statuses = push.status()
        storage.registrations().forEach { (login, registration) ->
            client.active(registration.route, statuses[login] == PushStatus.Active)
        }
        results = logins.associateWith { login -> statuses[login].delivery }
    }

    private suspend fun register(
        login: String,
        deviceToken: String,
        client: ApplePushBridge,
    ): AppleRegistration {
        val keyId = host.createIdentifier()
        val keys = checkNotNull(host.createKey(keyId)) { "Push keys unavailable" }
        var saved = false
        try {
            val route = client.register(deviceToken, keyId)
            val registration = AppleRegistration(route, keyId, keys, deviceToken)
            storage.save(storage.registrations() + (login to registration))
            saved = true
            return registration
        } finally {
            if (!saved) host.removeKey(keyId)
        }
    }

    private suspend fun receive(
        login: String,
        registration: AppleRegistration,
        client: ApplePushBridge,
        push: PushSubscriptions,
    ) {
        client.pending(registration.route).forEach { messageId ->
            val payload = client.message(messageId) ?: return@forEach
            val text = host.decrypt(registration.keyId, Base64.encode(payload))
            when (val message = text?.let(PushMessage::decode)) {
                is PushMessage.Verification -> push.receive(login, message)
                is PushMessage.StateChange -> host.showDelivery(message)
                else -> Unit
            }
            client.acknowledge(registration.route, messageId)
        }
    }
}

private fun ApplePushPlatform.showDelivery(message: PushMessage.StateChange) {
    if (message.changed.values.any { "EmailDelivery" in it }) showNewMail()
}
