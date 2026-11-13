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
import kotlinx.coroutines.MainScope
import platform.BackgroundTasks.BGProcessingTask
import platform.Foundation.NSDate
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
        registerProcessingTask(IDENTIFIER) { run(it, fitDecoder) }
    }

    /** Asks iOS for a run with a connection, no sooner than [after]; it replaces any run already asked for. */
    fun schedule(after: Instant? = null) =
        submitProcessingTask(IDENTIFIER, needsNetwork = true, what = "Background uploads") {
            if (after != null) earliestBeginDate = NSDate.dateWithTimeIntervalSince1970(after.epochSeconds.toDouble())
        }

    private fun run(task: BGProcessingTask, fitDecoder: FitDecoder) =
        task.runIn(scope, reschedule = { schedule() }) {
            // Whatever failed (restoring the session, building the graph) is worth another try later; what's left stays queued.
            val outcome = runSuspendCatching { appGraph(fitDecoder).uploadQueuedWrites() }.getOrElse { PacerOutcome.Failed }
            when (outcome) {
                is PacerOutcome.Paused -> schedule(outcome.until)
                PacerOutcome.Failed -> schedule(Clock.System.now() + OutboxPacer.MAX_BACKOFF)
                PacerOutcome.Idle,
                null -> Unit
            }
            outcome != PacerOutcome.Failed
        }
}
