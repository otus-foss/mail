package com.otus.mail.app

import com.otus.mail.core.cache.InMemoryMailCache
import com.otus.mail.core.cache.MailCache
import com.otus.mail.core.files.DownloadFolder
import com.otus.mail.core.files.InMemoryDownloadFolder
import com.otus.mail.core.messages.domain.EmailChangeStore
import com.otus.mail.core.settings.domain.AddressPinStore
import com.otus.mail.core.storage.DeviceStore
import com.otus.mail.core.storage.StoreSpec
import com.otus.mail.feature.compose.domain.OutboxStore
import com.otus.mail.feature.contacts.domain.ContactStore
import com.otus.mail.feature.folders.domain.FolderColorStore
import com.otus.mail.feature.folders.domain.FolderKey
import com.otus.mail.feature.folders.domain.FolderNudgeStore
import com.otus.mail.feature.folders.domain.ReadLaterStore
import com.otus.mail.feature.notifications.domain.MailStateStore
import com.otus.mail.feature.notifications.domain.NotificationPreviewStore
import com.otus.mail.feature.notifications.domain.forgetAccount
import com.otus.storage.StringMapStore
import kotlinx.serialization.json.JsonObject

/**
 * App-private settings and mail kept on this device only; null where a platform keeps none. The
 * [cache] and the [downloads] folder of attachments stay in memory where a platform has no database
 * or folder. [notificationPreviews] exists only where alerts can show message content.
 */
class MailDeviceStores(
    val folders: FolderStores = FolderStores(),
    val addressPins: DeviceStore<String, List<String>>? = null,
    val cache: MailCache = InMemoryMailCache(),
    val notificationPreviews: NotificationPreviewStore? = null,
    val contacts: DeviceStore<String, String>? = null,
    val waiting: WaitingStores = WaitingStores(),
    val downloads: DownloadFolder = InMemoryDownloadFolder(),
    /** Each login's Email state from the last new-mail check. */
    val mailStates: DeviceStore<String, String>? = null,
) {
    /**
     * What each store keeps of one login, dropped when its account is removed. Every store that
     * keeps something per login is here; the outbox and waiting changes forget theirs with the
     * outbox and the change queue.
     */
    private val loginScoped: List<(String) -> Unit> =
        listOfNotNull(
            folders.readLater?.forgetting { it },
            folders.colors?.forgetting(FolderKey::accountId),
            folders.nudges?.forgetting { it },
            addressPins?.forgetting { it },
            notificationPreviews?.let { store -> store::forgetAccount },
            contacts?.let { store -> { loginId: String -> store.update { ContactStore.forget(it, loginId) } } },
            mailStates?.forgetting { it },
        )

    /** Drops everything these stores keep for [loginId], when its account is removed. */
    fun forgetAccount(loginId: String) = loginScoped.forEach { it(loginId) }

    companion object {
        /**
         * The stores of a platform that keeps each in a string map [open] opens by name. A map
         * opened with `commit` writes at once.
         */
        fun named(
            open: (name: String, commit: Boolean) -> StringMapStore,
            cache: MailCache,
            downloads: DownloadFolder,
            notificationPreviews: NotificationPreviewStore? = null,
        ): MailDeviceStores {
            fun <K, V> opened(spec: StoreSpec<K, V>) = DeviceStore(open(spec.name, spec.commit), spec)
            return MailDeviceStores(
                FolderStores(opened(ReadLaterStore), opened(FolderColorStore), opened(FolderNudgeStore)),
                opened(AddressPinStore),
                cache,
                notificationPreviews,
                opened(ContactStore),
                WaitingStores(opened(OutboxStore), opened(EmailChangeStore)),
                downloads,
                opened(MailStateStore),
            )
        }
    }
}

/** Forgets the entries whose key [loginOf] names the removed login. */
private fun <K, V> DeviceStore<K, V>.forgetting(loginOf: (K) -> String): (String) -> Unit =
    { loginId -> update { saved -> saved.filterKeys { loginOf(it) != loginId } } }

/** Each folder's Read later role, color, and the nudges shown about it. */
class FolderStores(
    val readLater: DeviceStore<String, String>? = null,
    val colors: DeviceStore<FolderKey, String>? = null,
    val nudges: DeviceStore<String, List<String>>? = null,
)

/** What waits on the device until the server has it: messages in [outbox], marks and moves in [changes]. */
class WaitingStores(
    val outbox: DeviceStore<String, String>? = null,
    val changes: DeviceStore<String, Map<String, JsonObject>>? = null,
)
