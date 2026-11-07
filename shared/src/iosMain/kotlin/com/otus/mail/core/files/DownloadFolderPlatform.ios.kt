package com.otus.mail.core.files

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.io.IOException
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import platform.Foundation.NSDate
import platform.Foundation.NSFileManager
import platform.Foundation.NSFileModificationDate
import platform.Foundation.NSFileProtectionCompleteUntilFirstUserAuthentication
import platform.Foundation.NSFileProtectionKey
import platform.Foundation.NSURL
import platform.Foundation.NSURLIsExcludedFromBackupKey
import platform.Foundation.timeIntervalSince1970

/**
 * Creates [root] with a protection class that files made inside take, readable only after the
 * first unlock, and keeps it out of backups.
 */
@OptIn(ExperimentalForeignApi::class)
internal actual fun prepareDownloadRoot(root: Path) {
    if (SystemFileSystem.exists(root)) return
    val protection = mapOf<Any?, Any?>(NSFileProtectionKey to NSFileProtectionCompleteUntilFirstUserAuthentication)
    if (!NSFileManager.defaultManager.createDirectoryAtPath(root.toString(), true, protection, null)) {
        throw IOException("Cannot create $root")
    }
    NSURL.fileURLWithPath(root.toString()).setResourceValue(true, NSURLIsExcludedFromBackupKey, null)
}

@OptIn(ExperimentalForeignApi::class)
internal actual fun modifiedAtMillis(file: Path): Long {
    val attributes = NSFileManager.defaultManager.attributesOfItemAtPath(file.toString(), null)
    val modified = attributes?.get(NSFileModificationDate) as? NSDate
    return ((modified?.timeIntervalSince1970 ?: 0.0) * MILLIS_PER_SECOND).toLong()
}

@OptIn(ExperimentalForeignApi::class)
internal actual fun markUsed(file: Path) {
    NSFileManager.defaultManager.setAttributes(
        mapOf<Any?, Any?>(NSFileModificationDate to NSDate()),
        file.toString(),
        null,
    )
}

internal actual fun moveFile(
    source: Path,
    destination: Path,
) = SystemFileSystem.atomicMove(source, destination)

internal actual suspend fun <T> reportingDiskFailures(
    failure: (Exception) -> Exception,
    block: suspend () -> T,
): T =
    try {
        block()
    } catch (error: IOException) {
        throw failure(error)
    }

private const val MILLIS_PER_SECOND = 1000.0
