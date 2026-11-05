package com.otus.mail

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf

/**
 * Archives or marks read the message of an alert's button, once the device has a network. A
 * refused or unreachable change tries again later, up to [MAX_ATTEMPTS] times in all; an account
 * without an Archive folder keeps the message where it is.
 */
class AlertActionWorker(
    context: Context,
    parameters: WorkerParameters,
) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result {
        val action = AlertAction.named(inputData.getString(ACTION))
        val loginId = inputData.getString(EXTRA_LOGIN_ID)
        val emailId = inputData.getString(EXTRA_EMAIL_ID)
        if (action == null || loginId == null || emailId == null) return Result.failure()
        val shortcuts = (applicationContext as OtusMailApplication).services.shortcuts
        val done =
            when (action) {
                AlertAction.Archive -> shortcuts.archive(loginId, emailId)
                AlertAction.MarkRead -> shortcuts.markRead(loginId, emailId)
            }
        return when {
            done -> Result.success()
            runAttemptCount + 1 < MAX_ATTEMPTS -> Result.retry()
            else -> Result.failure()
        }
    }

    override suspend fun getForegroundInfo(): ForegroundInfo = MailNotifications(applicationContext).backgroundWork()

    companion object {
        private const val ACTION = "action"
        private const val MAX_ATTEMPTS = 5

        /** Expedited, so the change usually reaches the server within seconds, even in Doze. */
        internal fun enqueue(
            context: Context,
            action: AlertAction,
            loginId: String,
            emailId: String,
        ) {
            val input = workDataOf(ACTION to action.name, EXTRA_LOGIN_ID to loginId, EXTRA_EMAIL_ID to emailId)
            WorkManager
                .getInstance(context)
                .enqueue(
                    OneTimeWorkRequestBuilder<AlertActionWorker>()
                        .setInputData(input)
                        .setConstraints(Constraints(requiredNetworkType = NetworkType.CONNECTED))
                        .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                        .build(),
                )
        }
    }
}
