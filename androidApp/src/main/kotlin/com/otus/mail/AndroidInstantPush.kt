package com.otus.mail

import android.content.pm.PackageManager
import com.otus.jmap.login.JmapLogin
import com.otus.mail.feature.notifications.domain.InstantPush
import com.otus.mail.feature.notifications.domain.PushDistributor
import com.otus.push.PushStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.unifiedpush.android.connector.UnifiedPush

/** Registers each account with the UnifiedPush app the user chose. Endpoints arrive in [MailPushService]. */
class AndroidInstantPush(
    private val app: OtusMailApplication,
) : InstantPush {
    override suspend fun distributors(): List<PushDistributor> =
        withContext(Dispatchers.IO) { UnifiedPush.getDistributors(app).map(::distributor) }

    override suspend fun current(): PushDistributor? =
        withContext(Dispatchers.IO) { UnifiedPush.getSavedDistributor(app)?.let(::distributor) }

    override suspend fun use(distributor: PushDistributor?) {
        withContext(Dispatchers.IO) {
            val logins = app.services.logins()
            if (distributor == null) {
                logins.forEach {
                    UnifiedPush.unregister(app, it.id)
                    app.push.clearEndpoint(it.id)
                }
                UnifiedPush.removeDistributor(app)
            } else {
                // Saving another app unregisters every account from the old one.
                UnifiedPush.saveDistributor(app, distributor.id)
                logins.forEach(::register)
            }
        }
        PushSyncWorker.enqueue(app)
    }

    override suspend fun statuses(): Map<String, PushStatus> = app.push.status()

    /** Registers accounts that have no endpoint yet, such as one added since the app was chosen. */
    suspend fun registerMissing() {
        if (withContext(Dispatchers.IO) { UnifiedPush.getSavedDistributor(app) } == null) return
        val statuses = app.push.status()
        withContext(Dispatchers.IO) {
            app.services
                .logins()
                .filter { (statuses[it.id] ?: PushStatus.Off) == PushStatus.Off }
                .forEach(::register)
        }
    }

    private fun register(login: JmapLogin) = UnifiedPush.register(app, login.id, "Otus Mail: ${login.name}")

    private fun distributor(packageName: String): PushDistributor {
        val name =
            try {
                app.packageManager.getApplicationLabel(app.packageManager.getApplicationInfo(packageName, 0)).toString()
            } catch (_: PackageManager.NameNotFoundException) {
                packageName
            }
        return PushDistributor(packageName, name)
    }
}
