package com.otus.mail

import android.app.Application
import com.otus.jmap.login.AndroidLoginStore
import com.otus.mail.app.MailServices
import com.otus.mail.core.avatar.FileAvatarStore
import java.io.File

/** Owns process-wide services, so activity recreation does not open new network clients. */
class OtusMailApplication : Application() {
    val services: MailServices by lazy {
        // Names kept from the first release so saved accounts keep working.
        MailServices(
            AndroidLoginStore(this, preferencesName = "otus_accounts", keyAlias = "otus.account.credentials"),
            avatarStore = FileAvatarStore(File(cacheDir, "avatars-v1").absolutePath),
        )
    }
}
