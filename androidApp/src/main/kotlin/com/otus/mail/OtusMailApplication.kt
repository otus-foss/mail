package com.otus.mail

import android.app.Application
import androidx.work.Configuration
import com.otus.avatar.FileAvatarStore
import com.otus.jmap.login.AndroidLoginStore
import com.otus.mail.app.MailServices
import java.io.File

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
            readLaterStore = AndroidReadLaterStore(this),
        )
    }

    override fun onCreate() {
        super.onCreate()
        MailNotifications(this).createChannel()
        NewMailWorker.schedule(this)
    }
}
