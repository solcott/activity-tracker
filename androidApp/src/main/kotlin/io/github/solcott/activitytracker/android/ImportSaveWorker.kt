package io.github.solcott.activitytracker.android

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import io.github.solcott.activitytracker.app.background.runImportSaves
import io.github.solcott.activitytracker.data.db.ImportSaveCounts
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeMark
import kotlin.time.TimeSource

/**
 * Saves batch imports with the app in the background or killed (feed/batch-import): a `dataSync` foreground job showing "Saving N of M",
 * asked for when the athlete taps Save, so Android doesn't freeze the save once they leave. WorkManager runs it again after the process is
 * killed; a run that starts in the background may not get the foreground, and goes on without it until stopped. Each activity is saved for
 * good as it's queued, so any run picks up where the last stopped. Uploading stays [OutboxWorker]'s.
 */
class ImportSaveWorker
internal constructor(
    context: Context,
    params: WorkerParameters,
    /** Runs the signed-in account's saves, telling the progress; whether saves are left, or null when no one is signed in. */
    private val runSaves: suspend (onProgress: suspend (ImportSaveCounts?) -> Unit) -> Boolean?,
) : CoroutineWorker(context, params) {
    /** The constructor WorkManager calls: the saves run in the app's background session graph. */
    constructor(
        context: Context,
        params: WorkerParameters,
    ) : this(context, params, { onProgress -> (context.applicationContext as ActivityTrackerApplication).graph.runImportSaves(onProgress) })

    override suspend fun doWork(): Result {
        foreground(null)
        // Each update re-posts the foreground notification, so the count moves at most once a second.
        var shown: TimeMark? = null
        val left =
            runSaves({ counts ->
                if (counts != null && shown.let { it == null || it.elapsedNow() >= 1.seconds }) {
                    shown = TimeSource.Monotonic.markNow()
                    foreground(counts)
                }
            })
        return if (left == true) Result.retry() else Result.success()
    }

    override suspend fun getForegroundInfo(): ForegroundInfo = foregroundInfo(null)

    /** Shows [counts] as the job's foreground notification; refused while the app is in the background, the save goes on regardless. */
    @Suppress("TooGenericExceptionCaught", "SwallowedException") // ForegroundServiceStartNotAllowedException and IllegalStateException.
    private suspend fun foreground(counts: ImportSaveCounts?) {
        try {
            setForeground(foregroundInfo(counts))
        } catch (_: Exception) {
            // Saving goes on without the notification, as a plain job.
        }
    }

    private fun foregroundInfo(counts: ImportSaveCounts?): ForegroundInfo {
        ensureChannel()
        val context = applicationContext
        val builder =
            NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_sys_upload)
                .setContentTitle(context.getString(R.string.saving_title))
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setSilent(true)
                .setCategory(NotificationCompat.CATEGORY_PROGRESS)
                .setPriority(NotificationCompat.PRIORITY_LOW)
        if (counts == null) builder.setProgress(0, 0, true)
        else {
            val saved = counts.done + counts.skipped
            builder.setContentText(context.getString(R.string.saving_text, saved, counts.total)).setProgress(counts.total, saved, false)
        }
        return ForegroundInfo(NOTIFICATION_ID, builder.build(), ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
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
        private const val NAME = "import-save"

        /** The Uploads channel, [OutboxWorker]'s. */
        private const val CHANNEL_ID = "uploads"

        /** Apart from the recording's and the uploads', so none replaces another. */
        private const val NOTIFICATION_ID = 3

        /**
         * Asks for a run now, expedited so it starts while the app is still in the foreground; one already running finishes every save,
         * those written after it started included.
         */
        fun schedule(context: Context) {
            val request =
                OneTimeWorkRequestBuilder<ImportSaveWorker>().setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST).build()
            WorkManager.getInstance(context).enqueueUniqueWork(NAME, ExistingWorkPolicy.KEEP, request)
        }
    }
}
