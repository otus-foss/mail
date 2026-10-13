package com.otus.mail

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

/** The background check: looks for new mail and posts notifications for it. */
class NewMailWorker(
    context: Context,
    parameters: WorkerParameters,
) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result {
        val app = applicationContext as OtusMailApplication
        app.services.newMail(AndroidMailStateStore(app)).check(MailNotifications(app)::show)
        return Result.success()
    }

    companion object {
        private const val NAME = "new-mail-check"

        /** Android's shortest period for repeating work. Doze and app standby can stretch it. */
        private const val PERIOD_MINUTES = 15L

        /** Keeps an existing schedule, so each app start does not restart the period. */
        fun schedule(context: Context) {
            val request =
                PeriodicWorkRequestBuilder<NewMailWorker>(PERIOD_MINUTES, TimeUnit.MINUTES)
                    .setConstraints(Constraints(requiredNetworkType = NetworkType.CONNECTED))
                    .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}
