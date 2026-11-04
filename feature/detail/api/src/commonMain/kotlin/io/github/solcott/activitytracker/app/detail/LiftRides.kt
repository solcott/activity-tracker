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
