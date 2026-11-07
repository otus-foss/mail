package com.otus.mail

import android.content.Context
import com.otus.mail.app.MailDeviceStores
import com.otus.mail.core.cache.InMemoryMailCache
import com.otus.mail.core.files.InMemoryDownloadFolder

/** The app's named stores on [context], in the files the app keeps them in. */
internal fun testStores(context: Context): MailDeviceStores =
    MailDeviceStores.named(
        { name, commit -> mailPreferences(context, name, commit) },
        InMemoryMailCache(),
        InMemoryDownloadFolder(),
    )
