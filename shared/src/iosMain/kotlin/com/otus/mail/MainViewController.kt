package com.otus.mail

import androidx.compose.ui.window.ComposeUIViewController
import app.cash.sqldelight.driver.native.NativeSqliteDriver
import com.otus.avatar.FileAvatarStore
import com.otus.dateformat.IosDateFormatPreferenceStore
import com.otus.jmap.login.IosLoginStore
import com.otus.mail.app.App
import com.otus.mail.app.MailDeviceStores
import com.otus.mail.app.MailServices
import com.otus.mail.core.cache.MailDatabase
import com.otus.mail.core.cache.SqlMailCache
import com.otus.mail.feature.inbox.presentation.MailBehaviorState
import com.otus.settings.data.IosAnimationPreferenceStore
import com.otus.settings.presentation.AnimationSettingsState
import com.otus.settings.presentation.DateFormatSettingsState
import com.otus.settings.presentation.ThemeSettingsState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.launch
import platform.Foundation.NSCachesDirectory
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSUserDomainMask

fun mainViewController() =
    DateFormatSettingsState(IosDateFormatPreferenceStore()).let { dateFormatState ->
        // Names kept from the first release so saved accounts keep working.
        val cacheDirectory =
            NSSearchPathForDirectoriesInDomains(
                NSCachesDirectory,
                NSUserDomainMask,
                true,
            ).first() as String
        val services =
            MailServices(
                IosLoginStore(listKey = "otus.accounts", service = "otus.mail.account"),
                avatarStore = FileAvatarStore("$cacheDirectory/avatars-v1"),
                stores =
                    MailDeviceStores(
                        IosReadLaterStore(),
                        IosFolderColorStore(),
                        IosAddressPinStore(),
                        IosFolderNudgeStore(),
                        SqlMailCache(cacheDriver(cacheDirectory)),
                    ),
            )
        // Cache upkeep outlives the screen; it ends with the process.
        CoroutineScope(Dispatchers.IO).launch { services.trimCache() }
        val behaviorState = MailBehaviorState(IosMailBehaviorStore())
        val themeState = ThemeSettingsState(IosThemePreferenceStore())
        val animationState = AnimationSettingsState(IosAnimationPreferenceStore())
        ComposeUIViewController { App(dateFormatState, services, behaviorState, themeState, animationState) }
    }

/** In the Caches directory, which iOS leaves out of backups and may empty when space runs low. */
private fun cacheDriver(directory: String) =
    NativeSqliteDriver(
        MailDatabase.Schema,
        "otus_mail_cache.db",
        onConfiguration = { config -> config.copy(extendedConfig = config.extendedConfig.copy(basePath = directory)) },
    )
