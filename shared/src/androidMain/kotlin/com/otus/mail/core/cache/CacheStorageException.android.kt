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

private fun Exception.cacheFailure(): Exception =
    when {
        isCacheLock(this) -> CacheStorageException(this, transient = true)
        isCacheStorageFailure(this) -> CacheStorageException(this)
        else -> this
    }

/** SQLITE_BUSY or SQLITE_LOCKED: another connection holds the database, so a later attempt can succeed. */
internal fun isCacheLock(error: Exception): Boolean =
    when (error) {
        is SQLException -> error.primaryCode in LOCK_CODES
        else -> error is SQLiteDatabaseLockedException
    }

internal fun isCacheStorageFailure(error: Exception): Boolean =
    when (error) {
        is SQLException -> error.primaryCode in STORAGE_CODES

        is SQLiteAccessPermException,
        is SQLiteCantOpenDatabaseException,
        is SQLiteDatabaseCorruptException,
        is SQLiteDiskIOException,
        is SQLiteFullException,
        is SQLiteReadOnlyDatabaseException,
        -> true

        else -> false
    }

// Extended SQLite result codes retain their primary code in the low byte.
private val SQLException.primaryCode get() = errorCode and PRIMARY_CODE_MASK

private val LOCK_CODES = setOf(SQLITE_BUSY, SQLITE_LOCKED)

private val STORAGE_CODES =
    setOf(
        SQLITE_PERM,
        SQLITE_READONLY,
        SQLITE_IOERR,
        SQLITE_CORRUPT,
        SQLITE_FULL,
        SQLITE_CANTOPEN,
        SQLITE_NOTADB,
    )

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
