package com.otusfoss.mail

import android.content.Context
import com.otusfoss.mail.app.MailDeviceStores
import com.otusfoss.mail.core.cache.InMemoryMailCache
import com.otusfoss.mail.core.files.InMemoryDownloadFolder

/** The app's named stores on [context], in the files the app keeps them in. */
internal fun testStores(context: Context): MailDeviceStores =
    MailDeviceStores.named(
        { name, commit -> mailPreferences(context, name, commit) },
        InMemoryMailCache(),
        InMemoryDownloadFolder(),
    )
