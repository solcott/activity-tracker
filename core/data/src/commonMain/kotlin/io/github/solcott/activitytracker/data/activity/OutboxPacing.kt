package io.github.solcott.activitytracker.data.activity

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.github.solcott.activitytracker.atproto.xrpc.RateLimit
import io.github.solcott.activitytracker.lexicon.Did
import kotlin.concurrent.atomics.AtomicReference
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex

/**
 * Each account's upload pacing, app-wide (data/outbox): a background job's session and the app's are separate session graphs, and they
 * share one [AccountPacing] so only one drains at a time and both honour the same pause.
 */
@SingleIn(AppScope::class)
@Inject
@OptIn(ExperimentalAtomicApi::class)
class OutboxPacing {
    private val accounts = AtomicReference(emptyMap<Did, AccountPacing>())

    fun of(did: Did): AccountPacing {
        while (true) {
            val current = accounts.load()
            current[did]?.let {
                return it
            }
            val created = AccountPacing()
            if (accounts.compareAndSet(current, current + (did to created))) return created
        }
    }
}

/** One account's drain lock, the PDS's stated [rateLimits], and the pauses it set. */
class AccountPacing internal constructor() {
    /** Held by every drain of this account, whichever session runs it. */
    val lock = Mutex()

    /**
     * Each limit the account's PDS stated, last heard, by its policy: record writes and blob uploads count against different limits, and a
     * response states only the one it counted against.
     */
    val rateLimits: StateFlow<Map<String, RateLimit>>
        field = MutableStateFlow(emptyMap())

    /** Until when the PDS asked for nothing more to be sent: a 429. */
    val pausedUntil: StateFlow<Instant?>
        field = MutableStateFlow<Instant?>(null)

    /** Until when bulk writes wait, leaving the rest of the limit for the athlete's own writes. */
    val bulkPausedUntil: StateFlow<Instant?>
        field = MutableStateFlow<Instant?>(null)

    /** The later pause in effect, for the feed: while the import waits, so does what the athlete sees uploading. */
    val shownPause: Flow<Instant?> = combine(pausedUntil, bulkPausedUntil) { full, bulk -> listOfNotNull(full, bulk).maxOrNull() }

    fun heard(limit: RateLimit) {
        rateLimits.update { it + ((limit.policy ?: "${limit.limit}") to limit) }
    }

    fun pause(until: Instant) {
        pausedUntil.value = until
    }

    fun pauseBulk(until: Instant) {
        bulkPausedUntil.value = until
    }

    /** Whether a pause is in effect at [now]; a pause that ended is cleared. */
    fun isPaused(now: Instant): Boolean = active(pausedUntil, now)

    fun isBulkPaused(now: Instant): Boolean = isPaused(now) || active(bulkPausedUntil, now)

    private fun active(flow: MutableStateFlow<Instant?>, now: Instant): Boolean {
        val until = flow.value
        val active = until != null && until > now
        if (until != null && !active) flow.compareAndSet(until, null)
        return active
    }
}
