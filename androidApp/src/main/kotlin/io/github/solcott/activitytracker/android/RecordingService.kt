package io.github.solcott.activitytracker.android

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import io.github.solcott.activitytracker.data.units.UnitConversions
import io.github.solcott.activitytracker.data.units.UnitSystem
import io.github.solcott.activitytracker.data.units.deviceRegion
import io.github.solcott.activitytracker.data.units.formatDecimal
import io.github.solcott.activitytracker.location.AndroidRecordingForeground
import io.github.solcott.activitytracker.location.RecordingState
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Keeps the process alive with a location foreground service while a recording is in progress or paused (record/gps-recording), and shows
 * its elapsed time and distance in an ongoing notification with Pause or Resume. It collects nothing itself: the app's `ActivityRecorder`
 * does, and this only follows its state, stopping once it's idle. Started by [AndroidRecordingForeground].
 */
class RecordingService : Service() {
    private val scope = MainScope()
    private var following: Job? = null

    private val recorder
        get() = (application as ActivityTrackerApplication).graph.activityRecorder

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            AndroidRecordingForeground.ACTION_PAUSE -> recorder.pause()
            AndroidRecordingForeground.ACTION_RESUME -> recorder.resume()
        }
        // Android needs the service in the foreground within seconds of every start.
        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            notification(recorder.state.value),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION,
        )
        if (following == null) following = scope.launch { follow() }
        // A killed recording is recovered on the next launch, never restarted from the background.
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    /** Updates the notification as the recording changes, and about every [REFRESH] while it records; stops once it's idle. */
    private suspend fun follow() {
        recorder.state.collectLatest { state ->
            if (state.status == RecordingState.Status.Idle) {
                ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
                stopSelf()
                return@collectLatest
            }
            while (true) {
                notify(state)
                if (state.status != RecordingState.Status.Recording) break
                delay(REFRESH)
            }
        }
    }

    @Suppress("MissingPermission") // Without notification permission the update is dropped, and recording goes on.
    private fun notify(state: RecordingState) {
        if (NotificationManagerCompat.from(this).areNotificationsEnabled()) {
            NotificationManagerCompat.from(this).notify(NOTIFICATION_ID, notification(state))
        }
    }

    private fun notification(state: RecordingState): android.app.Notification {
        ensureChannel()
        val paused = state.status != RecordingState.Status.Recording
        val action = if (paused) AndroidRecordingForeground.ACTION_RESUME else AndroidRecordingForeground.ACTION_PAUSE
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_recording)
            .setContentTitle(getString(if (paused) R.string.recording_paused_title else R.string.recording_title))
            .setContentText(
                getString(R.string.recording_text, elapsedText(state.elapsed(Clock.System.now())), distanceText(state.distanceMeters))
            )
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_WORKOUT)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setContentIntent(openRecording())
            .addAction(0, getString(if (paused) R.string.recording_resume else R.string.recording_pause), serviceIntent(action))
            .build()
    }

    /** Opens the app on the recording screen. */
    private fun openRecording(): PendingIntent =
        PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).setAction(MainActivity.ACTION_OPEN_RECORDING).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

    private fun serviceIntent(action: String): PendingIntent =
        PendingIntent.getService(
            this,
            action.hashCode(),
            Intent(this, RecordingService::class.java).setAction(action),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

    private fun ensureChannel() {
        val manager = getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(CHANNEL_ID) == null) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, getString(R.string.recording_channel), NotificationManager.IMPORTANCE_LOW)
            )
        }
    }

    private companion object {
        const val CHANNEL_ID = "recording"
        const val NOTIFICATION_ID = 1
        val REFRESH = 5.seconds
    }
}

private const val SECONDS_PER_HOUR = 3600L
private const val SECONDS_PER_MINUTE = 60L

/** `h:mm:ss`, or `m:ss` under an hour. */
private fun elapsedText(duration: Duration): String {
    val total = duration.inWholeSeconds
    val (h, m, s) = Triple(total / SECONDS_PER_HOUR, total % SECONDS_PER_HOUR / SECONDS_PER_MINUTE, total % SECONDS_PER_MINUTE)
    fun two(n: Long) = n.toString().padStart(2, '0')
    return if (h > 0) "$h:${two(m)}:${two(s)}" else "$m:${two(s)}"
}

/** In the device region's units: the athlete's chosen ones belong to their session, which the service doesn't see. */
private fun Context.distanceText(meters: Double): String {
    val units = UnitSystem.defaultFor(deviceRegion())
    val unit = getString(if (units == UnitSystem.Imperial) R.string.unit_mi else R.string.unit_km)
    return "${formatDecimal(UnitConversions.toDistanceUnit(meters, units), 1)} $unit"
}
