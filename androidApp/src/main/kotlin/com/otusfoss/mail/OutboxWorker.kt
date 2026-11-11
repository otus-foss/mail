package com.otusfoss.mail

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * Sends what the outbox keeps once the network is back, such as a message the device held for
 * Undo when the app closed, or one whose send lost its connection, and the marks and moves that
 * wait for the server.
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
        private const val AT_TAG = "outbox-at:"

        // One wake at a time, so two wakes never both read the old run and drop each other's time.
        private val wakes = Executors.newSingleThreadExecutor()

        /**
         * Runs the outbox at [at], or as soon as the network allows. A wake during a run follows
         * that run, as the run's own wakes do. Otherwise the waiting run is replaced by one at the
         * earliest time either wanted, so a new time never waits out a failing run's backoff, and
         * no earlier time is dropped. The returned future ends once the run is queued.
         */
        fun wake(
            context: Context,
            at: Instant,
        ): Future<*> = wakes.submit { schedule(WorkManager.getInstance(context), at.toEpochMilliseconds()) }

        private fun schedule(
            manager: WorkManager,
            atMillis: Long,
        ) {
            val runs =
                manager
                    .getWorkInfosForUniqueWork(NAME)
                    .get()
                    .filterNot { it.state.isFinished }
            val running = runs.any { it.state == WorkInfo.State.RUNNING }
            val wanted = if (running) atMillis else minOf(atMillis, runs.minOfOrNull { it.wantedMillis() } ?: atMillis)
            val policy = if (running) ExistingWorkPolicy.APPEND_OR_REPLACE else ExistingWorkPolicy.REPLACE
            manager
                .enqueueUniqueWork(NAME, policy, outboxRun(wanted))
                .result
                .get()
        }

        private fun outboxRun(atMillis: Long) =
            OneTimeWorkRequestBuilder<OutboxWorker>()
                .setInitialDelay(
                    (atMillis - Clock.System.now().toEpochMilliseconds()).coerceAtLeast(0),
                    TimeUnit.MILLISECONDS,
                ).setConstraints(Constraints(requiredNetworkType = NetworkType.CONNECTED))
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, FIRST_RETRY_SECONDS, TimeUnit.SECONDS)
                .addTag("$AT_TAG$atMillis")
                .build()

        /** When this run should go: a blocked run at its own time, a queued one when it is next due. */
        private fun WorkInfo.wantedMillis(): Long =
            if (state == WorkInfo.State.BLOCKED) {
                tags
                    .filter { it.startsWith(AT_TAG) }
                    .firstNotNullOfOrNull { it.removePrefix(AT_TAG).toLongOrNull() }
                    ?: Long.MAX_VALUE
            } else {
                nextScheduleTimeMillis
            }
    }
}
