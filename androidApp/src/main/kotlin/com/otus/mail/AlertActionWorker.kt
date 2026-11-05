package com.otus.mail

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ForegroundInfo
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.otus.mail.feature.inbox.domain.EmailShortcuts
import com.otus.mail.feature.notifications.domain.NewMail

/**
 * Runs the change of an alert's button once the device has a network. A refused or unreachable
 * change tries again later, up to [MAX_ATTEMPTS] times in all. When the last try fails too, the
 * alert comes back and says which change failed, so a failure is never silent.
 */
class AlertActionWorker(
    context: Context,
    parameters: WorkerParameters,
) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result {
        val action = AlertAction.named(inputData.getString(ACTION))
        val mail = inputData.mail()
        if (action == null || mail == null) return Result.failure()
        val shortcuts = (applicationContext as OtusMailApplication).services.shortcuts
        return when {
            shortcuts.perform(action, mail.loginId, mail.emailId) -> Result.success()
            runAttemptCount + 1 < MAX_ATTEMPTS -> Result.retry()
            else -> failed(action, mail)
        }
    }

    private fun failed(
        action: AlertAction,
        mail: NewMail,
    ): Result {
        MailNotifications(applicationContext).showFailed(mail, action)
        return Result.failure()
    }

    override suspend fun getForegroundInfo(): ForegroundInfo = MailNotifications(applicationContext).backgroundWork()

    companion object {
        private const val ACTION = "action"
        private const val MAX_ATTEMPTS = 5

        /** Expedited, so the change usually reaches the server within seconds, even in Doze. */
        internal fun enqueue(
            context: Context,
            action: AlertAction,
            mail: NewMail,
        ) {
            val input =
                Data
                    .Builder()
                    .putString(ACTION, action.name)
                    .putMail(mail)
                    .build()
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

/** Runs [action] on the email [emailId] of the login [loginId]; false when it did not change. */
private suspend fun EmailShortcuts.perform(
    action: AlertAction,
    loginId: String,
    emailId: String,
): Boolean =
    when (action) {
        AlertAction.Trash -> trash(loginId, emailId)

        AlertAction.Archive -> archive(loginId, emailId)

        AlertAction.MarkRead -> markRead(loginId, emailId)

        AlertAction.Flag -> flag(loginId, emailId)

        AlertAction.Spam -> reportSpam(loginId, emailId)

        // Reply opens Mail instead, so it never reaches the worker.
        AlertAction.Reply -> false
    }
