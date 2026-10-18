package com.otus.mail.app

import com.otus.mail.feature.folders.domain.FolderColorStore
import com.otus.mail.feature.folders.domain.FolderNudgeStore
import com.otus.mail.feature.folders.domain.ReadLaterStore
import com.otus.mail.feature.inbox.domain.AddressPinStore

/** App-private settings kept on this device only; null where a platform keeps none. */
class MailDeviceStores(
    val readLater: ReadLaterStore? = null,
    val folderColors: FolderColorStore? = null,
    val addressPins: AddressPinStore? = null,
    val folderNudges: FolderNudgeStore? = null,
)
