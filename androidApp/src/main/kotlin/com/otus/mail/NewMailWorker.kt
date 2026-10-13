package com.otus.mail

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import java.util.concurrent.TimeUnit

/** Looks for new mail and posts notifications for it, periodically and after each push. */
class NewMailWorker(
    context: Context,
    parameters: WorkerParameters,
) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result {
        val app = applicationContext as OtusMailApplication
        app.services.newMail(AndroidMailStateStore(app)).check(MailNotifications(app)::show)
        // The periodic run also renews push subscriptions and registers new accounts.
        if (!inputData.getBoolean(PUSHED, false)) PushSyncWorker.enqueue(app)
        return Result.success()
    }

    override suspend fun getForegroundInfo(): ForegroundInfo = MailNotifications(applicationContext).backgroundWork()

    companion object {
        private const val NAME = "new-mail-check"
        private const val NOW = "new-mail-now"
        private const val PUSHED = "pushed"

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

        /**
         * Checks at once after a push. Expedited work runs within seconds, even in Doze. A push
         * during a check queues another, so mail delivered mid-check is not missed.
         */
        fun runNow(context: Context) {
            val request =
                OneTimeWorkRequestBuilder<NewMailWorker>()
                    .setInputData(workDataOf(PUSHED to true))
                    .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                    .build()
            WorkManager.getInstance(context).enqueueUniqueWork(NOW, ExistingWorkPolicy.APPEND_OR_REPLACE, request)
        }
    }
}
