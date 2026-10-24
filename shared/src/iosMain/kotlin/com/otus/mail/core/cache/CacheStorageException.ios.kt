package com.otus.mail.core.cache

import co.touchlab.sqliter.interop.SQLiteExceptionErrorCode
import co.touchlab.sqliter.interop.SqliteErrorType

internal actual fun <T> cacheStorage(block: () -> T): T =
    try {
        block()
    } catch (error: SQLiteExceptionErrorCode) {
        if (error.errorType in STORAGE_ERRORS) throw CacheStorageException(error)
        throw error
    }

private val STORAGE_ERRORS =
    setOf(
        SqliteErrorType.SQLITE_PERM,
        SqliteErrorType.SQLITE_BUSY,
        SqliteErrorType.SQLITE_LOCKED,
        SqliteErrorType.SQLITE_READONLY,
        SqliteErrorType.SQLITE_IOERR,
        SqliteErrorType.SQLITE_CORRUPT,
        SqliteErrorType.SQLITE_FULL,
        SqliteErrorType.SQLITE_CANTOPEN,
        SqliteErrorType.SQLITE_NOTADB,
    )
