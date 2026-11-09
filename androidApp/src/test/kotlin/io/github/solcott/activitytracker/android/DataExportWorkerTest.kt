package io.github.solcott.activitytracker.android

import android.app.Application
import android.app.NotificationManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.ForegroundInfo
import androidx.work.ForegroundUpdater
import androidx.work.ListenableWorker
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.impl.utils.futures.SettableFuture
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.testing.WorkManagerTestInitHelper
import com.google.common.util.concurrent.ListenableFuture
import io.github.solcott.activitytracker.data.db.DataExportEntity
import io.github.solcott.activitytracker.data.export.DataExportProgress
import java.util.UUID
import kotlin.coroutines.cancellation.CancellationException
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.time.Instant
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * The data-export job (auth/data-export): it runs the account's export, asks to run again while it's still running (a run stopped early,
 * its process killed or its time up), keeps one job however often Export asks for it, and says when the export is ready.
 */
@RunWith(RobolectricTestRunner::class)
// The plain Application: the app's own builds the whole graph. Robolectric's newest SDK, below the app's compile SDK.
@Config(sdk = [36], application = Application::class)
class DataExportWorkerTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    private fun factory(runExport: suspend (suspend (DataExportProgress?) -> Unit) -> Boolean?) =
        object : WorkerFactory() {
            override fun createWorker(appContext: Context, workerClassName: String, workerParameters: WorkerParameters): ListenableWorker =
                DataExportWorker(appContext, workerParameters, runExport)
        }

    private fun worker(attempt: Int = 0, runExport: suspend (suspend (DataExportProgress?) -> Unit) -> Boolean?): DataExportWorker =
        TestListenableWorkerBuilder<DataExportWorker>(context)
            .setWorkerFactory(factory(runExport))
            .setForegroundUpdater(Ignored)
            .setRunAttemptCount(attempt)
            .build()

    private object Ignored : ForegroundUpdater {
        @Suppress("ForbiddenVoid") // WorkManager's Java interface.
        override fun setForegroundAsync(context: Context, id: UUID, foregroundInfo: ForegroundInfo): ListenableFuture<Void> =
            SettableFuture.create<Void>().apply { set(null) }
    }

    private fun progress(state: String) =
        DataExportProgress(
            id = "3kexport",
            state = state,
            reason = null,
            records = 10,
            blobsDone = 2,
            blobsTotal = 2,
            activitiesDone = 1,
            activitiesTotal = 1,
            problems = 0,
            unsentChanges = 0,
            spacesIncluded = true,
            neededBytes = null,
            handle = "alice.test",
            createdAt = Instant.parse("2026-11-05T12:00:00Z"),
            finishedAt = null,
        )

    private fun readyShown() =
        shadowOf(context.getSystemService(NotificationManager::class.java)).allNotifications.any {
            it.extras.getString("android.title") == context.getString(R.string.export_ready_title)
        }

    @Test fun aRunStillRunningAsksToRunAgain() = runTest { assertEquals(ListenableWorker.Result.retry(), worker { true }.doWork()) }

    @Test
    fun aStoppedRunIsntTakenForAFailedOne() = runTest {
        assertFailsWith<CancellationException> { worker { throw CancellationException("WorkManager stopped the job") }.doWork() }
    }

    @Test
    fun anExportThatKeepsFailingStopsBeingRetriedAfterTheCap() = runTest {
        assertEquals(ListenableWorker.Result.success(), worker(attempt = DataExportWorker.MAX_ATTEMPTS) { true }.doWork())
    }

    @Test
    fun aReadyExportIsAnnounced() = runTest {
        shadowOf(context.getSystemService(NotificationManager::class.java)).setNotificationsEnabled(true)

        val result = worker { onProgress ->
            onProgress(progress(DataExportEntity.STATE_READY))
            false
        }
            .doWork()

        assertEquals(ListenableWorker.Result.success(), result)
        assert(readyShown()) { "the ready notification is posted" }
        val open = shadowOf(context.getSystemService(NotificationManager::class.java)).getNotification(DataExportWorker.READY_ID)
        assertNotNull(shadowOf(assertNotNull(open.contentIntent)).savedIntent.action.takeIf { it == MainActivity.ACTION_OPEN_ACCOUNT })
    }

    @Test
    fun aPausedExportIsNotAnnounced() = runTest {
        worker { onProgress ->
            onProgress(progress(DataExportEntity.STATE_PAUSED))
            false
        }
            .doWork()

        assertNull(shadowOf(context.getSystemService(NotificationManager::class.java)).getNotification(DataExportWorker.READY_ID))
    }

    @Test
    fun exportAsksForOneJob() {
        var runs = 0
        WorkManagerTestInitHelper.initializeTestWorkManager(
            context,
            Configuration.Builder()
                .setExecutor(SynchronousExecutor())
                .setWorkerFactory(
                    factory {
                        runs++
                        true
                    },
                )
                .build(),
        )
        WorkManagerTestInitHelper.getTestDriver(context)?.let { driver ->
            DataExportWorker.schedule(context)
            DataExportWorker.schedule(context)
            val job = WorkManager.getInstance(context).getWorkInfosForUniqueWork("data-export").get().single()
            // It needs a connection: let the constraint pass.
            driver.setAllConstraintsMet(job.id)
        }

        val job = WorkManager.getInstance(context).getWorkInfosForUniqueWork("data-export").get().single()
        assertEquals(1, runs, "a second Export while the first job runs or waits adds none")
        assertEquals(WorkInfo.State.ENQUEUED, job.state, "still running, so it waits to run again")
    }
}
