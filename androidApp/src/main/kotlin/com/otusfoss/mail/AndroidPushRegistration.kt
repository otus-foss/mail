package com.otusfoss.mail

import android.content.Context
import com.otusfoss.core.jmap.JmapException
import com.otusfoss.core.jmap.login.JmapLogin
import com.otusfoss.core.jmap.login.LoginStoreException
import com.otusfoss.core.jmap.push.PushStatus
import com.otusfoss.core.jmap.push.PushSubscriptions
import com.otusfoss.core.storage.PreferenceMap
import com.otusfoss.mail.feature.notifications.domain.PushDeliveryStatus
import org.unifiedpush.android.connector.UnifiedPush
import org.unifiedpush.android.connector.keys.DefaultKeyManager
import org.unifiedpush.android.connector.keys.KeyManager

/** Called under AndroidInstantPush's lock, so settings and worker callbacks cannot race. */
class AndroidPushRegistration(
    private val context: Context,
    private val subscriptions: PushSubscriptions,
    private val vapidKey: suspend (JmapLogin) -> String?,
    private val keyManager: KeyManager = DefaultKeyManager(context),
) {
    private val keys = PreferenceMap(context, "otus_mail_push_vapid")
    private val problems = PreferenceMap(context, "otus_mail_push_problems")

    fun problems(): Map<String, PushDeliveryStatus> =
        problems
            .read()
            .mapNotNull { (id, name) ->
                PushDeliveryStatus.entries.find { it.name == name }?.let { id to it }
            }.toMap()

    fun failed(
        loginId: String,
        problem: PushDeliveryStatus,
    ) {
        problems.write(problems.read() + (loginId to problem.name))
    }

    fun connected(loginId: String) {
        problems.write(problems.read() - loginId)
    }

    fun forget(loginId: String) {
        connected(loginId)
        keys.write(keys.read() - loginId)
    }

    fun clear() {
        keys.write(emptyMap())
        problems.write(emptyMap())
    }

    suspend fun register(
        login: JmapLogin,
        distributor: String,
        retry: Boolean,
    ) {
        try {
            registerKey(login, distributor, retry)
        } catch (_: JmapException) {
            failed(login.id, PushDeliveryStatus.Unavailable)
        } catch (_: LoginStoreException) {
            failed(login.id, PushDeliveryStatus.Unavailable)
        } catch (_: UnifiedPush.VapidNotValidException) {
            failed(login.id, PushDeliveryStatus.InvalidKey)
        }
    }

    private suspend fun registerKey(
        login: JmapLogin,
        distributor: String,
        retry: Boolean,
    ) {
        val google = distributor == context.packageName
        val key = if (google) vapidKey(login)?.trimEnd('=') else null
        val problem = keyProblem(google, key)
        if (problem != null) {
            stop(login.id, distributor)
            failed(login.id, problem)
            return
        }
        val saved = keys.read()
        val changed = login.id in saved && saved[login.id] != key.orEmpty()
        val status = subscriptions.status()[login.id]
        if (changed) stop(login.id, distributor)
        val needsRetry = retry && (status == null || status == PushStatus.Off || login.id in problems.read())
        if (!changed && login.id in saved && !needsRetry) return
        UnifiedPush.register(context, login.id, "Otus Mail: ${login.name}", vapid = key, keyManager = keyManager)
        keys.write(keys.read() + (login.id to key.orEmpty()))
        connected(login.id)
    }

    private fun keyProblem(
        google: Boolean,
        key: String?,
    ): PushDeliveryStatus? =
        when {
            google && key == null -> PushDeliveryStatus.VapidRequired
            key != null && !VAPID_KEY.matches(key) -> PushDeliveryStatus.InvalidKey
            else -> null
        }

    private suspend fun stop(
        loginId: String,
        distributor: String,
    ) {
        if (loginId in keys.read()) {
            // Unregistering the last account also removes the connector's distributor choice.
            UnifiedPush.unregister(context, loginId, keyManager)
            UnifiedPush.saveDistributor(context, distributor)
        }
        subscriptions.clearEndpoint(loginId)
        forget(loginId)
    }

    private companion object {
        val VAPID_KEY = Regex("^[A-Za-z0-9_-]{87}$")
    }
}
