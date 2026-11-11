package com.otusfoss.mail.feature.folders.data

import com.otusfoss.core.jmap.login.LoginStoreException
import com.otusfoss.mail.feature.folders.domain.FolderStorageException

/** [read]'s result, with saved logins that cannot be read reported as a [FolderStorageException]. */
internal suspend fun <T> folderStorage(read: suspend () -> T): T =
    try {
        read()
    } catch (error: LoginStoreException) {
        throw FolderStorageException(error)
    }
