package io.github.solcott.activitytracker.data.activity

import io.github.solcott.activitytracker.data.activity.ActivityCombiner.SAMPLE_TOLERANCE_SECONDS
import io.github.solcott.activitytracker.data.activity.ActivityCombiner.nearest
import io.github.solcott.activitytracker.data.activity.ActivityCombiner.offsetOf
import io.github.solcott.activitytracker.data.activity.ActivityCombiner.usualInterval
import io.github.solcott.activitytracker.lexicon.AtUri
import io.github.solcott.activitytracker.lexicon.app.ActivityStreamData
import io.github.solcott.activitytracker.lexicon.app.CombinedChannel
import io.github.solcott.activitytracker.lexicon.app.StreamChannel
import io.github.solcott.activitytracker.lexicon.app.TankPressure
import kotlin.time.Instant

/** The combined activity's streams ([ActivityCombiner]): one timeline, each sample from the highest-ranked member with one then. */
internal object CombinedStreams {
    /** A member's streams moved onto the combined start, the [pieces] it really recorded, and how far apart it usually samples. */
    class Placed(val member: CombineMember, val data: ActivityStreamData, val pieces: List<Piece>, val tolerance: Int) {
        /** Index of [data]'s sample at [t], if it has one near enough and [t] is within a piece it recorded. */
        fun indexAt(t: Int): Int? = if (pieces.any { t in it }) nearest(data.time, t, tolerance) else null
    }

    fun build(
        members: List<CombineMember>,
        groups: List<Group>,
        rankings: Map<String, List<AtUri>>,
        start: Instant,
    ): ActivityStreamData? {
        val pieces = groups.flatMap { it.pieces }.groupBy { it.member.uri }
        val placed =
            members
                .mapNotNull { member ->
                    val data = member.streams ?: return@mapNotNull null
                    val moved = ActivityMerge.shifted(data, offsetOf(member, start))
                    Placed(member, moved, pieces[member.uri].orEmpty(), maxOf(SAMPLE_TOLERANCE_SECONDS, usualInterval(moved.time)))
                }
                .associateBy { it.member.uri }
        if (placed.isEmpty()) return null
        // Only the times a member really recorded: not a dead device's leftovers.
        val time = placed.values.flatMap { p -> p.data.time.filter { t -> p.pieces.any { t in it } } }.distinct().sorted()
        fun ranked(channel: String): List<Placed> = rankings[channel].orEmpty().mapNotNull { placed[it] }

        val sampled =
            ActivityStreamData.INT_CHANNELS.fold(ActivityStreamData(time = time)) { data, channel ->
                val sources = ranked(channel.token).filter { channel.get(it.data) != null }
                if (sources.isEmpty()) return@fold data
                val values =
                    if (channel.token == StreamChannel.DISTANCE) carriedOn(time, sources) { channel.get(it.data) }
                    else time.map { t -> pick(sources, t) { channel.get(it.data) }?.second }
                channel.with(data, values.takeIf { v -> v.any { it != null } })
            }
        // Each group's laps, sets and the like from its best member that has them, only those within the group.
        fun <T> perGroup(channel: String, items: (ActivityStreamData) -> List<T>?, at: (T) -> Int): List<T> = groups.flatMap { group ->
            val within = { item: T -> at(item) in group.from..group.to }
            ranked(channel)
                .filter { p -> group.members.any { it.uri == p.member.uri } }
                .firstNotNullOfOrNull { p -> items(p.data)?.filter(within)?.ifEmpty { null } }
                .orEmpty()
        }

        val legs = CombinedLegs.of(groups, rankings, placed, sampled)
        val tanks = tankPressures(time, ranked(CombinedChannel.DIVE).firstOrNull { !it.data.tankPressures.isNullOrEmpty() })
        return sampled.copy(
            laps = perGroup(CombinedChannel.LAPS, { it.laps }) { it.start }.ifEmpty { null },
            lengths = perGroup(CombinedChannel.LENGTHS, { it.lengths }) { it.start }.ifEmpty { null },
            sets = perGroup(CombinedChannel.SETS, { it.sets }) { it.start }.ifEmpty { null },
            gears = perGroup(StreamChannel.GEARS, { it.gears }) { it.time }.ifEmpty { null },
            gasSwitches = perGroup(CombinedChannel.DIVE, { it.gasSwitches }) { it.time }.ifEmpty { null },
            diveAlerts = perGroup(CombinedChannel.DIVE, { it.diveAlerts }) { it.time }.ifEmpty { null },
            tankPressures = tanks,
            legs = legs,
        )
    }

    /** The first of [sources] (best first) with a sample at [t] in [channel], and that sample. */
    private fun pick(sources: List<Placed>, t: Int, channel: (Placed) -> List<Int?>?): Pair<Placed, Int>? =
        sources.firstNotNullOfOrNull { source ->
            source.indexAt(t)?.let { channel(source)?.getOrNull(it) }?.let { source to it }
        }

    /**
     * Distance, which each device counts from its own start: whenever the member supplying it changes, the new one's values are moved to
     * carry on from the combined distance so far, so the curve never jumps back.
     */
    private fun carriedOn(time: List<Int>, sources: List<Placed>, channel: (Placed) -> List<Int?>?): List<Int?> {
        var last: Int? = null
        var current: Placed? = null
        var adjust = 0
        return time.map { t ->
            val (source, raw) = pick(sources, t, channel) ?: return@map null
            if (source !== current) {
                adjust = last?.let { it - raw } ?: 0
                current = source
            }
            val value = maxOf(raw + adjust, last ?: Int.MIN_VALUE)
            last = value
            value
        }
    }

    /** [source]'s tank pressures, sampled onto [time]; only one member's, since they line up with its own tanks. */
    private fun tankPressures(time: List<Int>, source: Placed?): List<TankPressure>? =
        source?.data?.tankPressures?.map { series ->
            series.copy(pressure = time.map { t -> source.indexAt(t)?.let { series.pressure.getOrNull(it) } })
        }
}
