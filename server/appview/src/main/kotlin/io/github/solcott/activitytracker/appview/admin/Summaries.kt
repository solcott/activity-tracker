package io.github.solcott.activitytracker.appview.admin

import io.github.solcott.activitytracker.atproto.runSuspendCatching
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import org.slf4j.LoggerFactory

/** One page's summary as read: its [tiles], or null when they couldn't be read in time. */
class PageSummary(val page: AdminPage, val tiles: List<Tile>?) {
    /** The worst status among the tiles, which the navigation flags: `warn` when none could be read, none when all are fine. */
    val worst: TileStatus?
        get() = tiles?.mapNotNull { it.status }?.filter { it != TileStatus.Ok }?.maxOrNull() ?: TileStatus.Warn.takeIf { tiles == null }
}

/**
 * Every page's summary (admin/admin-site), read concurrently, each within [timeout], and kept for [ttl], so Home's refresh and every open
 * tab's navigation flags cost at most one set of summary queries per [ttl]. A page whose summary throws or times out comes back unavailable
 * without holding up the others. Summaries are the same for every admin, so one cached read serves all of them.
 */
class Summaries(
    private val pages: () -> List<AdminPage>,
    private val clock: Clock,
    private val ttl: Duration = TTL,
    private val timeout: Duration = TIMEOUT,
) {
    private val lock = Mutex()
    private var cached: Pair<Instant, List<PageSummary>>? = null

    suspend fun read(): List<PageSummary> = lock.withLock {
        val now = clock.now()
        cached?.takeIf { now - it.first < ttl }?.second ?: readAll(now).also { cached = now to it }
    }

    private suspend fun readAll(now: Instant): List<PageSummary> = coroutineScope {
        pages()
            .map { page ->
                async {
                    // withTimeoutOrNull, not withTimeout: a timeout is a cancellation, which runSuspendCatching rethrows.
                    val tiles = runSuspendCatching {
                        withTimeoutOrNull(timeout) { page.summary(now) }
                    }
                        .onSuccess { if (it == null) log.warn("Summary of admin page {} timed out", page.path) }
                        .onFailure { log.warn("Summary of admin page {} failed", page.path, it) }
                        .getOrNull()
                    PageSummary(page, tiles)
                }
            }
            .awaitAll()
    }

    companion object {
        val TTL = 10.seconds
        val TIMEOUT = 5.seconds
        private val log = LoggerFactory.getLogger(Summaries::class.java)
    }
}
