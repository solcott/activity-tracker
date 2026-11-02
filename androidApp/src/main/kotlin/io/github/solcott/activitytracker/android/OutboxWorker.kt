package io.github.solcott.activitytracker.android

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
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
import io.github.solcott.activitytracker.data.activity.ImportProgress
import io.github.solcott.activitytracker.data.activity.PacerOutcome
import java.util.concurrent.TimeUnit
import kotlin.time.Clock
import kotlin.time.Duration

/**
 * Sends queued writes while the app is closed (data/outbox): one pass with a connection, then, while the PDS has paused the upload, another
 * pass once the pause ends, until nothing is left or no one is signed in. Writes held only by the athlete's pause of imports ask for no
 * pass ([PacerOutcome.Idle]). While imports upload it shows their progress in a quiet notification (data/upload-queue), only when the
 * athlete allows notifications, and removes it once imports finish or are paused.
 */
class OutboxWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    private val notifications = NotificationManagerCompat.from(applicationContext)

    override suspend fun doWork(): Result {
        val graph = (applicationContext as ActivityTrackerApplication).graph
        val outcome = graph.uploadQueuedWrites(onImportProgress = ::showProgress)
        // A pass that will come back keeps the last progress shown; one that won't, for imports done or paused, takes it down.
        if (outcome == null || outcome == PacerOutcome.Idle) notifications.cancel(NOTIFICATION_ID)
        return when (outcome) {
            null,
            PacerOutcome.Idle -> Result.success()
            PacerOutcome.Failed -> Result.retry()
            is PacerOutcome.Paused -> {
                schedule(applicationContext, after = outcome.until - Clock.System.now(), ExistingWorkPolicy.APPEND_OR_REPLACE)
                Result.success()
            }
        }
    }

    @SuppressLint("MissingPermission") // Checked: posted only while notifications are allowed, and never asked for here.
    private fun showProgress(progress: ImportProgress?) {
        if (progress == null) {
            notifications.cancel(NOTIFICATION_ID)
            return
        }
        if (!notifications.areNotificationsEnabled()) return
        ensureChannel()
        val context = applicationContext
        val notification =
            NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_sys_upload)
                .setContentTitle(context.getString(R.string.uploads_title))
                .setContentText(context.getString(R.string.uploads_text, progress.uploaded, progress.saved))
                .setProgress(progress.saved, progress.uploaded, false)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setSilent(true)
                .setCategory(NotificationCompat.CATEGORY_PROGRESS)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build()
        notifications.notify(NOTIFICATION_ID, notification)
    }

    private fun ensureChannel() {
        val manager = applicationContext.getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(CHANNEL_ID) == null) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, applicationContext.getString(R.string.uploads_channel), NotificationManager.IMPORTANCE_LOW)
            )
        }
    }

    companion object {
        private const val NAME = "outbox-drain"
        private const val CHANNEL_ID = "uploads"

        /** Apart from the recording's (`RecordingService`), so the two never replace each other. */
        private const val NOTIFICATION_ID = 2

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
