package com.otus.mail.app

import com.otus.mail.core.cache.InMemoryMailCache
import com.otus.mail.core.cache.MailCache
import com.otus.mail.feature.contacts.domain.ContactStore
import com.otus.mail.feature.folders.domain.FolderColorStore
import com.otus.mail.feature.folders.domain.FolderNudgeStore
import com.otus.mail.feature.folders.domain.ReadLaterStore
import com.otus.mail.feature.inbox.domain.AddressPinStore
import com.otus.mail.feature.inbox.domain.EmailChangeStore
import com.otus.mail.feature.inbox.domain.OutboxStore
import com.otus.mail.feature.notifications.domain.NotificationPreviewStore

/**
 * App-private settings and mail kept on this device only; null where a platform keeps none. The
 * [cache] stays in memory where a platform has no database. [notificationPreviews] exists only where
 * alerts can show message content.
 */
class MailDeviceStores(
    val folders: FolderStores = FolderStores(),
    val addressPins: AddressPinStore? = null,
    val cache: MailCache = InMemoryMailCache(),
    val notificationPreviews: NotificationPreviewStore? = null,
    val contacts: ContactStore? = null,
    val waiting: WaitingStores = WaitingStores(),
)

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
