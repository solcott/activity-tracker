package io.github.solcott.activitytracker.data.activity

import io.github.solcott.activitytracker.lexicon.AtUri
import io.github.solcott.activitytracker.lexicon.app.ActivityRecord
import io.github.solcott.activitytracker.lexicon.app.ActivityStreamData
import io.github.solcott.activitytracker.lexicon.app.ChannelSource
import io.github.solcott.activitytracker.lexicon.app.CombinedActivity
import io.github.solcott.activitytracker.lexicon.app.CombinedChannel
import io.github.solcott.activitytracker.lexicon.app.DetailCategory
import io.github.solcott.activitytracker.lexicon.app.StreamChannel
import io.github.solcott.activitytracker.lexicon.app.Visibility
import io.github.solcott.activitytracker.location.LocationSample
import kotlin.math.abs
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/** One recording a combined activity is built from: its whole record (every place's details laid over), its streams and its track. */
data class CombineMember(
    val uri: AtUri,
    val record: ActivityRecord,
    val streams: ActivityStreamData? = null,
    val track: List<LocationSample>? = null,
) {
    val start: Instant
        get() = record.startedAt

    val length: Duration
        get() = record.elapsedTime.coerceAtLeast(0).seconds

    val end: Instant
        get() = start + length
}

/** A stretch of time, from [start] to [end]. */
data class Span(val start: Instant, val end: Instant) {
    val length: Duration
        get() = end - start
}

/** A stretch [member] really recorded, in seconds since the combined activity's start. */
class Piece(val member: CombineMember, val from: Int, val to: Int) {
    constructor(
        member: CombineMember,
        span: Span,
        start: Instant,
    ) : this(member, (span.start - start).inWholeSeconds.toInt(), (span.end - start).inWholeSeconds.toInt())

    val length: Int
        get() = to - from

    operator fun contains(t: Int): Boolean = t in from..to
}

/** Pieces recorded at the same time: one leg of a combined activity, or all of it. */
class Group(val pieces: List<Piece>) {
    val from: Int = pieces.minOf { it.from }
    val to: Int = pieces.maxOf { it.to }
    val members: List<CombineMember> = pieces.map { it.member }.distinctBy { it.uri }

    /** How long [member]'s pieces in this group last, in seconds. */
    fun secondsOf(member: CombineMember): Int = pieces.filter { it.member.uri == member.uri }.sumOf { it.length }
}

/**
 * What [ActivityCombiner.combine] builds: the combined [record], without a polyline (the caller trims [route] by the athlete's privacy
 * zones, [RouteTrim.segmentedPolylineOf]), its [streams], and its full-resolution [route] in time order.
 */
class CombinedBuild(val record: ActivityRecord, val streams: ActivityStreamData?, val route: List<LocationSample>)

/**
 * Builds a combined activity from its members (`feed/combined-activities`). For each channel, at each moment, the combined activity takes
 * the sample of the highest-ranked member that has one then; members that follow one another join end to end, each a leg.
 *
 * Pure: the caller reads the members and writes what this returns.
 */
object ActivityCombiner {
    /**
     * The combined activity of [members] (at least two), starting from [base]'s title, description, photos, sport and visibility-defining
     * fields. [sources] is the ranking stored so far, if any: each channel keeps its order there, and members it doesn't name follow by the
     * default ranking ([rankings]).
     */
    fun combine(base: AtUri, members: List<CombineMember>, now: Instant, sources: List<ChannelSource> = emptyList()): CombinedBuild {
        require(members.size >= CombinedActivity.MIN_MEMBERS) { "A combined activity has at least ${CombinedActivity.MIN_MEMBERS} members" }
        require(members.size <= CombinedActivity.MAX_MEMBERS) { "A combined activity has at most ${CombinedActivity.MAX_MEMBERS} members" }
        val sorted = members.sortedWith(compareBy<CombineMember> { it.start }.thenBy { it.uri.value })
        val baseMember = sorted.firstOrNull { it.uri == base } ?: sorted.first()
        val rankings = rankings(sorted, sources)
        val covered = sorted.associate { it.uri to CombinedCoverage.of(it) }
        val start = covered.values.flatten().minOf { it.start }
        val groups = CombinedCoverage.groups(sorted.flatMap { member -> covered.getValue(member.uri).map { Piece(member, it, start) } })
        val streams = CombinedStreams.build(sorted, groups, rankings, start)
        val record =
            CombinedSummary.record(baseMember, sorted, groups, rankings, streams, start, now)
                .copy(
                    combined =
                        CombinedActivity(
                            members = sorted.map { it.uri },
                            sources = rankings.map { (channel, ranked) -> ChannelSource(channel, ranked) },
                        )
                )
        val route = CombinedRoute.build(sorted, rankings[CombinedChannel.POSITION].orEmpty(), covered)
        return CombinedBuild(record, streams, route)
    }

    /**
     * [members] in groups recorded at the same time, by when each really recorded ([CombinedCoverage.of]): the members of each group, in
     * order. A convenience over [CombinedCoverage.groups].
     */
    fun groups(members: List<CombineMember>): List<List<CombineMember>> {
        val start = members.flatMap { CombinedCoverage.of(it) }.minOf { it.start }
        return CombinedCoverage.groups(members.flatMap { member -> CombinedCoverage.of(member).map { Piece(member, it, start) } }).map {
            it.members
        }
    }

    /**
     * For each channel some member has, the members that have it, best first: [stored]'s order for the channel, then the default — the
     * member with more of the channel first (samples, track points, or simply having laps, legs, sets, lengths or dive data), an earlier
     * start breaking a tie.
     */
    fun rankings(members: List<CombineMember>, stored: List<ChannelSource> = emptyList()): Map<String, List<AtUri>> {
        val byStart = members.sortedWith(compareBy<CombineMember> { it.start }.thenBy { it.uri.value })
        return CHANNELS.mapNotNull { channel ->
                val amounts = byStart.associate { it.uri to amountOf(it, channel) }
                val having = byStart.filter { amounts.getValue(it.uri) > 0 }.sortedByDescending { amounts.getValue(it.uri) }.map { it.uri }
                if (having.isEmpty()) return@mapNotNull null
                val chosen = stored.firstOrNull { it.channel == channel }?.members.orEmpty().filter { it in having }.distinct()
                channel to (chosen + (having - chosen.toSet()))
            }
            .toMap()
    }

    /** Each detail category's narrowest visibility among [members]' (each a category → tier map), for a new combined activity. */
    fun narrowestTiers(activity: String, members: List<Map<String, String>>): Map<String, String> =
        DetailCategory.all.associateWith { category ->
            members.map { it[category] ?: activity }.fold(activity) { narrowest, tier -> Visibility.narrower(narrowest, tier) }
        }

    /** How much of [channel] [member] has: samples, track points, or 1 for having laps and the like at all. */
    private fun amountOf(member: CombineMember, channel: String): Int {
        val streams = member.streams
        val samples = ActivityStreamData.INT_CHANNELS.firstOrNull { it.token == channel }
        return when (channel) {
            samples?.token -> streams?.let { samples.get(it) }?.count { it != null } ?: 0
            CombinedChannel.POSITION -> member.track?.size ?: 0
            StreamChannel.GEARS -> if (streams?.gears.isNullOrEmpty()) 0 else 1
            CombinedChannel.LAPS -> if (streams?.laps.isNullOrEmpty()) 0 else 1
            CombinedChannel.LEGS -> if (streams?.legs.isNullOrEmpty()) 0 else 1
            CombinedChannel.SETS -> if (streams?.sets.isNullOrEmpty()) 0 else 1
            CombinedChannel.LENGTHS -> if (streams?.lengths.isNullOrEmpty()) 0 else 1
            CombinedChannel.DIVE -> if (hasDiveData(member)) 1 else 0
            else -> 0
        }
    }

    private fun hasDiveData(member: CombineMember): Boolean {
        val streams = member.streams
        return member.record.dive != null ||
            !streams?.gasSwitches.isNullOrEmpty() ||
            !streams?.diveAlerts.isNullOrEmpty() ||
            !streams?.tankPressures.isNullOrEmpty()
    }

    /** Seconds from [start] to [member]'s start. */
    internal fun offsetOf(member: CombineMember, start: Instant): Int = (member.start - start).inWholeSeconds.toInt()

    /** Every channel a combined activity ranks, in a fixed order: the stream channels, then the rest. */
    val CHANNELS: List<String> =
        ActivityStreamData.INT_CHANNELS.map { it.token } +
            listOf(
                StreamChannel.GEARS,
                CombinedChannel.POSITION,
                CombinedChannel.LAPS,
                CombinedChannel.LEGS,
                CombinedChannel.SETS,
                CombinedChannel.LENGTHS,
                CombinedChannel.DIVE,
            )

    /**
     * A member supplies a sample at a moment when it has one this many seconds away at most, or within its own usual recording interval if
     * that's longer (dive computers log every 10 s or so).
     */
    const val SAMPLE_TOLERANCE_SECONDS = 5

    /** The usual interval between [times] (sorted seconds): their median gap. */
    internal fun usualInterval(times: List<Int>): Int {
        if (times.size < 2) return 0
        val gaps = times.zipWithNext { a, b -> b - a }.filter { it > 0 }.sorted()
        return gaps.getOrNull(gaps.size / 2) ?: 0
    }

    /** The index in sorted [times] closest to [t], if within [tolerance] seconds. */
    internal fun nearest(times: List<Int>, t: Int, tolerance: Int): Int? {
        if (times.isEmpty()) return null
        val found = times.binarySearch(t)
        val index =
            if (found >= 0) {
                found
            } else {
                val after = -found - 1
                listOf(after - 1, after).filter { it in times.indices }.minBy { abs(times[it] - t) }
            }
        return index.takeIf { abs(times[it] - t) <= tolerance }
    }
}
