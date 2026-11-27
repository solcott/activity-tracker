package io.github.solcott.activitytracker.android

import android.content.Context
import android.content.pm.ServiceInfo
import androidx.core.app.NotificationCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeMark
import kotlin.time.TimeSource

/**
 * A signed-in account's long job ([ImportSaveWorker], [DataExportWorker]) run as a `dataSync` foreground job showing its progress [P] on
 * the Uploads channel, so Android doesn't freeze it once the athlete leaves. A run that throws leaves work as surely as one that stops
 * early: both are retried until [maxAttempts], after which the next session start resumes it. Each subclass keeps the `(Context,
 * WorkerParameters)` constructor WorkManager calls by reflection.
 */
internal abstract class AccountJobWorker<P : Any>(
    context: Context,
    params: WorkerParameters,
    /** Runs the signed-in account's job, telling its progress; whether any is left, or null when no one is signed in. */
    private val run: suspend (onProgress: suspend (P?) -> Unit) -> Boolean?,
) : CoroutineWorker(context, params) {
    /** Runs of one request before a job that keeps failing waits for the app instead. */
    protected abstract val maxAttempts: Int

    /** The foreground notification's id, apart from every other notification's so none replaces another. */
    protected abstract val notificationId: Int

    /** The foreground notification's title. */
    protected abstract val notificationTitle: Int

    /** Adds [progress]'s text and bar to the foreground notification. */
    protected abstract fun describe(builder: NotificationCompat.Builder, progress: P)

    /** Called once a run returns or throws (not when stopped), with the last progress it told. */
    protected open fun finished(last: P?) = Unit

    override suspend fun doWork(): Result {
        foreground(null)
        // Each update re-posts the foreground notification, so it moves at most once a second.
        var shown: TimeMark? = null
        var last: P? = null
        val left =
            try {
                run(
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
        finished(last)
        return if (left == true && runAttemptCount < maxAttempts) Result.retry() else Result.success()
    }

    override suspend fun getForegroundInfo(): ForegroundInfo = foregroundInfo(null)

    /** Shows [progress] as the job's foreground notification; refused while the app is in the background, the job goes on regardless. */
    @Suppress("TooGenericExceptionCaught", "SwallowedException") // ForegroundServiceStartNotAllowedException and IllegalStateException.
    private suspend fun foreground(progress: P?) {
        try {
            setForeground(foregroundInfo(progress))
        } catch (_: Exception) {
            // The job goes on without the notification, as a plain job.
        }
    }

    private fun foregroundInfo(progress: P?): ForegroundInfo {
        val context = applicationContext
        val builder = uploadsProgressNotification(context, context.getString(notificationTitle))
        if (progress == null) builder.setProgress(0, 0, true) else describe(builder, progress)
        return ForegroundInfo(notificationId, builder.build(), ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
    }
}

/**
 * Asks for a run of [W] as the unique work [name] now, expedited so it starts while the app is still in the foreground; one already running
 * or waiting is kept.
 */
internal inline fun <reified W : AccountJobWorker<*>> scheduleAccountJob(
    context: Context,
    name: String,
    constraints: Constraints = Constraints.NONE,
) {
    val request =
        OneTimeWorkRequestBuilder<W>().setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST).setConstraints(constraints).build()
    WorkManager.getInstance(context).enqueueUniqueWork(name, ExistingWorkPolicy.KEEP, request)
}
