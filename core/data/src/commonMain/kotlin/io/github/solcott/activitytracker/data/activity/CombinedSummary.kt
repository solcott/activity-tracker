package io.github.solcott.activitytracker.data.activity

import io.github.solcott.activitytracker.activityfile.PerSecond
import io.github.solcott.activitytracker.data.streams.StreamAnalytics
import io.github.solcott.activitytracker.lexicon.AtUri
import io.github.solcott.activitytracker.lexicon.app.ActivityRecord
import io.github.solcott.activitytracker.lexicon.app.ActivitySensor
import io.github.solcott.activitytracker.lexicon.app.ActivityStreamData
import io.github.solcott.activitytracker.lexicon.app.CombinedChannel
import io.github.solcott.activitytracker.lexicon.app.Leg
import io.github.solcott.activitytracker.lexicon.app.StreamChannel
import kotlin.math.roundToInt
import kotlin.math.roundToLong
import kotlin.time.Instant

/**
 * A combined activity's summary figures ([ActivityCombiner]). Elapsed time runs from when the first member started recording to when the
 * last stopped, gaps included. Totals add up over the groups of pieces recorded at the same time ([Group]): each group's from its member
 * ranked first for the matching channel, in proportion to how much of that member's recording falls in the group, and only for the part of
 * the group no earlier group covers, so an overlap isn't counted twice. Averages and maxima come from the combined channels where there are
 * any.
 */
internal object CombinedSummary {
    @Suppress("LongParameterList", "LongMethod", "CyclomaticComplexMethod") // Every summary field, each from its own channel.
    fun record(
        base: CombineMember,
        members: List<CombineMember>,
        groups: List<Group>,
        rankings: Map<String, List<AtUri>>,
        streams: ActivityStreamData?,
        start: Instant,
        now: Instant,
    ): ActivityRecord {
        val shares = shares(groups)
        val recorded = recordedSeconds(groups)
        fun totalBy(ranking: List<AtUri>?, field: (ActivityRecord) -> Long?): Long? =
            groups
                .zip(shares)
                .mapNotNull { (group, share) ->
                    pick(group, ranking, field)?.let { (member, value) -> value * share * fraction(group, member, recorded) }
                }
                .takeIf { it.isNotEmpty() }
                ?.sum()
                ?.roundToLong()
        fun total(channel: String, field: (ActivityRecord) -> Long?): Long? = totalBy(rankings[channel], field)
        fun firstBy(ranking: List<AtUri>?, field: (ActivityRecord) -> Int?): Int? = groups.firstNotNullOfOrNull { group ->
            pick(group, ranking, field)?.second
        }
        fun first(channel: String, field: (ActivityRecord) -> Int?): Int? = firstBy(rankings[channel], field)
        // Averages and maxima from the combined channel; without one, the groups' own, by time.
        fun mean(token: String): Double? = streams?.let { channel(it, token) }?.takeIf { it.isNotEmpty() }?.average()
        fun peak(token: String): Int? = streams?.let { channel(it, token) }?.maxOrNull()
        fun weighted(channel: String, field: (ActivityRecord) -> Int?): Int? {
            val values = groups.mapNotNull { group -> pick(group, rankings[channel], field)?.let { it.second to (group.to - group.from) } }
            val weight = values.sumOf { it.second }
            return if (values.isEmpty()) null
            else if (weight <= 0) values.first().first else (values.sumOf { it.first.toDouble() * it.second } / weight).roundToInt()
        }
        fun maxOf(channel: String, field: (ActivityRecord) -> Int?): Int? =
            groups.mapNotNull { pick(it, rankings[channel], field)?.second }.maxOrNull()

        val elapsed = groups.maxOf { it.to }.toLong()
        // Without distance and speed (left out), nothing tells moving from stopped.
        val movingTime =
            total(StreamChannel.DISTANCE) { it.movingTime ?: it.elapsedTime }
                ?: elapsed.takeIf { excluded(rankings, StreamChannel.DISTANCE) }
        val distance =
            streams?.distance?.lastOrNull { it != null }?.let { it / CM_PER_M.toLong() } ?: total(StreamChannel.DISTANCE) { it.distance }
        val power = streams?.power?.let { StreamAnalytics.perSecond(streams.time, it) }
        val record = base.record
        return ActivityRecord(
            sport = record.sport,
            startedAt = start,
            elapsedTime = elapsed,
            createdAt = now,
            title = record.title,
            description = record.description,
            movingTime = movingTime,
            distance = distance,
            elevationGain = total(StreamChannel.ALTITUDE) { it.elevationGain },
            elevationLoss = total(StreamChannel.ALTITUDE) { it.elevationLoss },
            avgSpeed =
                if (distance != null && movingTime != null && movingTime > 0) distance * MM_PER_M / movingTime
                else weighted(StreamChannel.SPEED) { it.avgSpeed?.toInt() }?.toLong(),
            maxSpeed = (peak(StreamChannel.SPEED) ?: maxOf(StreamChannel.SPEED) { it.maxSpeed?.toInt() })?.toLong(),
            avgHeartRate = mean(StreamChannel.HEART_RATE)?.roundToInt() ?: weighted(StreamChannel.HEART_RATE) { it.avgHeartRate },
            maxHeartRate = peak(StreamChannel.HEART_RATE) ?: maxOf(StreamChannel.HEART_RATE) { it.maxHeartRate },
            avgTemperature =
                (mean(StreamChannel.TEMPERATURE)?.roundToInt() ?: weighted(StreamChannel.TEMPERATURE) { it.avgTemperature?.toInt() })
                    ?.toLong(),
            device =
                ordered(members, kept(rankings, CombinedChannel.POSITION) ?: kept(rankings, StreamChannel.DISTANCE)).firstNotNullOfOrNull {
                    it.record.device
                },
            subSport = record.subSport,
            sensors = members.flatMap { it.record.sensors.orEmpty() }.distinct().take(ActivitySensor.MAX).ifEmpty { null },
            dive = ordered(members, rankings[CombinedChannel.DIVE]).firstNotNullOfOrNull { it.record.dive },
            media = record.media,
            avgCadence = cadence(streams) ?: weighted(StreamChannel.CADENCE) { it.avgCadence },
            maxCadence = peak(StreamChannel.CADENCE) ?: maxOf(StreamChannel.CADENCE) { it.maxCadence },
            avgPower = power?.takeIf { it.isNotEmpty() }?.average()?.roundToInt() ?: weighted(StreamChannel.POWER) { it.avgPower },
            maxPower = peak(StreamChannel.POWER) ?: maxOf(StreamChannel.POWER) { it.maxPower },
            normalizedPower =
                power?.let(PerSecond::normalizedPower)
                    ?: groups.singleOrNull()?.let { pick(it, rankings[StreamChannel.POWER]) { r -> r.normalizedPower }?.second },
            work = total(StreamChannel.POWER) { it.work },
            // The devices' own estimates, kept when heart rate is left out.
            calories = totalBy(kept(rankings, StreamChannel.HEART_RATE)) { it.calories?.toLong() }?.toInt(),
            frontShifts = total(StreamChannel.GEARS) { it.frontShifts?.toLong() }?.toInt(),
            rearShifts = total(StreamChannel.GEARS) { it.rearShifts?.toLong() }?.toInt(),
            jumps = total(CombinedChannel.POSITION) { it.jumps?.toLong() }?.toInt(),
            grit = first(CombinedChannel.POSITION) { it.grit },
            flow = first(CombinedChannel.POSITION) { it.flow },
            poolLength = firstBy(kept(rankings, CombinedChannel.LENGTHS)) { it.poolLength },
            cycles = total(StreamChannel.CADENCE) { it.cycles?.toLong() }?.toInt(),
            avgStrokeDistance = firstBy(kept(rankings, CombinedChannel.LENGTHS)) { it.avgStrokeDistance },
            utcOffset = record.utcOffset ?: members.firstNotNullOfOrNull { it.record.utcOffset },
        )
    }

    /** [group]'s member ranked first by [ranking] (then the rest by start) with a value for [field], and that value. */
    fun <T : Any> pick(group: Group, ranking: List<AtUri>?, field: (ActivityRecord) -> T?): Pair<CombineMember, T>? =
        ordered(group.members, ranking).firstNotNullOfOrNull { member -> field(member.record)?.let { member to it } }

    /** [members] best first by [ranking], then the rest in order of start; none when [ranking] is empty, a channel left out. */
    fun ordered(members: List<CombineMember>, ranking: List<AtUri>?): List<CombineMember> {
        if (ranking != null && ranking.isEmpty()) return emptyList()
        val byUri = members.associateBy { it.uri }
        val ranked = ranking.orEmpty().mapNotNull { byUri[it] }
        return ranked + (members - ranked.toSet())
    }

    /** [channel] was left out of the combined activity: it's ranked, by no member. */
    fun excluded(rankings: Map<String, List<AtUri>>, channel: String): Boolean = rankings[channel]?.isEmpty() == true

    /** [channel]'s ranking, or null (every member by start) when it was left out: for figures that aren't the channel's own data. */
    fun kept(rankings: Map<String, List<AtUri>>, channel: String): List<AtUri>? = rankings[channel]?.ifEmpty { null }

    /** How long each member really recorded over all [groups], in seconds. */
    fun recordedSeconds(groups: List<Group>): Map<AtUri, Int> =
        groups.flatMap { it.pieces }.groupBy { it.member.uri }.mapValues { (_, pieces) -> pieces.sumOf { it.length } }

    /** The share of [member]'s own totals that falls in [group]: all of them when it recorded nowhere else. */
    fun fraction(group: Group, member: CombineMember, recorded: Map<AtUri, Int>): Double {
        val all = recorded[member.uri] ?: 0
        return if (all <= 0) 1.0 else group.secondsOf(member).toDouble() / all
    }

    /**
     * For each group, the share of it no earlier group covers, from 0 to 1: a group recorded while the one before it was still going counts
     * only for the rest of its time.
     */
    fun shares(groups: List<Group>): List<Double> {
        var coveredUntil: Int? = null
        return groups.map { group ->
            val from = coveredUntil?.let { maxOf(it, group.from) } ?: group.from
            coveredUntil = coveredUntil?.let { maxOf(it, group.to) } ?: group.to
            val length = group.to - group.from
            if (length <= 0) 1.0 else ((group.to - from).coerceAtLeast(0).toDouble() / length)
        }
    }

    /** [token]'s samples as one value per second recorded, so irregular sampling doesn't skew an average. */
    private fun channel(data: ActivityStreamData, token: String): List<Int>? =
        ActivityStreamData.INT_CHANNELS.first { it.token == token }.get(data)?.let { StreamAnalytics.perSecond(data.time, it) }

    /** Average cadence while pedalling or stepping: devices leave out the zeros of coasting and standing. */
    private fun cadence(streams: ActivityStreamData?): Int? =
        streams?.let { channel(it, StreamChannel.CADENCE) }?.filter { it > 0 }?.takeIf { it.isNotEmpty() }?.average()?.roundToInt()

    private const val CM_PER_M = 100
    private const val MM_PER_M = 1000L
}

/** A combined activity's legs: none when its members were all recorded at the same time, one per group of them otherwise. */
internal object CombinedLegs {
    fun of(
        groups: List<Group>,
        rankings: Map<String, List<AtUri>>,
        placed: Map<AtUri, CombinedStreams.Placed>,
        combined: ActivityStreamData,
    ): List<Leg>? {
        // A multisport member's own legs, those within the group.
        fun legsOf(group: Group): List<Leg>? =
            CombinedSummary.ordered(group.members, rankings[CombinedChannel.LEGS]).firstNotNullOfOrNull { member ->
                placed[member.uri]?.data?.legs?.filter { it.start in group.from..group.to }?.ifEmpty { null }
            }
        if (groups.size == 1) return legsOf(groups.single())
        val recorded = CombinedSummary.recordedSeconds(groups)
        return groups.flatMap { group -> legsOf(group) ?: listOf(legOf(group, rankings, recorded, combined)) }
    }

    /** A group recorded at the same time, as one leg: its distance from the combined channel, else its figures from its members. */
    private fun legOf(group: Group, rankings: Map<String, List<AtUri>>, recorded: Map<AtUri, Int>, combined: ActivityStreamData): Leg {
        fun <T : Any> pick(channel: String, field: (ActivityRecord) -> T?): T? =
            CombinedSummary.pick(group, rankings[channel], field)?.second
        fun scaled(channel: String, field: (ActivityRecord) -> Number?): Int? =
            CombinedSummary.pick(group, rankings[channel], field)?.let { (member, value) ->
                (value.toDouble() * CombinedSummary.fraction(group, member, recorded)).roundToInt()
            }
        val first = group.pieces.minBy { it.from }.member
        val distance = combined.distance?.let { distanceWithin(combined.time, it, group) }
        return Leg(
            start = group.from,
            elapsed = group.to - group.from,
            sport = first.record.sport,
            subSport = first.record.subSport,
            moving = scaled(StreamChannel.DISTANCE) { it.movingTime },
            distance = distance ?: scaled(StreamChannel.DISTANCE) { it.distance },
            avgSpeed = pick(StreamChannel.SPEED) { it.avgSpeed }?.toInt(),
            maxSpeed = pick(StreamChannel.SPEED) { it.maxSpeed }?.toInt(),
            avgHeartRate = pick(StreamChannel.HEART_RATE) { it.avgHeartRate },
            maxHeartRate = pick(StreamChannel.HEART_RATE) { it.maxHeartRate },
            avgPower = pick(StreamChannel.POWER) { it.avgPower },
            avgCadence = pick(StreamChannel.CADENCE) { it.avgCadence },
            ascent = scaled(StreamChannel.ALTITUDE) { it.elevationGain },
            descent = scaled(StreamChannel.ALTITUDE) { it.elevationLoss },
            calories =
                CombinedSummary.pick(group, CombinedSummary.kept(rankings, StreamChannel.HEART_RATE)) { it.calories }
                    ?.let { (member, value) -> (value * CombinedSummary.fraction(group, member, recorded)).roundToInt() },
        )
    }

    /** Meters covered between [group]'s first and last combined distance samples. */
    private fun distanceWithin(time: List<Int>, distance: List<Int?>, group: Group): Int? {
        val within = time.indices.filter { time[it] in group.from..group.to }.mapNotNull { distance[it] }
        return if (within.size < 2) null else (within.last() - within.first()) / CM_PER_M
    }

    private const val CM_PER_M = 100
}
