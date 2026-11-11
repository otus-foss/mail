package com.otusfoss.mail

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.otusfoss.core.jmap.PushKeys
import com.otusfoss.core.jmap.PushMessage
import com.otusfoss.core.jmap.push.PushEndpoint
import com.otusfoss.mail.feature.notifications.domain.PushDeliveryStatus

/**
 * Applies one change from the UnifiedPush app, if any, then brings every account's push
 * subscription in line on its server. Runs one at a time, in the order the changes came.
 */
class PushSyncWorker(
    context: Context,
    parameters: WorkerParameters,
) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result {
        val app = applicationContext as OtusMailApplication
        val login = inputData.getString(LOGIN)
        if (login != null) {
            when (inputData.getString(KIND)) {
                ENDPOINT -> app.instantPush.endpoint(login, endpoint())
                FAILED -> app.instantPush.failed(login, problem())
                CLEARED -> app.push.clearEndpoint(login)
                VERIFICATION -> app.push.receive(login, verification())
            }
        }
        app.instantPush.registerAccounts(retry = inputData.getString(KIND) == null)
        app.push.sync()
        return Result.success()
    }

    override suspend fun getForegroundInfo(): ForegroundInfo = MailNotifications(applicationContext).backgroundWork()

    private fun problem(): PushDeliveryStatus =
        PushDeliveryStatus.entries.find { it.name == inputData.getString(PROBLEM) }
            ?: PushDeliveryStatus.RegistrationFailed

    private fun endpoint(): PushEndpoint {
        val p256dh = inputData.getString(P256DH)
        val auth = inputData.getString(AUTH)
        val keys = if (p256dh != null && auth != null) PushKeys(p256dh, auth) else null
        return PushEndpoint(inputData.getString(URL).orEmpty(), keys)
    }

    private fun verification() =
        PushMessage.Verification(inputData.getString(SUBSCRIPTION).orEmpty(), inputData.getString(CODE).orEmpty())

    companion object {
        private const val NAME = "push-sync"
        private const val KIND = "kind"
        private const val LOGIN = "login"
        private const val URL = "url"
        private const val P256DH = "p256dh"
        private const val AUTH = "auth"
        private const val SUBSCRIPTION = "subscription"
        private const val CODE = "code"
        private const val ENDPOINT = "endpoint"
        private const val FAILED = "failed"
        private const val PROBLEM = "problem"
        private const val CLEARED = "cleared"
        private const val VERIFICATION = "verification"

        fun endpoint(
            login: String,
            url: String,
            p256dh: String?,
            auth: String?,
        ): Data = workDataOf(KIND to ENDPOINT, LOGIN to login, URL to url, P256DH to p256dh, AUTH to auth)

        fun failed(
            login: String,
            problem: PushDeliveryStatus,
        ): Data = workDataOf(KIND to FAILED, LOGIN to login, PROBLEM to problem.name)

        fun cleared(login: String): Data = workDataOf(KIND to CLEARED, LOGIN to login)

        fun verification(
            login: String,
            verification: PushMessage.Verification,
        ): Data =
            workDataOf(
                KIND to VERIFICATION,
                LOGIN to login,
                SUBSCRIPTION to verification.pushSubscriptionId,
                CODE to verification.verificationCode,
            )

        /** Expedited, so a verification goes back to the server within seconds, even in Doze. */
        fun enqueue(
            context: Context,
            change: Data = Data.EMPTY,
        ) {
            WorkManager
                .getInstance(context)
                .enqueueUniqueWork(
                    NAME,
                    ExistingWorkPolicy.APPEND_OR_REPLACE,
                    OneTimeWorkRequestBuilder<PushSyncWorker>()
                        .setInputData(change)
                        .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                        .build(),
                )
        }
    }
}
