package com.otus.mail

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import com.otus.mail.feature.updates.domain.UpdateInstaller
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.io.OutputStream
import java.net.URL

/**
 * Streams a nightly APK into a [PackageInstaller] session, so the update installs in place without a
 * browser download. Android asks the user to confirm the first update, and also to let Otus install
 * apps; once Otus has installed itself, later updates need no confirmation on Android 12 and newer.
 * The installer then closes the app. Needs `REQUEST_INSTALL_PACKAGES` and [UpdateStatusReceiver]
 * in the manifest, which only the nightly build declares.
 */
class AndroidUpdateInstaller(
    private val context: Context,
) : UpdateInstaller {
    override suspend fun install(
        download: String,
        onProgress: (Float) -> Unit,
    ) = withContext(Dispatchers.IO) {
        val installer = context.packageManager.packageInstaller
        val params =
            PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
                setAppPackageName(context.packageName)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
                }
            }
        val id = installer.createSession(params)
        var committed = false
        try {
            installer.openSession(id).use { session ->
                write(download, session, onProgress)
                session.commit(statusReceiver(id).intentSender)
                committed = true
            }
        } finally {
            if (!committed) installer.abandonSession(id)
        }
    }

    private suspend fun write(
        download: String,
        session: PackageInstaller.Session,
        onProgress: (Float) -> Unit,
    ) {
        val connection = URL(download).openConnection()
        val total = connection.contentLengthLong
        connection.getInputStream().use { input ->
            session.openWrite(APK_NAME, 0, total).use { output ->
                input.copyWithProgress(output, total, onProgress)
                session.fsync(output)
            }
        }
    }

    /** Reports whole percents of [total], so the footer's ring redraws at most a hundred times. */
    private suspend fun InputStream.copyWithProgress(
        output: OutputStream,
        total: Long,
        onProgress: (Float) -> Unit,
    ) {
        val buffer = ByteArray(BUFFER_SIZE)
        var written = 0L
        var shown = -1
        var read = read(buffer)
        while (read >= 0) {
            currentCoroutineContext().ensureActive()
            output.write(buffer, 0, read)
            written += read
            val percent = if (total > 0) (written * PERCENT / total).toInt() else 0
            if (percent != shown) onProgress(percent / PERCENT.toFloat())
            shown = percent
            read = read(buffer)
        }
    }

    private fun statusReceiver(session: Int): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            session,
            Intent(context, UpdateStatusReceiver::class.java).setPackage(context.packageName),
            // Mutable, because the installer adds the status and the confirmation intent.
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
        )

    private companion object {
        const val APK_NAME = "update.apk"
        const val BUFFER_SIZE = 64 * 1024
        const val PERCENT = 100L
    }
}
