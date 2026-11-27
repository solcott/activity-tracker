package io.github.solcott.activitytracker.android

import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.work.WorkerParameters
import io.github.solcott.activitytracker.app.background.runImportSaves
import io.github.solcott.activitytracker.data.db.ImportSaveCounts

/**
 * Saves batch imports with the app in the background or killed (feed/batch-import): a `dataSync` foreground job showing "Saving N of M",
 * asked for when the athlete taps Save, so Android doesn't freeze the save once they leave. WorkManager runs it again after the process is
 * killed; a run that starts in the background may not get the foreground, and goes on without it until stopped. Each activity is saved for
 * good as it's queued, so any run picks up where the last stopped. Uploading stays [OutboxWorker]'s.
 */
internal class ImportSaveWorker(
    context: Context,
    params: WorkerParameters,
    /** Runs the signed-in account's saves, telling the progress; whether saves are left, or null when no one is signed in. */
    runSaves: suspend (onProgress: suspend (ImportSaveCounts?) -> Unit) -> Boolean?,
) : AccountJobWorker<ImportSaveCounts>(context, params, runSaves) {
    /** The constructor WorkManager calls: the saves run in the app's background session graph. */
    constructor(
        context: Context,
        params: WorkerParameters,
    ) : this(context, params, { onProgress -> (context.applicationContext as ActivityTrackerApplication).graph.runImportSaves(onProgress) })

    override val maxAttempts = MAX_ATTEMPTS
    override val notificationId = NOTIFICATION_ID
    override val notificationTitle = R.string.saving_title

    override fun describe(builder: NotificationCompat.Builder, progress: ImportSaveCounts) {
        val saved = progress.done + progress.skipped
        builder
            .setContentText(applicationContext.getString(R.string.saving_text, saved, progress.total))
            .setProgress(progress.total, saved, false)
    }

    companion object {
        private const val NAME = "import-save"

        /** Runs of one request before a save that keeps failing waits for the app instead. */
        internal const val MAX_ATTEMPTS = 10

        /** Apart from the recording's and the uploads', so none replaces another. */
        private const val NOTIFICATION_ID = 3

        /**
         * Asks for a run now, expedited so it starts while the app is still in the foreground; one already running finishes every save,
         * those written after it started included.
         */
        fun schedule(context: Context) = scheduleAccountJob<ImportSaveWorker>(context, NAME)
    }
}
