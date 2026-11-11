package com.otusfoss.mail.core.files

import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import java.io.File
import java.io.IOException

/** The app's private files folder is already out of backups, so the folder only needs to exist. */
internal actual fun prepareDownloadRoot(root: Path) = SystemFileSystem.createDirectories(root)

internal actual fun modifiedAtMillis(file: Path): Long = File(file.toString()).lastModified()

internal actual fun markUsed(file: Path) {
    File(file.toString()).setLastModified(System.currentTimeMillis())
}

/** A rename, since kotlinx-io's atomic move needs `java.nio.file`, which Android has only from API 26. */
internal actual fun moveFile(
    source: Path,
    destination: Path,
) {
    if (!File(source.toString()).renameTo(File(destination.toString()))) throw IOException("Cannot finish $destination")
}

/** Android also refuses a file it may not touch with a [SecurityException]. */
internal actual suspend fun <T> reportingDiskFailures(
    failure: (Exception) -> Exception,
    block: suspend () -> T,
): T =
    try {
        block()
    } catch (error: IOException) {
        throw failure(error)
    } catch (error: SecurityException) {
        throw failure(error)
    }
