package io.github.solcott.activitytracker.android

import android.content.Context
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import io.github.solcott.activitytracker.app.background.uploadQueuedWrites
import io.github.solcott.activitytracker.data.activity.PacerOutcome
import java.util.concurrent.TimeUnit
import kotlin.time.Clock
import kotlin.time.Duration

/**
 * Sends queued writes while the app is closed (data/outbox): one pass with a connection, then, while the PDS has paused the upload, another
 * pass once the pause ends, until nothing is left or no one is signed in.
 */
class OutboxWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val graph = (applicationContext as ActivityTrackerApplication).graph
        return when (val outcome = graph.uploadQueuedWrites()) {
            null,
            PacerOutcome.Idle -> Result.success()
            PacerOutcome.Failed -> Result.retry()
            is PacerOutcome.Paused -> {
                schedule(applicationContext, after = outcome.until - Clock.System.now(), ExistingWorkPolicy.APPEND_OR_REPLACE)
                Result.success()
            }
        }
    }

    companion object {
        private const val NAME = "outbox-drain"

        /** Asks for a pass with a connection, no sooner than [after]; [policy] keeps a pass already waiting by default. */
        fun schedule(context: Context, after: Duration = Duration.ZERO, policy: ExistingWorkPolicy = ExistingWorkPolicy.KEEP) {
            val request =
                OneTimeWorkRequestBuilder<OutboxWorker>()
                    .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                    .setInitialDelay(after.inWholeMilliseconds.coerceAtLeast(0), TimeUnit.MILLISECONDS)
                    .build()
            WorkManager.getInstance(context).enqueueUniqueWork(NAME, policy, request)
        }
    }
}

/** Asks for a background pass each time the app leaves the foreground; the pass ends at once when nothing is queued. */
class ScheduleUploadsOnStop(private val context: Context) : DefaultLifecycleObserver {
    override fun onStop(owner: LifecycleOwner) = OutboxWorker.schedule(context)
}
