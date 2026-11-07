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
import com.otus.mail.feature.notifications.domain.PERIODIC_CHECK
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.TimeUnit

/**
 * Looks for new mail and posts notifications for it, periodically and after each push, and clears
 * the notifications of mail handled since.
 */
class NewMailWorker(
    context: Context,
    parameters: WorkerParameters,
) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result {
        val app = applicationContext as OtusMailApplication
        // A periodic run and a pushed one can overlap. Running both from the same saved state
        // would post the same mail twice and bring back notifications the user dismissed.
        val pushed = inputData.getBoolean(PUSHED, false)
        checking.withLock {
            if (pushes.start(
                    pushed,
                )
            ) {
                app.services.newMail().check(MailNotifications(app))
            }
        }
        // The periodic run also renews push subscriptions and registers new accounts.
        if (!pushed) PushSyncWorker.enqueue(app)
        return Result.success()
    }

    override suspend fun getForegroundInfo(): ForegroundInfo = MailNotifications(applicationContext).backgroundWork()

    companion object {
        private const val NAME = "new-mail-check"
        private const val NOW = "new-mail-now"
        private const val PUSHED = "pushed"

        private val checking = Mutex()
        private val pushes = PushedChecks()

        /** Keeps an existing schedule, so each app start does not restart the period. */
        fun schedule(context: Context) {
            WorkManager
                .getInstance(context)
                .enqueueUniquePeriodicWork(
                    NAME,
                    ExistingPeriodicWorkPolicy.KEEP,
                    PeriodicWorkRequestBuilder<NewMailWorker>(PERIODIC_CHECK.inWholeMinutes, TimeUnit.MINUTES)
                        .setConstraints(Constraints(requiredNetworkType = NetworkType.CONNECTED))
                        .build(),
                )
        }

        /**
         * Checks at once after a push. Expedited work runs within seconds, even in Doze. A push
         * during a check queues another, so mail delivered mid-check is not missed; however many
         * pushes arrive during one check, only one more check runs after it.
         */
        fun runNow(context: Context) {
            pushes.received()
            WorkManager
                .getInstance(context)
                .enqueueUniqueWork(
                    NOW,
                    ExistingWorkPolicy.APPEND_OR_REPLACE,
                    OneTimeWorkRequestBuilder<NewMailWorker>()
                        .setInputData(workDataOf(PUSHED to true))
                        .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                        .build(),
                )
        }
    }
}
