package com.otus.mail

import app.cash.sqldelight.driver.native.NativeSqliteDriver
import com.otus.avatar.FileAvatarStore
import com.otus.jmap.login.IosLoginStore
import com.otus.mail.app.MailDeviceStores
import com.otus.mail.app.MailPlatform
import com.otus.mail.app.MailServices
import com.otus.mail.core.cache.MailDatabase
import com.otus.mail.core.cache.SqlMailCache
import com.otus.mail.core.files.FileDownloadFolder
import com.otus.mail.core.settings.presentation.MailBehaviorState
import com.otus.mail.feature.folders.domain.ReadLaterStore
import com.otus.mail.feature.notifications.domain.InstantPush
import com.otus.storage.UserDefaultsMap
import platform.Foundation.NSApplicationSupportDirectory
import platform.Foundation.NSCachesDirectory
import platform.Foundation.NSSearchPathDirectory
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSUserDomainMask

/**
 * The saved Mail settings, one for the process, so the screen and the mail services that run
 * without it, such as push, read the same settings.
 */
internal val iosMailBehavior: MailBehaviorState by lazy { MailBehaviorState(IosMailBehaviorStore()) }

internal fun iosMailServices(push: InstantPush?): MailServices {
    val cacheDirectory = userDirectory(NSCachesDirectory)
    // Each store keeps one user-defaults dictionary; Read later keeps the name of the first release.
    return MailServices(
        // Names kept from the first release so saved accounts keep working.
        IosLoginStore(listKey = "otus.accounts", service = "otus.mail.account"),
        avatarStore = FileAvatarStore("$cacheDirectory/avatars-v1"),
        stores =
            MailDeviceStores.named(
                { name, _ ->
                    UserDefaultsMap("otus.mail." + if (name == ReadLaterStore.name) "read_later_folders" else name)
                },
                SqlMailCache(cacheDriver(cacheDirectory)),
                FileDownloadFolder("${userDirectory(NSApplicationSupportDirectory)}/attachments"),
            ),
        host =
            MailPlatform(
                instantPush = push,
                categoriesOn = { iosMailBehavior.behavior.list.categories.enabled },
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
