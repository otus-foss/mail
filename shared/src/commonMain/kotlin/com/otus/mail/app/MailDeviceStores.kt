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
 * alerts can show message content. The [outbox] keeps messages, and [changes] marks and moves,
 * until the server has them.
 */
class MailDeviceStores(
    val readLater: ReadLaterStore? = null,
    val folderColors: FolderColorStore? = null,
    val addressPins: AddressPinStore? = null,
    val folderNudges: FolderNudgeStore? = null,
    val cache: MailCache = InMemoryMailCache(),
    val notificationPreviews: NotificationPreviewStore? = null,
    val contacts: ContactStore? = null,
    val outbox: OutboxStore? = null,
    val changes: EmailChangeStore? = null,
)
