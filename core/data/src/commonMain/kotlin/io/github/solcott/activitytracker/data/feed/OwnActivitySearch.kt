package io.github.solcott.activitytracker.data.feed

import io.github.solcott.activitytracker.atproto.runSuspendCatching
import io.github.solcott.activitytracker.data.activity.MatchSpan
import io.github.solcott.activitytracker.data.activity.OwnActivity
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

/** [FeedRepository.ownActivitiesInRange]'s [activities], and whether the AppView had more pages than it read ([truncated]). */
class OwnActivityRange(val activities: List<OwnActivity>, val truncated: Boolean)

/** The athlete's own activities near a batch's, oldest first, and whether every part of the batch's span was read ([complete]). */
class OwnActivitySearch(val activities: List<OwnActivity>, val complete: Boolean)

/**
 * The athlete's own activities that started from [margin] before each of [spans] to [margin] after its end, for a batch however many years
 * it spans. The spans are searched in windows of at most [maxWindow], only where the batch has activities; a window with more than
 * [maxPages] pages is halved and searched again, so a busy month is never cut off. A window that fails ends the search with what was found,
 * [OwnActivitySearch.complete] false. Never throws for a failed read.
 */
suspend fun FeedRepository.ownActivitiesAround(
    spans: List<MatchSpan>,
    margin: Duration = SEARCH_MARGIN,
    maxWindow: Duration = MAX_WINDOW,
    maxPages: Int = MAX_RANGE_PAGES,
): OwnActivitySearch {
    val found = LinkedHashMap<String, OwnActivity>()
    val pending = ArrayDeque(windows(spans, margin, maxWindow))
    var complete = true
    while (complete && pending.isNotEmpty()) {
        val window = pending.removeFirst()
        val read = runSuspendCatching { ownActivitiesInRange(window.start, window.endExclusive, maxPages = maxPages) }.getOrNull()
        when {
            read == null -> complete = false
            read.truncated && window.endExclusive - window.start > MIN_WINDOW -> {
                val middle = window.start + (window.endExclusive - window.start) / 2
                pending.addFirst(middle..<window.endExclusive)
                pending.addFirst(window.start..<middle)
            }
            else -> {
                read.activities.forEach { found[it.uri.toString()] = it }
                // A minute holding more than every page? Then only part of it was read.
                if (read.truncated) complete = false
            }
        }
    }
    return OwnActivitySearch(found.values.sortedBy { it.record.startedAt }, complete)
}

/** The padded [spans] merged into windows, oldest first, each at most [maxWindow] long unless one padded span is longer by itself. */
internal fun windows(spans: List<MatchSpan>, margin: Duration, maxWindow: Duration): List<OpenEndRange<Instant>> {
    val padded = spans.map { (it.startedAt - margin)..<(it.startedAt + it.elapsed + margin) }.sortedBy { it.start }
    val merged = mutableListOf<OpenEndRange<Instant>>()
    for (range in padded) {
        val joined =
            merged.lastOrNull()?.takeIf { range.start <= it.endExclusive }?.let { it.start..<maxOf(it.endExclusive, range.endExclusive) }
        if (joined != null && joined.endExclusive - joined.start <= maxWindow) {
            merged[merged.lastIndex] = joined
        } else {
            merged += range
        }
    }
    return merged
}

/** UTC-12 to UTC+14 apart, rounded: wide enough for any zone the athlete might place a zone-less clock in. */
val SEARCH_MARGIN: Duration = 14.hours

private val MAX_WINDOW = 31.days

/** A window this short isn't halved further. */
private val MIN_WINDOW = 1.minutes
