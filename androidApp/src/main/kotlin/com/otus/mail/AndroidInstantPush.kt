package com.otus.mail

import android.content.pm.PackageManager
import com.otus.jmap.push.PushEndpoint
import com.otus.jmap.push.PushStatus
import com.otus.mail.feature.notifications.domain.InstantPush
import com.otus.mail.feature.notifications.domain.PushDeliveryStatus
import com.otus.mail.feature.notifications.domain.PushDistributor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.unifiedpush.android.connector.UnifiedPush

/** Registers each account with the built-in Google service or a separate UnifiedPush app. */
class AndroidInstantPush(
    private val app: OtusMailApplication,
) : InstantPush {
    private val lock = Mutex()
    private val registration by lazy { AndroidPushRegistration(app, app.push, app.services::pushVapidKey) }

    override suspend fun distributors(): List<PushDistributor> =
        withContext(Dispatchers.IO) {
            UnifiedPush.getDistributors(app).map(::distributor).sortedByDescending { it.isGoogle }
        }

    override suspend fun current(): PushDistributor? =
        withContext(Dispatchers.IO) { UnifiedPush.getSavedDistributor(app)?.let(::distributor) }

    override suspend fun use(distributor: PushDistributor?) {
        withContext(Dispatchers.IO) {
            lock.withLock {
                if (UnifiedPush.getSavedDistributor(app) != distributor?.id) {
                    app.services.logins().forEach { app.push.clearEndpoint(it.id) }
                    registration.clear()
                }
                if (distributor == null) {
                    UnifiedPush.removeDistributor(app)
                } else {
                    UnifiedPush.saveDistributor(app, distributor.id)
                }
            }
        }
        PushSyncWorker.enqueue(app)
    }

    override suspend fun statuses(): Map<String, PushDeliveryStatus> =
        withContext(Dispatchers.IO) {
            app.push.status().mapValues { (_, status) ->
                when (status) {
                    PushStatus.Active -> PushDeliveryStatus.Active
                    PushStatus.Refused -> PushDeliveryStatus.ServerRefused
                    PushStatus.Off, PushStatus.Waiting -> PushDeliveryStatus.Connecting
                }
            } + registration.problems()
        }

    override suspend fun forget(loginId: String) {
        withContext(Dispatchers.IO) {
            lock.withLock {
                val distributor = UnifiedPush.getSavedDistributor(app)
                UnifiedPush.unregister(app, loginId)
                // Keep the user's choice for the next account, including after removing the last.
                if (distributor != null) UnifiedPush.saveDistributor(app, distributor)
                registration.forget(loginId)
                app.push.forget(loginId)
            }
        }
    }

    /** Checks Google signing keys even for active accounts, because servers can rotate them. */
    suspend fun registerAccounts(retry: Boolean) {
        withContext(Dispatchers.IO) {
            lock.withLock {
                val distributor = UnifiedPush.getSavedDistributor(app) ?: return@withLock
                app.services.logins().forEach { registration.register(it, distributor, retry) }
            }
        }
    }

    suspend fun endpoint(
        loginId: String,
        endpoint: PushEndpoint,
    ) {
        withContext(Dispatchers.IO) {
            lock.withLock {
                if (UnifiedPush.getSavedDistributor(app) != null && app.services.logins().any { it.id == loginId }) {
                    app.push.setEndpoint(loginId, endpoint)
                    registration.connected(loginId)
                }
            }
        }
    }

    suspend fun failed(
        loginId: String,
        problem: PushDeliveryStatus,
    ) {
        withContext(Dispatchers.IO) { lock.withLock { registration.failed(loginId, problem) } }
    }

    private fun distributor(packageName: String): PushDistributor {
        if (packageName == app.packageName) return PushDistributor(packageName, "Google Play services", isGoogle = true)
        val name =
            try {
                app.packageManager.getApplicationLabel(app.packageManager.getApplicationInfo(packageName, 0)).toString()
            } catch (_: PackageManager.NameNotFoundException) {
                packageName
            }
        return PushDistributor(packageName, name)
    }
}
