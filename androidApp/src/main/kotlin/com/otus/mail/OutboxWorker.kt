package com.otus.mail

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * Sends what the outbox keeps once the network is back, such as a message the device held for
 * Undo when the app closed, or one whose send lost its connection.
 */
class OutboxWorker(
    context: Context,
    parameters: WorkerParameters,
) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result {
        val waiting = (applicationContext as OtusMailApplication).services.resumeOutbox()
        return if (waiting) Result.retry() else Result.success()
    }

    companion object {
        private const val NAME = "outbox"
        private const val FIRST_RETRY_SECONDS = 30L

        /**
         * Runs the outbox at [at], or as soon as the network allows. A run already waiting keeps its
         * place and this one follows it, so no time is dropped.
         */
        fun wake(
            context: Context,
            at: Instant,
        ) {
            val delay = (at - Clock.System.now()).inWholeMilliseconds.coerceAtLeast(0)
            WorkManager
                .getInstance(context)
                .enqueueUniqueWork(
                    NAME,
                    ExistingWorkPolicy.APPEND_OR_REPLACE,
                    OneTimeWorkRequestBuilder<OutboxWorker>()
                        .setInitialDelay(delay, TimeUnit.MILLISECONDS)
                        .setConstraints(Constraints(requiredNetworkType = NetworkType.CONNECTED))
                        .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, FIRST_RETRY_SECONDS, TimeUnit.SECONDS)
                        .build(),
                )
        }
    }
}
