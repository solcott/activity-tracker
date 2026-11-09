package io.github.solcott.activitytracker.android

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import io.github.solcott.activitytracker.app.background.runDataExport
import io.github.solcott.activitytracker.data.db.DataExportEntity
import io.github.solcott.activitytracker.data.export.DataExportProgress
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeMark
import kotlin.time.TimeSource

/**
 * Runs the athlete's data export with the app in the background or killed (auth/data-export): a `dataSync` foreground job showing how far
 * it's got, asked for when they start it, so Android doesn't freeze it once they leave. WorkManager runs it again after the process is
 * killed, once there's a connection; every item is staged for good as it's read, so any run picks up where the last stopped. When it's
 * ready, a notification says so and opens the Account page.
 */
class DataExportWorker
internal constructor(
    context: Context,
    params: WorkerParameters,
    /** Runs the signed-in account's export, telling its progress; whether it's still to run, or null when no one is signed in. */
    private val runExport: suspend (onProgress: suspend (DataExportProgress?) -> Unit) -> Boolean?,
) : CoroutineWorker(context, params) {
    /** The constructor WorkManager calls: the export runs in the app's background session graph. */
    constructor(
        context: Context,
        params: WorkerParameters,
    ) : this(context, params, { onProgress -> (context.applicationContext as ActivityTrackerApplication).graph.runDataExport(onProgress) })

    override suspend fun doWork(): Result {
        foreground(null)
        // Each update re-posts the foreground notification, so it moves at most once a second.
        var shown: TimeMark? = null
        var last: DataExportProgress? = null
        val left =
            try {
                runExport(
                    { progress ->
                        last = progress
                        if (progress != null && shown.let { it == null || it.elapsedNow() >= 1.seconds }) {
                            shown = TimeSource.Monotonic.markNow()
                            foreground(progress)
                        }
                    },
                )
            } catch (e: CancellationException) {
                throw e
            } catch (@Suppress("TooGenericExceptionCaught") _: Exception) {
                true
            }
        if (last?.state == DataExportEntity.STATE_READY) notifyReady(applicationContext)
        return if (left == true && runAttemptCount < MAX_ATTEMPTS) Result.retry() else Result.success()
    }

    override suspend fun getForegroundInfo(): ForegroundInfo = foregroundInfo(null)

    /** Shows [progress] as the job's foreground notification; refused while the app is in the background, the export goes on regardless. */
    @Suppress("TooGenericExceptionCaught", "SwallowedException") // ForegroundServiceStartNotAllowedException and IllegalStateException.
    private suspend fun foreground(progress: DataExportProgress?) {
        try {
            setForeground(foregroundInfo(progress))
        } catch (_: Exception) {
            // The export goes on without the notification, as a plain job.
        }
    }

    private fun foregroundInfo(progress: DataExportProgress?): ForegroundInfo {
        val context = applicationContext
        val builder = uploadsProgressNotification(context, context.getString(R.string.export_title))
        val counted = progress?.takeIf { it.activitiesTotal > 0 }
        when (counted) {
            null -> builder.setProgress(0, 0, true)
            else ->
                builder
                    .setContentText(context.getString(R.string.export_text, counted.activitiesDone, counted.activitiesTotal))
                    .setProgress(counted.activitiesTotal, counted.activitiesDone, false)
        }
        return ForegroundInfo(PROGRESS_ID, builder.build(), ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
    }

    companion object {
        private const val NAME = "data-export"
        private const val CHANNEL_ID = "exports"

        /** Runs of one request before an export that keeps failing waits for the app instead. */
        internal const val MAX_ATTEMPTS = 10

        /** Apart from the recording's, the uploads' and the saves', so none replaces another. */
        private const val PROGRESS_ID = 4
        internal const val READY_ID = 5

        /** Asks for a run now, expedited so it starts while the app is still in the foreground; one already running finishes the export. */
        fun schedule(context: Context) {
            val request =
                OneTimeWorkRequestBuilder<DataExportWorker>()
                    .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                    .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                    .build()
            WorkManager.getInstance(context).enqueueUniqueWork(NAME, ExistingWorkPolicy.KEEP, request)
        }

        /** Says the export is ready, opening the Account page when tapped; nothing when the athlete hasn't allowed notifications. */
        @Suppress("MissingPermission") // Checked: areNotificationsEnabled is false without POST_NOTIFICATIONS.
        internal fun notifyReady(context: Context) {
            val notifications = NotificationManagerCompat.from(context)
            if (!notifications.areNotificationsEnabled()) return
            val manager = context.getSystemService(NotificationManager::class.java)
            if (manager.getNotificationChannel(CHANNEL_ID) == null) {
                manager.createNotificationChannel(
                    NotificationChannel(CHANNEL_ID, context.getString(R.string.export_channel), NotificationManager.IMPORTANCE_DEFAULT),
                )
            }
            val open =
                PendingIntent.getActivity(
                    context,
                    0,
                    Intent(context, MainActivity::class.java).setAction(MainActivity.ACTION_OPEN_ACCOUNT),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                )
            val notification =
                NotificationCompat.Builder(context, CHANNEL_ID)
                    .setSmallIcon(android.R.drawable.stat_sys_download_done)
                    .setContentTitle(context.getString(R.string.export_ready_title))
                    .setContentText(context.getString(R.string.export_ready_text))
                    .setContentIntent(open)
                    .setAutoCancel(true)
                    .build()
            notifications.notify(READY_ID, notification)
        }
    }
}
