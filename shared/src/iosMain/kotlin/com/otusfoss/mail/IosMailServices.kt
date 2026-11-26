package com.otusfoss.mail

import app.cash.sqldelight.driver.native.NativeSqliteDriver
import com.otusfoss.core.avatar.FileAvatarStore
import com.otusfoss.core.jmap.login.IosLoginStore
import com.otusfoss.core.storage.KeychainTextCipher
import com.otusfoss.core.storage.SealedMapStore
import com.otusfoss.core.storage.UserDefaultsMap
import com.otusfoss.mail.app.MailDeviceStores
import com.otusfoss.mail.app.MailPlatform
import com.otusfoss.mail.app.MailServices
import com.otusfoss.mail.core.cache.MailDatabase
import com.otusfoss.mail.core.cache.SealedMailCache
import com.otusfoss.mail.core.cache.SqlMailCache
import com.otusfoss.mail.core.files.FileDownloadFolder
import com.otusfoss.mail.core.files.prepareDownloadRoot
import com.otusfoss.mail.core.settings.presentation.MailBehaviorState
import com.otusfoss.mail.core.storage.FileMapStore
import com.otusfoss.mail.feature.folders.domain.ReadLaterStore
import com.otusfoss.mail.feature.notifications.domain.InstantPush
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
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
    val supportDirectory = userDirectory(NSApplicationSupportDirectory)
    // User defaults go into backups, so the stores live in files that stay out of them.
    val storeFolder = Path(supportDirectory, "stores").also(::prepareDownloadRoot)
    // iOS may empty Caches when space runs low, so the mail kept for offline use stays out of it.
    val cacheFolder = Path(supportDirectory, "cache").also(::prepareDownloadRoot)
    deleteUnsealedCache(cacheDirectory)
    // One per process, so the keys are read from the Keychain once.
    val cipher = KeychainTextCipher()
    return MailServices(
        // Names kept from the first release so saved accounts keep working.
        IosLoginStore(listKey = "otus.accounts", service = "otus.mail.account"),
        avatarStore = FileAvatarStore("$cacheDirectory/avatars-v1"),
        stores =
            MailDeviceStores.named(
                { name, _ ->
                    SealedMapStore(FileMapStore(Path(storeFolder, "$name.json"), legacyStore(name)), cipher, name)
                },
                SealedMailCache(SqlMailCache(cacheDriver(cacheFolder.toString())), cipher),
                FileDownloadFolder("$supportDirectory/attachments"),
            ),
        host =
            MailPlatform(
                instantPush = push,
                categoriesOn = { iosMailBehavior.behavior.list.categories.enabled },
            ),
    )
}

/** Where releases before the files kept store [name]; Read later keeps the name of the first release. */
private fun legacyStore(name: String) =
    UserDefaultsMap("otus.mail." + if (name == ReadLaterStore.name) "read_later_folders" else name)

/** The path of the app's [directory], such as its Caches. */
private fun userDirectory(directory: NSSearchPathDirectory): String =
    NSSearchPathForDirectoriesInDomains(directory, NSUserDomainMask, true).first() as String

/** The cache of releases that kept mail unsealed, in Caches. Sync fills the sealed one again. */
private fun deleteUnsealedCache(directory: String) =
    listOf("", "-wal", "-shm").forEach { suffix ->
        SystemFileSystem.delete(Path(directory, "otus_mail_cache.db$suffix"), mustExist = false)
    }

/** In a folder of Application Support that stays out of backups and that iOS does not empty. */
private fun cacheDriver(directory: String) =
    NativeSqliteDriver(
        MailDatabase.Schema,
        "otus_mail_sealed_cache.db",
        onConfiguration = { config -> config.copy(extendedConfig = config.extendedConfig.copy(basePath = directory)) },
    )
