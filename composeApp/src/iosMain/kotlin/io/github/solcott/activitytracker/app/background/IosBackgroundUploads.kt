package io.github.solcott.activitytracker.app.background

import io.github.solcott.activitytracker.activityfile.FitDecoder
import io.github.solcott.activitytracker.app.appGraph
import io.github.solcott.activitytracker.atproto.runSuspendCatching
import io.github.solcott.activitytracker.data.activity.OutboxPacer
import io.github.solcott.activitytracker.data.activity.PacerOutcome
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.ObjCObjectVar
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.value
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import platform.BackgroundTasks.BGProcessingTask
import platform.BackgroundTasks.BGProcessingTaskRequest
import platform.BackgroundTasks.BGTaskScheduler
import platform.Foundation.NSDate
import platform.Foundation.NSError
import platform.Foundation.NSLog
import platform.Foundation.NSString
import platform.Foundation.dateWithTimeIntervalSince1970

/**
 * Queued writes upload while the app is in the background (data/outbox), through a `BGProcessingTask` that needs a connection. iOS runs it
 * when it chooses, no sooner than asked: [schedule] asks when the app leaves the foreground, and each run asks again while the PDS's pause
 * or a failure leaves writes to send. Used by `iosApp/iosApp/iOSApp.swift`.
 */
@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
object IosBackgroundUploads {
    /** Listed in `BGTaskSchedulerPermittedIdentifiers` in `iosApp/Info.plist`. */
    const val IDENTIFIER = "io.github.solcott.activitytracker.outbox"

    private val scope = MainScope()

    /** Registers the task's handler; iOS requires it before the app finishes launching. */
    fun register(fitDecoder: FitDecoder) {
        BGTaskScheduler.sharedScheduler.registerForTaskWithIdentifier(IDENTIFIER, usingQueue = null) { task ->
            run(task as BGProcessingTask, fitDecoder)
        }
    }

    /** Asks iOS for a run with a connection, no sooner than [after]; it replaces any run already asked for. */
    fun schedule(after: Instant? = null) {
        val request = BGProcessingTaskRequest(IDENTIFIER)
        request.requiresNetworkConnectivity = true
        request.requiresExternalPower = false
        if (after != null) request.earliestBeginDate = NSDate.dateWithTimeIntervalSince1970(after.epochSeconds.toDouble())
        memScoped {
            val error = alloc<ObjCObjectVar<NSError?>>()
            // iOS refuses when Background App Refresh is off, too many requests are pending, or (the simulator) always; the writes then
            // wait for the app to open, so say why where Console shows it.
            if (!BGTaskScheduler.sharedScheduler.submitTaskRequest(request, error.ptr)) {
                // A Kotlin String reaches a C variadic as a C string, so %@ needs an NSString.
                NSLog("Background uploads not scheduled: %@", (error.value?.localizedDescription ?: "unknown error") as NSString)
            }
        }
    }

    private fun run(task: BGProcessingTask, fitDecoder: FitDecoder) {
        val job = scope.launch {
            // Whatever failed (restoring the session, building the graph) is worth another try later.
            val outcome = runSuspendCatching { appGraph(fitDecoder).uploadQueuedWrites() }.getOrElse { PacerOutcome.Failed }
            when (outcome) {
                is PacerOutcome.Paused -> schedule(outcome.until)
                PacerOutcome.Failed -> schedule(Clock.System.now() + OutboxPacer.MAX_BACKOFF)
                PacerOutcome.Idle,
                null -> Unit
            }
            task.setTaskCompletedWithSuccess(outcome != PacerOutcome.Failed)
        }
        task.expirationHandler = {
            // Out of time: what's left is still queued, so ask again.
            job.cancel()
            schedule()
            task.setTaskCompletedWithSuccess(false)
        }
    }
}
