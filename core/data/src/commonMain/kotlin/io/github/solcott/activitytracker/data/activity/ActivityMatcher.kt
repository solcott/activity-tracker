package io.github.solcott.activitytracker.data.activity

import io.github.solcott.activitytracker.activityfile.ImportedActivity
import io.github.solcott.activitytracker.lexicon.app.ActivityRecord
import io.github.solcott.activitytracker.lexicon.app.Sport
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/** What an imported activity repeats. */
sealed interface DuplicateOf {
    /** An activity already in the athlete's log. */
    data class Existing(val activity: OwnActivity) : DuplicateOf

    /** An earlier activity in the same batch (one file, or several picked together), by its index there. */
    data class InBatch(val index: Int) : DuplicateOf
}

/**
 * What [ActivityMatcher] needs of an import: when it [startedAt] (placed in the zone the athlete picked), how long it took, and whether its
 * file's times had no zone ([zoneless], a dive log's), which lets a whole number of hours off still match. Its [sport], [distanceMeters]
 * and [device], where known, tell an exact copy and a match of another sport apart.
 */
data class MatchSpan(
    val startedAt: Instant,
    val elapsed: Duration,
    val zoneless: Boolean,
    val sport: String? = null,
    val distanceMeters: Double? = null,
    val device: String? = null,
)

/** This activity as [ActivityMatcher] matches it, as its file's sport. */
fun ImportedActivity.matchSpan(sport: String? = type.sport): MatchSpan =
    MatchSpan(startedAt, elapsed, localStart != null, sport, distanceMeters, device)

/**
 * [hoursApart] is 0 for the same activity; otherwise it's one of the same length that many hours off, likely read in the wrong zone.
 * [exactCopy] when it's the very same recording (same start and length to the second, same distance and device); [sameSport] unless both
 * name a sport and they differ.
 */
data class ActivityMatch(val of: DuplicateOf, val hoursApart: Int = 0, val exactCopy: Boolean = false, val sameSport: Boolean = true)

/** How two of an athlete's activities likely belong in one combined activity. */
enum class Pairing {
    /** Recorded at the same time, on two devices. */
    Overlap,

    /** One after the other: a device change, a shuttle or a break. */
    Continuation,
}

/**
 * Finds the activities in an import that are already in the athlete's log, or appear twice in the file (two computers on one dive, say),
 * whatever their sport: the athlete decides whether a run that overlaps a walk is the same thing.
 *
 * Two activities are the same when their times overlap by at least half the shorter one: devices' clocks drift by minutes, not by half an
 * activity. For a file whose times have no zone ([ImportedActivity.localStart], a dive log's), one of the same length (within 10%) a whole
 * number of hours off is likely the same one read in another zone, so it matches too, flagged by [ActivityMatch.hoursApart]. A file in UTC
 * (FIT, TCX) hours off is another activity.
 */
object ActivityMatcher {
    /** For each of [activities] (placed in the zone the athlete picked), what it duplicates, or null. */
    fun match(activities: List<ImportedActivity>, existing: List<OwnActivity>): List<ActivityMatch?> =
        matchSpans(activities.map { it.matchSpan() }, existing)

    /**
     * For each of [imports], in batch order, what it duplicates, or null: of [existing] it's the same as, an exact copy, else the first (in
     * their order); else for a zoneless one the first a whole number of hours off; else of the activities before it in the batch it's the
     * same as, an exact copy, else the earliest. Only activities within [MAX_ZONE_HOURS] of each other are compared, so a batch of
     * thousands against a log of thousands stays quick.
     */
    fun matchSpans(imports: List<MatchSpan>, existing: List<OwnActivity>): List<ActivityMatch?> {
        val loggedKeys = existing.map { it.record.key() }
        val keys = imports.map { it.key() }
        val logged = Nearby(loggedKeys.map { it.span })
        val batch = Nearby(keys.map { it.span })
        return imports.indices.map { i ->
            val key = keys[i]
            val near = logged.near(key.span)
            fun of(of: DuplicateOf, other: Key, hoursApart: Int = 0) =
                ActivityMatch(of, hoursApart, hoursApart == 0 && key.copyOf(other), key.sameSport(other))
            near
                .filter { key.span.sameAs(logged.spans[it]) }
                .preferringCopies(key, loggedKeys)
                ?.let { of(DuplicateOf.Existing(existing[it]), loggedKeys[it]) }
                ?: near
                    .takeIf { imports[i].zoneless }
                    ?.firstNotNullOfOrNull { j ->
                        key.span.hoursFrom(logged.spans[j])?.let { of(DuplicateOf.Existing(existing[j]), loggedKeys[j], it) }
                    }
                ?: batch
                    .near(key.span)
                    .filter { j -> j < i && key.span.sameAs(batch.spans[j]) }
                    .preferringCopies(key, keys)
                    ?.let { of(DuplicateOf.InBatch(it), keys[it]) }
        }
    }

    /** The first of these indices into [keys] that [key] is an exact copy of, else the first. */
    private fun List<Int>.preferringCopies(key: Key, keys: List<Key>): Int? = firstOrNull { key.copyOf(keys[it]) } ?: firstOrNull()

    /** An activity as compared: when and how long, and what tells a copy of it apart. */
    private class Key(val span: Span, val sport: String?, val distanceMeters: Double?, val device: String?) {
        /** The same recording: starts and lengths within a second, distances within [COPY_DISTANCE_TOLERANCE] (or none), one device. */
        fun copyOf(other: Key): Boolean =
            (span.start - other.span.start).absoluteValue <= COPY_TIME_TOLERANCE &&
                (span.length - other.span.length).absoluteValue <= COPY_TIME_TOLERANCE &&
                sameDistance(other) &&
                (device == null || other.device == null || device == other.device)

        private fun sameDistance(other: Key): Boolean {
            val a = distanceMeters
            val b = other.distanceMeters
            return if (a == null || b == null) a == b else abs(a - b) <= maxOf(a, b) * COPY_DISTANCE_TOLERANCE
        }

        fun sameSport(other: Key): Boolean = sport == null || other.sport == null || sport == other.sport
    }

    private fun MatchSpan.key() = Key(Span(startedAt, elapsed), sport, distanceMeters, device)

    private fun ActivityRecord.key() = Key(span(), sport, distance?.toDouble(), device)

    /** [spans] indexed by start, so those near one are found without comparing every pair. */
    private class Nearby(val spans: List<Span>) {
        private val byStart = spans.indices.sortedBy { spans[it].start }
        private val longest = spans.maxOfOrNull { it.length }?.coerceAtLeast(Duration.ZERO) ?: Duration.ZERO

        /**
         * The indices, ascending, of every span that could overlap [span] or start within [MAX_ZONE_HOURS] (and drift) of it: those
         * starting from the longest span's length plus that margin before it to that margin after its end.
         */
        fun near(span: Span): List<Int> {
            val margin = MAX_ZONE_HOURS.hours + CLOCK_DRIFT
            val from = span.start - longest - margin
            val until = span.end + margin
            var low = 0
            var high = byStart.size
            while (low < high) {
                val mid = (low + high) ushr 1
                if (spans[byStart[mid]].start < from) low = mid + 1 else high = mid
            }
            val found = mutableListOf<Int>()
            var at = low
            while (at < byStart.size && spans[byStart[at]].start <= until) found += byStart[at++]
            return found.sorted()
        }
    }

    /**
     * How [a] and [b], two of the athlete's saved activities, likely belong together, or null: they're of the same sport
     * ([ActivityRecord.subSport] aside) and overlap as [match] would call them the same, or they're a [continuation]. Unlike [match], which
     * lets the athlete decide whether an imported run that overlaps a walk is the same thing, a suggestion asks unprompted, so an overlap
     * of two sports isn't one.
     */
    fun pairing(a: ActivityRecord, b: ActivityRecord): Pairing? =
        when {
            a.sport == b.sport && a.span().sameAs(b.span()) -> Pairing.Overlap
            continuation(a, b) -> Pairing.Continuation
            else -> null
        }

    /**
     * Whether one of [a] and [b] starts no more than [MAX_BREAK] after the other ends, in the same sport ([ActivityRecord.subSport] aside,
     * so a mountain ride continues a ride). Dives never do: repeat dives are separate dives.
     */
    fun continuation(a: ActivityRecord, b: ActivityRecord): Boolean {
        if (a.sport != b.sport || a.sport == Sport.DIVE) return false
        val (earlier, later) = if (a.startedAt <= b.startedAt) a.span() to b.span() else b.span() to a.span()
        val gap = later.start - earlier.end
        return gap >= -CLOCK_DRIFT && gap <= MAX_BREAK
    }

    private fun ActivityRecord.span() = Span(startedAt, elapsedTime.seconds)

    private class Span(val start: Instant, val length: Duration) {
        val end: Instant = start + length

        /** The two overlap by half the shorter; a zero-length one needs starts within a few minutes. */
        fun sameAs(other: Span): Boolean {
            val shorter = minOf(length, other.length)
            val overlap = minOf(end, other.end) - maxOf(start, other.start)
            return if (shorter <= Duration.ZERO) (start - other.start).absoluteValue <= CLOCK_DRIFT else overlap >= shorter / 2
        }

        /** The whole hours (signed, never 0) [other] is off by, when it's the same length; null otherwise. */
        fun hoursFrom(other: Span): Int? {
            val longer = maxOf(length, other.length)
            if (longer <= Duration.ZERO || (length - other.length).absoluteValue > longer * LENGTH_TOLERANCE) return null
            val offset = start - other.start
            val hours = (offset.inWholeMinutes.toDouble() / MINUTES_PER_HOUR).roundToInt()
            val off = (offset - hours.hours).absoluteValue
            return hours.takeIf { it != 0 && abs(it) <= MAX_ZONE_HOURS && off <= CLOCK_DRIFT }
        }
    }

    private val CLOCK_DRIFT = 3.minutes
    /** Two exports of one upload agree to the second; a second recording of the activity doesn't. */
    private val COPY_TIME_TOLERANCE = 1.seconds
    /** A distance stored in whole metres against the file's, and rounding between exports. */
    private const val COPY_DISTANCE_TOLERANCE = 0.005
    /** A ski lunch, a shuttle or a device swap, but not a morning and evening double. */
    val MAX_BREAK = 2.hours
    private const val LENGTH_TOLERANCE = 0.1
    private const val MINUTES_PER_HOUR = 60
    /** UTC-12 to UTC+14 apart, rounded: the most two zones differ by in practice. */
    private const val MAX_ZONE_HOURS = 14
}
