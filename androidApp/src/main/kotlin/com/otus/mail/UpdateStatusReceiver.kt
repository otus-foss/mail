package com.otus.mail

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.widget.Toast
import androidx.core.content.IntentCompat

/**
 * Hears how an update session ended. When Android needs the user to confirm, it opens the system's
 * confirmation screen. A failure other than the user cancelling shows a short message.
 */
class UpdateStatusReceiver : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        when (intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                openConfirmation(context, intent)
            }

            PackageInstaller.STATUS_SUCCESS, PackageInstaller.STATUS_FAILURE_ABORTED -> {}

            else -> {
                Toast.makeText(context, "Couldn't install the update", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun openConfirmation(
        context: Context,
        intent: Intent,
    ) {
        val confirmation = IntentCompat.getParcelableExtra(intent, Intent.EXTRA_INTENT, Intent::class.java) ?: return
        context.startActivity(confirmation.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
