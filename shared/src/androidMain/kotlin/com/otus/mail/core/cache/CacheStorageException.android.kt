package com.otus.mail.core.cache

import android.database.sqlite.SQLiteAccessPermException
import android.database.sqlite.SQLiteCantOpenDatabaseException
import android.database.sqlite.SQLiteDatabaseCorruptException
import android.database.sqlite.SQLiteDatabaseLockedException
import android.database.sqlite.SQLiteDiskIOException
import android.database.sqlite.SQLiteException
import android.database.sqlite.SQLiteFullException
import android.database.sqlite.SQLiteReadOnlyDatabaseException
import java.sql.SQLException

internal actual fun <T> cacheStorage(block: () -> T): T =
    try {
        block()
    } catch (error: SQLiteException) {
        throw error.cacheFailure()
    } catch (error: SQLException) {
        throw error.cacheFailure()
    }

private fun Exception.cacheFailure(): Exception = if (isCacheStorageFailure(this)) CacheStorageException(this) else this

internal fun isCacheStorageFailure(error: Exception): Boolean =
    when (error) {
        is SQLException -> (error.errorCode and PRIMARY_CODE_MASK) in STORAGE_CODES

        is SQLiteAccessPermException,
        is SQLiteCantOpenDatabaseException,
        is SQLiteDatabaseCorruptException,
        is SQLiteDatabaseLockedException,
        is SQLiteDiskIOException,
        is SQLiteFullException,
        is SQLiteReadOnlyDatabaseException,
        -> true

        else -> false
    }

private val STORAGE_CODES =
    setOf(
        SQLITE_PERM,
        SQLITE_BUSY,
        SQLITE_LOCKED,
        SQLITE_READONLY,
        SQLITE_IOERR,
        SQLITE_CORRUPT,
        SQLITE_FULL,
        SQLITE_CANTOPEN,
        SQLITE_NOTADB,
    )

// Extended SQLite result codes retain their primary code in the low byte.
private const val PRIMARY_CODE_MASK = 0xff
private const val SQLITE_PERM = 3
private const val SQLITE_BUSY = 5
private const val SQLITE_LOCKED = 6
private const val SQLITE_READONLY = 8
private const val SQLITE_IOERR = 10
private const val SQLITE_CORRUPT = 11
private const val SQLITE_FULL = 13
private const val SQLITE_CANTOPEN = 14
private const val SQLITE_NOTADB = 26
