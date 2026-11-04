package io.github.solcott.activitytracker.app.detail

import androidx.compose.runtime.Immutable
import io.github.solcott.activitytracker.lexicon.AtUri
import io.github.solcott.activitytracker.lexicon.app.EffortView
import io.github.solcott.activitytracker.lexicon.app.SegmentKind
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList

/**
 * One ski lift an activity rode (segments/segment-lifts): its segment, its current name, how many times the activity rode it, and the total
 * time on it in seconds.
 */
@Immutable data class LiftRides(val segment: AtUri, val name: String, val rides: Int, val elapsedSeconds: Long)

/** Whether [effort] is a ride on a lift rather than an effort on a run. */
fun isLiftRide(effort: EffortView): Boolean = SegmentKind.of(effort.segment.kind) == SegmentKind.LIFT

/** [efforts]' lift rides, one entry per lift in the order each was first ridden (by start offset), with its count and total time. */
fun liftRidesOf(efforts: List<EffortView>): ImmutableList<LiftRides> =
    efforts
        .filter(::isLiftRide)
        .sortedBy { it.startOffset }
        .groupBy { it.segment.uri }
        .map { (uri, rides) -> LiftRides(uri, rides.first().segment.name, rides.size, rides.sumOf { it.elapsed }) }
        .toImmutableList()

/**
 * [laps] with each lift-ride lap (a rest lap, as a ski day's FIT splits record lift rides) named after the matched ride in [efforts] it
 * overlaps most in time, so the laps table can say which lift it was; a lap no ride overlaps keeps no name.
 */
fun namedLiftLaps(laps: ImmutableList<LapRow>, efforts: List<EffortView>): ImmutableList<LapRow> {
    val rides = efforts.filter(::isLiftRide)
    if (rides.isEmpty() || laps.none { it.isRest }) return laps
    return laps
        .map { lap ->
            if (!lap.isRest) return@map lap
            val lapEnd = lap.startSeconds.toLong() + lap.elapsedSeconds
            val ride =
                rides
                    .map { it to minOf(lapEnd, it.startOffset + it.elapsed) - maxOf(lap.startSeconds.toLong(), it.startOffset) }
                    .filter { (_, overlap) -> overlap > 0 }
                    .maxByOrNull { (_, overlap) -> overlap }
                    ?.first
            lap.copy(liftName = ride?.segment?.name)
        }
        .toImmutableList()
}
