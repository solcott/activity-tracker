package io.github.solcott.activitytracker.android

import android.app.Application
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
import io.github.solcott.activitytracker.data.db.ImportSaveCounts
import java.util.UUID
import kotlin.coroutines.cancellation.CancellationException
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The batch-save job (feed/batch-import): it runs the account's saves, asks to run again while any is left (a run stopped early, its
 * process killed or its time up), and keeps one job however often Save asks for it.
 */
@RunWith(RobolectricTestRunner::class)
// The plain Application: the app's own builds the whole graph. Robolectric's newest SDK, below the app's compile SDK.
@Config(sdk = [36], application = Application::class)
class ImportSaveWorkerTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    private fun factory(runSaves: suspend (suspend (ImportSaveCounts?) -> Unit) -> Boolean?) =
        object : WorkerFactory() {
            override fun createWorker(appContext: Context, workerClassName: String, workerParameters: WorkerParameters): ListenableWorker =
                ImportSaveWorker(appContext, workerParameters, runSaves)
        }

    private fun worker(
        foreground: ForegroundUpdater = Shown(),
        attempt: Int = 0,
        runSaves: suspend (suspend (ImportSaveCounts?) -> Unit) -> Boolean?,
    ): ImportSaveWorker =
        TestListenableWorkerBuilder<ImportSaveWorker>(context)
            .setWorkerFactory(factory(runSaves))
            .setForegroundUpdater(foreground)
            .setRunAttemptCount(attempt)
            .build()

    /** Counts the foreground notifications the job posts. */
    private class Shown : ForegroundUpdater {
        var posts = 0

        @Suppress("ForbiddenVoid") // WorkManager's Java interface.
        override fun setForegroundAsync(context: Context, id: UUID, foregroundInfo: ForegroundInfo): ListenableFuture<Void> =
            SettableFuture.create<Void>().apply {
                posts++
                set(null)
            }
    }

    @Test fun aRunWithSavesLeftAsksToRunAgain() = runTest { assertEquals(ListenableWorker.Result.retry(), worker { true }.doWork()) }

    @Test
    fun aRunThatThrowsAsksToRunAgain() = runTest {
        assertEquals(ListenableWorker.Result.retry(), worker { error("no session graph") }.doWork())
    }

    @Test
    fun aStoppedRunIsntTakenForAFailedOne() = runTest {
        assertFailsWith<CancellationException> { worker { throw CancellationException("WorkManager stopped the job") }.doWork() }
    }

    @Test
    fun aSaveThatKeepsFailingStopsBeingRetriedAfterTheCap() = runTest {
        assertEquals(ListenableWorker.Result.retry(), worker(attempt = ImportSaveWorker.MAX_ATTEMPTS - 1) { true }.doWork())
        assertEquals(ListenableWorker.Result.success(), worker(attempt = ImportSaveWorker.MAX_ATTEMPTS) { true }.doWork())
    }

    @Test
    fun aRunThatFinishesOrFindsNoOneSignedInSucceeds() = runTest {
        assertEquals(ListenableWorker.Result.success(), worker { false }.doWork())
        assertEquals(ListenableWorker.Result.success(), worker { null }.doWork())
    }

    @Test
    fun progressUpdatesTheNotificationAtMostOnceASecond() = runTest {
        val shown = Shown()
        worker(shown) { onProgress ->
                repeat(20) {
                    onProgress(ImportSaveCounts("3kbatch", did = "did:plc:alice", total = 20, state = "running", done = it, skipped = 0))
                }
                false
            }
            .doWork()

        assertEquals(2, shown.posts, "the start, then the first count: the rest came within the second")
    }

    @Test
    fun aJobWhoseRunThrowsWaitsToRunAgainAndEndsAtTheCap() {
        var runs = 0
        WorkManagerTestInitHelper.initializeTestWorkManager(
            context,
            Configuration.Builder()
                .setExecutor(SynchronousExecutor())
                .setWorkerFactory(
                    factory {
                        runs++
                        error("the plan can't be opened")
                    },
                )
                .build(),
        )
        val work = WorkManager.getInstance(context)
        val driver = requireNotNull(WorkManagerTestInitHelper.getTestDriver(context))

        ImportSaveWorker.schedule(context)
        fun job() = work.getWorkInfosForUniqueWork("import-save").get().single()
        assertEquals(WorkInfo.State.ENQUEUED, job().state, "a throw leaves saves, so it waits to run again")
        // Each retry waits out its backoff; the driver lets it pass, a bounded number of times.
        repeat(ImportSaveWorker.MAX_ATTEMPTS * 2) { if (job().state == WorkInfo.State.ENQUEUED) driver.setInitialDelayMet(job().id) }

        assertEquals(WorkInfo.State.SUCCEEDED, job().state, "it stops asking once the attempts run out")
        assertEquals(ImportSaveWorker.MAX_ATTEMPTS + 1, runs)
    }

    @Test
    fun saveAsksForOneJobThatRunsTheSavesAndAsksAgainWhileAnyAreLeft() {
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

        ImportSaveWorker.schedule(context)
        ImportSaveWorker.schedule(context)

        val job = WorkManager.getInstance(context).getWorkInfosForUniqueWork("import-save").get().single()
        assertEquals(1, runs, "a second Save while the first job runs or waits adds none")
        assertEquals(WorkInfo.State.ENQUEUED, job.state, "saves are left, so it waits to run again")
        assertEquals(1, job.runAttemptCount)
    }
}
