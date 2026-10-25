package io.github.solcott.activitytracker.location

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn

/**
 * Starts the app's location foreground service when a recording starts (or one is offered back), so it goes on with the screen off. The
 * service lives in the app module, since it needs the app's icon and strings, answers [ACTION_START] in this package, and stops itself once
 * the recorder is idle. It's started from the athlete's tap, as Android requires of a location service without background-location
 * permission.
 */
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
@Inject
class AndroidRecordingForeground(private val context: Context) : RecordingForeground {
    private var running = false

    override fun statusChanged(status: RecordingState.Status) {
        val active = status != RecordingState.Status.Idle
        if (active && !running) ContextCompat.startForegroundService(context, Intent(ACTION_START).setPackage(context.packageName))
        running = active
    }

    companion object {
        const val ACTION_START = "io.github.solcott.activitytracker.recording.START"
        const val ACTION_PAUSE = "io.github.solcott.activitytracker.recording.PAUSE"
        const val ACTION_RESUME = "io.github.solcott.activitytracker.recording.RESUME"
    }
}
