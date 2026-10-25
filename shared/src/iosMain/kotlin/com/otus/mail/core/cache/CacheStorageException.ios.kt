package com.otus.mail.core.cache

import co.touchlab.sqliter.interop.SQLiteExceptionErrorCode
import co.touchlab.sqliter.interop.SqliteErrorType

internal actual fun <T> cacheStorage(block: () -> T): T =
    try {
        block()
    } catch (error: SQLiteExceptionErrorCode) {
        throw when (error.errorType) {
            in LOCK_ERRORS -> CacheStorageException(error, transient = true)
            in STORAGE_ERRORS -> CacheStorageException(error)
            else -> error
        }
    }

// Another connection holds the database, so a later attempt can succeed.
private val LOCK_ERRORS = setOf(SqliteErrorType.SQLITE_BUSY, SqliteErrorType.SQLITE_LOCKED)

private val STORAGE_ERRORS =
    setOf(
        SqliteErrorType.SQLITE_PERM,
        SqliteErrorType.SQLITE_READONLY,
        SqliteErrorType.SQLITE_IOERR,
        SqliteErrorType.SQLITE_CORRUPT,
        SqliteErrorType.SQLITE_FULL,
        SqliteErrorType.SQLITE_CANTOPEN,
        SqliteErrorType.SQLITE_NOTADB,
    )
