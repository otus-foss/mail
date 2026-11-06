package com.otus.mail

import app.cash.sqldelight.driver.native.NativeSqliteDriver
import com.otus.avatar.FileAvatarStore
import com.otus.jmap.login.IosLoginStore
import com.otus.mail.app.MailDeviceStores
import com.otus.mail.app.MailDeviceStores.Companion.READ_LATER
import com.otus.mail.app.MailServices
import com.otus.mail.core.cache.MailDatabase
import com.otus.mail.core.cache.SqlMailCache
import com.otus.mail.feature.notifications.domain.InstantPush
import com.otus.storage.UserDefaultsMap
import platform.Foundation.NSApplicationSupportDirectory
import platform.Foundation.NSCachesDirectory
import platform.Foundation.NSSearchPathDirectory
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSUserDomainMask

internal fun iosMailServices(push: InstantPush?): MailServices {
    val cacheDirectory = userDirectory(NSCachesDirectory)
    // Each store keeps one user-defaults dictionary; Read later keeps the name of the first release.
    return MailServices(
        // Names kept from the first release so saved accounts keep working.
        IosLoginStore(listKey = "otus.accounts", service = "otus.mail.account"),
        instantPush = push,
        avatarStore = FileAvatarStore("$cacheDirectory/avatars-v1"),
        stores =
            MailDeviceStores.named(
                { name, _ -> UserDefaultsMap("otus.mail." + if (name == READ_LATER) "read_later_folders" else name) },
                SqlMailCache(cacheDriver(cacheDirectory)),
                IosDownloadFolder("${userDirectory(NSApplicationSupportDirectory)}/attachments"),
            ),
    )
}

/** The path of the app's [directory], such as its Caches. */
private fun userDirectory(directory: NSSearchPathDirectory): String =
    NSSearchPathForDirectoriesInDomains(directory, NSUserDomainMask, true).first() as String

/** In the Caches directory, which iOS leaves out of backups and may empty when space runs low. */
private fun cacheDriver(directory: String) =
    NativeSqliteDriver(
        MailDatabase.Schema,
        "otus_mail_cache.db",
        onConfiguration = { config -> config.copy(extendedConfig = config.extendedConfig.copy(basePath = directory)) },
    )
