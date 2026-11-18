package com.otusfoss.mail

import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import com.otusfoss.mail.core.cache.MailDatabase

/**
 * The driver of the mail cache in the database [name]. It writes ahead to a log, so a read, such
 * as an opened message's body, runs while a list sync is being saved instead of after it. A row's
 * window is [WINDOW_BYTES], since one cached body can pass Android's default of 2 MB.
 */
internal fun mailCacheDriver(
    context: Context,
    name: String,
): SqlDriver =
    AndroidSqliteDriver(
        MailDatabase.Schema,
        context,
        name,
        callback =
            object : AndroidSqliteDriver.Callback(MailDatabase.Schema) {
                override fun onConfigure(db: SupportSQLiteDatabase) {
                    db.enableWriteAheadLogging()
                }
            },
        windowSizeBytes = WINDOW_BYTES,
    )

private const val WINDOW_BYTES = 8L * 1024 * 1024
