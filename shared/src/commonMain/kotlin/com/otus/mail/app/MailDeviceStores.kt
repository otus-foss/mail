package com.otus.mail.app

import com.otus.mail.core.cache.InMemoryMailCache
import com.otus.mail.core.cache.MailCache
import com.otus.mail.core.files.DownloadFolder
import com.otus.mail.core.files.InMemoryDownloadFolder
import com.otus.mail.core.messages.domain.EmailChangeStore
import com.otus.mail.core.settings.domain.AddressPinStore
import com.otus.mail.core.settings.domain.forgetAccount
import com.otus.mail.feature.contacts.domain.ContactStore
import com.otus.mail.feature.contacts.domain.forgetAccount
import com.otus.mail.feature.folders.domain.FolderColorStore
import com.otus.mail.feature.folders.domain.FolderNudgeStore
import com.otus.mail.feature.folders.domain.ReadLaterStore
import com.otus.mail.feature.folders.domain.forgetAccount
import com.otus.mail.feature.inbox.domain.OutboxStore
import com.otus.mail.feature.notifications.domain.NotificationPreviewStore

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
)

/** Drops the settings these stores keep for [loginId], when its account is removed. */
fun MailDeviceStores.forgetAccount(loginId: String) {
    folders.colors?.forgetAccount(loginId)
    addressPins?.forgetAccount(loginId)
    folders.nudges?.forgetAccount(loginId)
    contacts?.forgetAccount(loginId)
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
