package com.otus.mail.core.cache

import android.database.sqlite.SQLiteDiskIOException
import android.database.sqlite.SQLiteException
import android.database.sqlite.SQLiteFullException
import kotlinx.coroutines.CancellationException
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.sql.SQLException
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [35])
class CacheStorageExceptionTest {
    @Test
    fun androidStorageErrorsAreRecoverable() {
        assertTrue(isCacheStorageFailure(SQLiteFullException("full")))
        assertTrue(isCacheStorageFailure(SQLiteDiskIOException("disk unavailable")))
        assertFalse(isCacheStorageFailure(SQLiteException("SQL error")))
        val failure = SQLiteDiskIOException("disk unavailable")
        assertSame(failure, assertFailsWith<CacheStorageException> { cacheStorage { throw failure } }.cause)
    }

    @Test
    fun jdbcExtendedCodesKeepTheirStorageMeaning() {
        assertTrue(isCacheStorageFailure(SQLException("disk I/O", null, 10)))
        assertTrue(isCacheStorageFailure(SQLException("extended disk I/O", null, 266)))
        assertFalse(isCacheStorageFailure(SQLException("invalid SQL", null, 1)))
        assertFalse(isCacheStorageFailure(SQLException("constraint", null, 19)))
    }

    @Test
    fun cancellationAndProgrammingErrorsAreNotStorageFailures() {
        val canceled = CancellationException()
        assertFalse(isCacheStorageFailure(canceled))
        assertSame(canceled, assertFailsWith<CancellationException> { cacheStorage { throw canceled } })
        assertFalse(isCacheStorageFailure(IllegalStateException("bug")))
    }
}
