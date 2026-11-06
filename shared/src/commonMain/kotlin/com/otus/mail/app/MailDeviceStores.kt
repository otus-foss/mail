package com.otus.mail.app

import com.otus.mail.core.cache.InMemoryMailCache
import com.otus.mail.core.cache.MailCache
import com.otus.mail.core.files.DownloadFolder
import com.otus.mail.core.files.InMemoryDownloadFolder
import com.otus.mail.core.messages.domain.EmailChangeStore
import com.otus.mail.core.settings.domain.AddressPinStore
import com.otus.mail.core.settings.domain.forgetAccount
import com.otus.mail.feature.compose.domain.OutboxStore
import com.otus.mail.feature.contacts.domain.ContactStore
import com.otus.mail.feature.contacts.domain.forgetAccount
import com.otus.mail.feature.contacts.presentation.FrequentContacts
import com.otus.mail.feature.folders.domain.FolderColorStore
import com.otus.mail.feature.folders.domain.FolderNudgeStore
import com.otus.mail.feature.folders.domain.ReadLaterStore
import com.otus.mail.feature.folders.domain.forgetAccount
import com.otus.mail.feature.notifications.domain.MailStateStore
import com.otus.mail.feature.notifications.domain.NotificationPreviewStore
import com.otus.mail.feature.notifications.domain.forgetAccount
import com.otus.storage.StringMapStore

/**
 * App-private settings and mail kept on this device only; null where a platform keeps none. The
 * [cache] and the [downloads] folder of attachments stay in memory where a platform has no database
 * or folder. [notificationPreviews] exists only where alerts can show message content.
 */
class MailDeviceStores(
    val folders: FolderStores = FolderStores(),
    val addressPins: AddressPinStore? = null,
    val cache: MailCache = InMemoryMailCache(),
    val notificationPreviews: NotificationPreviewStore? = null,
    val contacts: ContactStore? = null,
    val waiting: WaitingStores = WaitingStores(),
    val downloads: DownloadFolder = InMemoryDownloadFolder(),
    /** Each login's Email state from the last new-mail check. */
    val mailStates: MailStateStore? = null,
) {
    /**
     * What each store keeps of one login, dropped when its account is removed. Every store that
     * keeps something per login is here; the outbox and waiting changes forget theirs with the
     * outbox and the change queue.
     */
    private val loginScoped: List<(String) -> Unit> =
        listOfNotNull(
            folders.readLater?.let { store -> store::forgetAccount },
            folders.colors?.let { store -> store::forgetAccount },
            folders.nudges?.let { store -> store::forgetAccount },
            addressPins?.let { store -> store::forgetAccount },
            notificationPreviews?.let { store -> store::forgetAccount },
            contacts?.let { store -> store::forgetAccount },
            contacts?.let { store -> { loginId: String -> FrequentContacts.forget(store, loginId) } },
            mailStates?.let { store -> { loginId: String -> store.write(store.read() - loginId) } },
        )

    /** Drops everything these stores keep for [loginId], when its account is removed. */
    fun forgetAccount(loginId: String) = loginScoped.forEach { it(loginId) }

    companion object {
        /**
         * The stores of a platform that keeps each in a string map [open] opens by name. A map
         * opened with `commit` writes at once, so what waits for the server survives the app
         * closing right after. Renaming a store loses what users saved in it.
         */
        fun named(
            open: (name: String, commit: Boolean) -> StringMapStore,
            cache: MailCache,
            downloads: DownloadFolder,
            notificationPreviews: NotificationPreviewStore? = null,
        ): MailDeviceStores =
            MailDeviceStores(
                FolderStores(
                    object : ReadLaterStore, StringMapStore by open(READ_LATER, false) {},
                    object : FolderColorStore, StringMapStore by open("folder_colors", false) {},
                    object : FolderNudgeStore, StringMapStore by open("folder_nudges", false) {},
                ),
                object : AddressPinStore, StringMapStore by open("address_pins", false) {},
                cache,
                notificationPreviews,
                object : ContactStore, StringMapStore by open("contacts", false) {},
                WaitingStores(
                    object : OutboxStore, StringMapStore by open("outbox", true) {},
                    object : EmailChangeStore, StringMapStore by open("pending_changes", true) {},
                ),
                downloads,
                object : MailStateStore, StringMapStore by open("states", false) {},
            )

        /** The store of each login's Read later folder; iOS kept it under its own name. */
        const val READ_LATER = "read_later"
    }
}

/** Each folder's Read later role, color, and the nudges shown about it. */
class FolderStores(
    val readLater: ReadLaterStore? = null,
    val colors: FolderColorStore? = null,
    val nudges: FolderNudgeStore? = null,
)

/** What waits on the device until the server has it: messages in [outbox], marks and moves in [changes]. */
class WaitingStores(
    val outbox: OutboxStore? = null,
    val changes: EmailChangeStore? = null,
)
