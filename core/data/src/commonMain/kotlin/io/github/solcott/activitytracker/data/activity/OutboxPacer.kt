package io.github.solcott.activitytracker.data.activity

import co.touchlab.kermit.Logger
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.github.solcott.activitytracker.atproto.runSuspendCatching
import io.github.solcott.activitytracker.data.db.AppDatabase
import io.github.solcott.activitytracker.data.di.SessionClients
import io.github.solcott.activitytracker.data.di.SessionScope
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Sends the session's queued writes without anyone asking (data/outbox): [run] drains whenever writes are queued, when the PDS's pause
 * ends, and again after a failure that may pass, waiting longer after each in a row. [runUntilIdleOrPaused] is one pass, for a background
 * job.
 */
@SingleIn(SessionScope::class)
@Inject
class OutboxPacer(
    private val db: AppDatabase,
    private val clients: SessionClients,
    private val activities: ActivityRepository,
    private val clock: Clock,
    logger: Logger,
) {
    private val log = logger.withTag("OutboxPacer")
    private val pacing
        get() = clients.pacing

    /** Until when the server paused the upload, while writes are still to send; null otherwise. For the feed. */
    val pausedUntil: Flow<Instant?> =
        combine(pacing.shownPause, activities.pendingWrites) { pause, pending -> pause?.takeIf { pending > 0 } }

    /** Drains for as long as the session lives; cancel it to stop. */
    suspend fun run(): Nothing {
        val queue = db.outbox().queueState(clients.did.value)
        var backoff = INITIAL_BACKOFF
        while (true) {
            // Taken before the pass, so a write queued during or after it wakes the next one.
            val before = queue.first()
            val outcome = runUntilIdleOrPaused()
            val now = clock.now()
            if (outcome == PacerOutcome.Failed) {
                delay(backoff)
                backoff = (backoff * 2).coerceAtMost(MAX_BACKOFF)
                continue
            }
            backoff = INITIAL_BACKOFF
            val changed = suspend { queue.first { it != before } }
            when (outcome) {
                is PacerOutcome.Paused -> withTimeoutOrNull((outcome.until - now).coerceAtLeast(Duration.ZERO)) { changed() }
                else -> changed()
            }
        }
    }

    /** Sends what it can now: [PacerOutcome.Idle] when nothing more can go (all sent, or held by a rejected write), else why it stopped. */
    suspend fun runUntilIdleOrPaused(): PacerOutcome {
        val run = runSuspendCatching {
            activities.syncRun()
        }
            .getOrElse { e ->
                log.w(e) { "Sending queued writes failed" }
                return PacerOutcome.Failed
            }
        val now = clock.now()
        return when {
            run.failed -> PacerOutcome.Failed
            run.notApplied == 0 -> PacerOutcome.Idle
            pacing.isPaused(now) -> PacerOutcome.Paused(pacing.pausedUntil.value ?: now)
            pacing.isBulkPaused(now) -> PacerOutcome.Paused(pacing.bulkPausedUntil.value ?: now)
            else -> PacerOutcome.Idle
        }
    }

    companion object {
        val INITIAL_BACKOFF: Duration = 15.seconds
        val MAX_BACKOFF: Duration = 15.minutes
    }
}

/** How a pass of [OutboxPacer] ended. */
sealed interface PacerOutcome {
    /** Nothing more can be sent now: everything went, or what's left waits on the athlete (rejected writes). */
    data object Idle : PacerOutcome

    /** The PDS asked to wait until [until]. */
    data class Paused(val until: Instant) : PacerOutcome

    /** A failure that may pass, such as no connection. */
    data object Failed : PacerOutcome
}
