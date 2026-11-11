package com.otusfoss.mail

import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class OutboxWorkerTest {
    private val app = ApplicationProvider.getApplicationContext<OtusMailApplication>()

    @Before
    fun testWorkManager() {
        WorkManagerTestInitHelper.initializeTestWorkManager(
            app,
            Configuration.Builder().setExecutor(SynchronousExecutor()).build(),
        )
    }

    @Test
    fun aSoonerWakeDoesNotWaitBehindALaterRun() {
        val now = Clock.System.now()
        OutboxWorker.wake(app, now + 5.hours).get()
        OutboxWorker.wake(app, now).get()
        val run = waiting().single()
        assertTrue(run.nextScheduleTimeMillis <= (now + 1.minutes).toEpochMilliseconds())
    }

    @Test
    fun aLaterWakeKeepsTheEarlierTime() {
        val now = Clock.System.now()
        OutboxWorker.wake(app, now + 1.minutes).get()
        OutboxWorker.wake(app, now + 5.hours).get()
        val run = waiting().single()
        assertTrue(run.nextScheduleTimeMillis <= (now + 2.minutes).toEpochMilliseconds())
        assertEquals(WorkInfo.State.ENQUEUED, run.state)
    }

    private fun waiting(): List<WorkInfo> =
        WorkManager
            .getInstance(app)
            .getWorkInfosForUniqueWork("outbox")
            .get()
            .filterNot { it.state.isFinished }
}
