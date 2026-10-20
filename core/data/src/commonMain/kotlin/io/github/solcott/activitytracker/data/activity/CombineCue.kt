package io.github.solcott.activitytracker.data.activity

import io.github.solcott.activitytracker.lexicon.app.ActivityRecord
import io.github.solcott.activitytracker.location.LocationSample
import io.github.solcott.activitytracker.location.TrackMath
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/** What a combine suggestion looks like, from where and when the athlete's own data shows its activities ended and started. */
enum class CombineCue {
    /** The two overlap: one activity recorded on two devices. */
    SameActivity,

    /** The later one starts a few minutes after, and close to where, the earlier one ended. */
    SwitchedDevices,

    /** The later one starts well above where the earlier one ended. */
    ShuttleOrLift,

    /** Anything else, including activities whose ends aren't known (a polyline trimmed by a privacy zone, say). */
    Break,
}

/** A point an activity started or ended at. */
data class EndPoint(val latitude: Double, val longitude: Double, val altitudeMeters: Double? = null)

/** Where an activity started and ended, each null when unknown. */
data class ActivityEnds(val start: EndPoint? = null, val end: EndPoint? = null)

object CombineCues {
    private val SWITCH_TIME = 5.minutes
    private const val SWITCH_DISTANCE_METERS = 300.0
    private const val CLIMB_METERS = 100.0

    /** The cue for combining [a] and [b], which [ActivityMatcher.pairing] paired as [pairing], given where each started and ended. */
    fun of(pairing: Pairing, a: ActivityRecord, aEnds: ActivityEnds, b: ActivityRecord, bEnds: ActivityEnds): CombineCue {
        if (pairing == Pairing.Overlap) return CombineCue.SameActivity
        val (earlier, later) = if (a.startedAt <= b.startedAt) (a to aEnds) to (b to bEnds) else (b to bEnds) to (a to aEnds)
        val gap = later.first.startedAt - (earlier.first.startedAt + earlier.first.elapsedTime.seconds)
        val from = earlier.second.end
        val to = later.second.start
        return when {
            from == null || to == null -> CombineCue.Break
            gap <= SWITCH_TIME &&
                TrackMath.distanceMeters(from.latitude, from.longitude, to.latitude, to.longitude) <= SWITCH_DISTANCE_METERS ->
                CombineCue.SwitchedDevices
            from.altitudeMeters != null && to.altitudeMeters != null && to.altitudeMeters - from.altitudeMeters >= CLIMB_METERS ->
                CombineCue.ShuttleOrLift
            else -> CombineCue.Break
        }
    }
}

/** Where a track started and ended. */
internal fun trackEnds(track: List<LocationSample>): ActivityEnds {
    val first = track.first()
    val last = track.last()
    return ActivityEnds(EndPoint(first.latitude, first.longitude, first.altitude), EndPoint(last.latitude, last.longitude, last.altitude))
}

/** Where a decoded polyline [line] started and ended, at the first and last of [altitude] (centimeters) known. */
internal fun polylineEnds(line: List<Pair<Double, Double>>, altitude: List<Int?>): ActivityEnds {
    val (startLat, startLon) = line.first()
    val (endLat, endLon) = line.last()
    return ActivityEnds(
        EndPoint(startLat, startLon, altitude.firstOrNull { it != null }?.let { it / CM_PER_M }),
        EndPoint(endLat, endLon, altitude.lastOrNull { it != null }?.let { it / CM_PER_M }),
    )
}

private const val CM_PER_M = 100.0
