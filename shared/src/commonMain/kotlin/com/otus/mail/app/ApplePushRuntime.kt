package com.otus.mail.app

import com.otus.mail.feature.notifications.data.ApplePushPlatform
import com.otus.mail.feature.notifications.domain.InstantPush
import com.otus.mail.feature.notifications.domain.PushDeliveryStatus
import com.otus.mail.feature.notifications.domain.PushDistributor
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration.Companion.seconds

/** One per app process, shared by the Swift lifecycle and the Compose settings page. */
open class ApplePushRuntime(
    private val host: ApplePushPlatform,
    baseUrl: String,
    createServices: (InstantPush) -> MailServices,
    http: HttpClient = applePushHttp(),
) : InstantPush {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val session = ApplePushSession(host, baseUrl, http) { createServices(this) }
    internal val mailServices: MailServices get() = session.mailServices
    private var watching: Job? = null
    private var background: Job? = null
    override val periodicChecks: Boolean = false

    fun registered(deviceToken: String) {
        if (host.write("token", deviceToken)) {
            session.registrationError = false
            session.token = deviceToken
            scope.launch { session.syncSafely() }
        } else {
            registrationFailed()
        }
    }

    fun registrationFailed() {
        session.registrationError = true
        session.results = mailServices.logins().associate { it.id to PushDeliveryStatus.RegistrationFailed }
    }

    fun resume() {
        if (session.enabled) host.requestRegistration()
        if (watching?.isActive == true) return
        watching =
            scope.launch {
                while (true) {
                    session.syncSafely()
                    delay(REFRESH_INTERVAL)
                }
            }
    }

    fun pause() {
        watching?.cancel()
        watching = null
        background?.cancel()
    }

    fun refresh(completion: (Boolean) -> Unit) {
        background?.cancel()
        background =
            scope.launch {
                var success = false
                try {
                    success = withTimeoutOrNull(BACKGROUND_TIMEOUT) { session.syncSafely() } ?: false
                } finally {
                    completion(success)
                }
            }
    }

    override suspend fun distributors(): List<PushDistributor> =
        if (session.bridge ==
            null
        ) {
            emptyList()
        } else {
            listOf(APPLE)
        }

    override suspend fun current(): PushDistributor? = APPLE.takeIf { session.enabled && session.bridge != null }

    override suspend fun statuses(): Map<String, PushDeliveryStatus> = session.results

    override suspend fun use(distributor: PushDistributor?) {
        try {
            session.enabled = distributor == APPLE && session.bridge != null
        } catch (_: IllegalStateException) {
            registrationFailed()
            return
        }
        if (session.enabled) host.requestRegistration() else host.stopRegistration()
        session.syncSafely()
    }

    override suspend fun forget(loginId: String) = session.forget(loginId)

    private companion object {
        val APPLE = PushDistributor("apple", "Apple Push", isApple = true)
        val REFRESH_INTERVAL = 15.seconds
        val BACKGROUND_TIMEOUT = 25.seconds
    }
}

private fun applePushHttp(): HttpClient =
    HttpClient {
        followRedirects = false
        install(HttpTimeout) { requestTimeoutMillis = APPLE_REQUEST_TIMEOUT }
    }

private const val APPLE_REQUEST_TIMEOUT = 10000L
