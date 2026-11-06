package io.github.solcott.activitytracker.android

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat

/** The Uploads channel, shared by [OutboxWorker]'s progress and [ImportSaveWorker]'s and [DataExportWorker]'s foreground notifications. */
private const val UPLOADS_CHANNEL_ID = "uploads"

/**
 * A quiet, ongoing progress notification titled [title] on the Uploads channel, creating the channel the first time; the caller adds the
 * text and progress.
 */
internal fun uploadsProgressNotification(context: Context, title: String): NotificationCompat.Builder {
    val manager = context.getSystemService(NotificationManager::class.java)
    if (manager.getNotificationChannel(UPLOADS_CHANNEL_ID) == null) {
        manager.createNotificationChannel(
            NotificationChannel(UPLOADS_CHANNEL_ID, context.getString(R.string.uploads_channel), NotificationManager.IMPORTANCE_LOW)
        )
    }
    return NotificationCompat.Builder(context, UPLOADS_CHANNEL_ID)
        .setSmallIcon(android.R.drawable.stat_sys_upload)
        .setContentTitle(title)
        .setOngoing(true)
        .setOnlyAlertOnce(true)
        .setSilent(true)
        .setCategory(NotificationCompat.CATEGORY_PROGRESS)
        .setPriority(NotificationCompat.PRIORITY_LOW)
}
