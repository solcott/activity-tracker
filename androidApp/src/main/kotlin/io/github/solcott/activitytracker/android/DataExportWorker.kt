package io.github.solcott.activitytracker.android

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.Constraints
import androidx.work.NetworkType
import androidx.work.WorkerParameters
import io.github.solcott.activitytracker.app.background.runDataExport
import io.github.solcott.activitytracker.data.db.DataExportEntity
import io.github.solcott.activitytracker.data.export.DataExportProgress

/**
 * Runs the athlete's data export with the app in the background or killed (auth/data-export): a `dataSync` foreground job showing how far
 * it's got, asked for when they start it, so Android doesn't freeze it once they leave. WorkManager runs it again after the process is
 * killed, once there's a connection; every item is staged for good as it's read, so any run picks up where the last stopped. When it's
 * ready, a notification says so and opens the Account page.
 */
internal class DataExportWorker(
    context: Context,
    params: WorkerParameters,
    /** Runs the signed-in account's export, telling its progress; whether it's still to run, or null when no one is signed in. */
    runExport: suspend (onProgress: suspend (DataExportProgress?) -> Unit) -> Boolean?,
) : AccountJobWorker<DataExportProgress>(context, params, runExport) {
    /** The constructor WorkManager calls: the export runs in the app's background session graph. */
    constructor(
        context: Context,
        params: WorkerParameters,
    ) : this(context, params, { onProgress -> (context.applicationContext as ActivityTrackerApplication).graph.runDataExport(onProgress) })

    override val maxAttempts = MAX_ATTEMPTS
    override val notificationId = PROGRESS_ID
    override val notificationTitle = R.string.export_title

    override fun describe(builder: NotificationCompat.Builder, progress: DataExportProgress) {
        if (progress.activitiesTotal <= 0) builder.setProgress(0, 0, true)
        else
            builder
                .setContentText(applicationContext.getString(R.string.export_text, progress.activitiesDone, progress.activitiesTotal))
                .setProgress(progress.activitiesTotal, progress.activitiesDone, false)
    }

    override fun finished(last: DataExportProgress?) {
        if (last?.state == DataExportEntity.STATE_READY) notifyReady(applicationContext)
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
        fun schedule(context: Context) =
            scheduleAccountJob<DataExportWorker>(context, NAME, Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())

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
