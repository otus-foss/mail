package com.otus.mail

import android.app.Application
import androidx.work.Configuration
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import com.otus.avatar.FileAvatarStore
import com.otus.jmap.login.AndroidLoginStore
import com.otus.mail.app.MailDeviceStores
import com.otus.mail.app.MailPickers
import com.otus.mail.app.MailServices
import com.otus.mail.app.NightlyUpdates
import com.otus.mail.core.cache.MailDatabase
import com.otus.mail.core.cache.SqlMailCache
import com.otus.mail.feature.inbox.domain.DevicePrivacy
import com.otus.push.PushSubscriptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID

/**
 * Owns process-wide services, so activity recreation does not open new network clients. It also
 * gives WorkManager its configuration, so WorkManager starts on first use instead of at launch.
 */
class OtusMailApplication :
    Application(),
    Configuration.Provider {
    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().build()

    val services: MailServices by lazy {
        // Names kept from the first release so saved accounts keep working.
        MailServices(
            AndroidLoginStore(this, preferencesName = "otus_accounts", keyAlias = "otus.account.credentials"),
            avatarStore = FileAvatarStore(File(cacheDir, "avatars-v1").absolutePath),
            stores =
                MailDeviceStores(
                    AndroidReadLaterStore(this),
                    AndroidFolderColorStore(this),
                    AndroidAddressPinStore(this),
                    AndroidFolderNudgeStore(this),
                    SqlMailCache(AndroidSqliteDriver(MailDatabase.Schema, this, CACHE_DATABASE)),
                ),
            nightly = NightlyUpdates(installer = AndroidUpdateInstaller(this)),
            instantPush = instantPush,
            pickers =
                MailPickers(
                    AndroidSignatureImages(photoPicker),
                    attachmentFiles,
                    AndroidSavedFiles(this),
                    printer,
                    AndroidAvatarPhotos(photoPicker),
                ),
            devicePrivacy = DevicePrivacy(screen = true, keyboard = true),
        )
    }

    val photoPicker: AndroidPhotoPicker by lazy { AndroidPhotoPicker(this) }

    val attachmentFiles: AndroidAttachmentFiles by lazy { AndroidAttachmentFiles(this) }

    /** The print dialog needs the activity in front, which attaches itself here. */
    val printer: AndroidMessagePrinter by lazy { AndroidMessagePrinter() }

    val instantPush: AndroidInstantPush by lazy { AndroidInstantPush(this) }

    /** One per process, so its lock covers the push service, the workers, and the settings page. */
    val push: PushSubscriptions by lazy { services.pushSubscriptions(AndroidPushStateStore(this), deviceClientId()) }

    /** Work that outlives any screen, such as cache upkeep. It ends with the process. */
    private val processScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        MailNotifications(this).createChannels()
        NewMailWorker.schedule(this)
        processScope.launch { services.trimCache() }
    }

    /**
     * Tells this app's push subscriptions apart from those of other apps and devices (RFC 8620,
     * section 7.2). A random id, kept until the app's data is cleared.
     */
    private fun deviceClientId(): String {
        val stored = PreferenceMap(this, "otus_mail_device")
        return stored.read()[DEVICE_ID]
            ?: "otus-mail-${UUID.randomUUID()}".also { stored.write(mapOf(DEVICE_ID to it)) }
    }

    private companion object {
        const val DEVICE_ID = "deviceClientId"
        const val CACHE_DATABASE = "otus_mail_cache.db"
    }
}
