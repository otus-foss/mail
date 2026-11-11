package com.otusfoss.mail.core.files

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import kotlinx.io.IOException
import kotlinx.io.Sink
import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.readByteArray

/** Prepares [root] before the first file goes in, such as with the platform's file protection. */
internal expect fun prepareDownloadRoot(root: Path)

/** When [file] was last written or marked used, from its modification time. */
internal expect fun modifiedAtMillis(file: Path): Long

/** Sets [file]'s modification time to now, which records it as used. */
internal expect fun markUsed(file: Path)

/** Moves [source] over [destination] in the same folder. */
internal expect fun moveFile(
    source: Path,
    destination: Path,
)

/** Runs [block], turning each error that means the disk failed into [failure]. */
internal expect suspend fun <T> reportingDiskFailures(
    failure: (Exception) -> Exception,
    block: suspend () -> T,
): T
