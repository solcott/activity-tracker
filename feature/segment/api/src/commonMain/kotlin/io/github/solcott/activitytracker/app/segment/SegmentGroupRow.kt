package io.github.solcott.activitytracker.app.segment

import androidx.compose.runtime.Immutable
import io.github.solcott.activitytracker.lexicon.AtUri
import io.github.solcott.activitytracker.lexicon.app.SegmentSportGroup
import io.github.solcott.activitytracker.lexicon.app.SegmentView
import io.github.solcott.activitytracker.location.GeoPoint
import io.github.solcott.activitytracker.location.SegmentClimb
import io.github.solcott.activitytracker.location.SegmentDirection
import io.github.solcott.activitytracker.location.SegmentGeometry
import io.github.solcott.activitytracker.location.SegmentHeading
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList

/**
 * One row of segment browse (segments/segment-groups): the listed segments of a segment group, which follow one path in any sport group and
 * either direction. The row is drawn, selected and opened as its [shown] member, so the map and the selection key on that member's URI.
 */
@Immutable
data class SegmentGroupRow(
    /** The segment group's listed segments, in the order they were listed. */
    val members: ImmutableList<SegmentView>,
    /** The member the row is drawn, opened and selected as: the chosen sport group's first, else the first group's in app order. */
    val shown: SegmentView,
    /** The members' sport groups, in [SegmentSportGroup.all] order. */
    val sports: ImmutableList<String>,
    /** Whether the members run both ways along the path. */
    val bothWays: Boolean,
    /** Which ways each of [sports] goes (segments/segment-groups), in the same order. */
    val ways: ImmutableList<SportWay> = persistentListOf(),
) {
    /** The level its [shown] member is drawn and marked in (segments/trail-ratings); null unrated. */
    val rating: TrailRating?
        get() = TrailRating.of(shown)

    /** The segment group's key: the segment naming it. */
    val key: AtUri
        get() = shown.groupKey

    companion object {
        /**
         * [segments] grouped by segment group in the order each segment group first appears, each shown as the first member of [sport], or
         * of the first sport group in app order when [sport] is null.
         */
        fun of(segments: List<SegmentView>, sport: String?): List<SegmentGroupRow> =
            segments.groupBy { it.groupKey }.values.map { members -> groupOf(members, sport) }

        /**
         * [name] as a segment group's row shows it (segments/segment-groups): for a row running [bothWays], without the direction the
         * segment importer adds, or with its one "→" read as "↔"; otherwise, and for any other name, as it is.
         */
        fun displayName(name: String, bothWays: Boolean): String {
            val direction = DIRECTION_SUFFIX.find(name)?.takeIf { SegmentDirection.ofWord(it.groupValues[1]) != null }
            return when {
                !bothWays -> name
                direction != null -> name.removeRange(direction.range)
                name.count { it == '→' } == 1 -> name.replace('→', '↔')
                else -> name
            }
        }

        /** [members], all of one segment group, as a row. */
        fun groupOf(members: List<SegmentView>, sport: String? = null): SegmentGroupRow {
            val order = SegmentSportGroup.all
            val sports = members.map { it.record.sport }.distinct().sortedBy { order.indexOf(it).takeIf { i -> i >= 0 } ?: order.size }
            val shownSport = sport?.takeIf { it in sports } ?: sports.first()
            val shown = members.first { it.record.sport == shownSport }
            return SegmentGroupRow(
                members = members.toImmutableList(),
                shown = shown,
                sports = sports.toImmutableList(),
                bothWays = bothWays(shown, members),
                ways = sports.map { group -> wayOf(group, members.filter { it.record.sport == group }) }.toImmutableList(),
            )
        }

        /**
         * Which ways [sport]'s [members] go: both ways when they run in both directions, one way when they all run one way and are one-way
         * (segments/segment-direction), never for alpine, and otherwise unstated.
         */
        private fun wayOf(sport: String, members: List<SegmentView>): SportWay {
            val first = members.first()
            val reference = lineOf(first)
            val bothWays = reference != null && members.mapNotNull(::lineOf).any { !sameWay(reference, it) }
            val oneWay = !bothWays && sport != SegmentSportGroup.ALPINE && members.all { it.oneWay == true }
            val direction = reference?.takeIf { oneWay }?.let { SegmentDirection.of(SegmentHeading.of(it), first.servedClimb()) }
            return SportWay(sport, direction, bothWays)
        }

        /** Whether any of [members] runs the other way from [shown]. */
        private fun bothWays(shown: SegmentView, members: List<SegmentView>): Boolean {
            val reference = lineOf(shown) ?: return false
            return members.any { member -> lineOf(member)?.let { !sameWay(reference, it) } == true }
        }

        /**
         * Whether [line] runs the same way as [reference]: for loops, the same turn; otherwise, its start is nearer [reference]'s start
         * than its end.
         */
        internal fun sameWay(reference: List<GeoPoint>, line: List<GeoPoint>): Boolean {
            val heading = SegmentHeading.of(reference)
            if (heading == SegmentHeading.Clockwise || heading == SegmentHeading.Counterclockwise) return heading == SegmentHeading.of(line)
            return SegmentGeometry.distanceMeters(line.first(), reference.first()) <
                SegmentGeometry.distanceMeters(line.first(), reference.last())
        }

        private fun lineOf(segment: SegmentView): List<GeoPoint>? =
            SegmentGeometry.decodePolyline(segment.record.line)?.takeIf { it.size >= 2 }
    }
}

/**
 * Which ways a segment group's [sport] goes (segments/segment-groups): [bothWays], one way only in [direction] when it is one-way
 * (segments/segment-direction), or neither, when its listed segments run one way without being one-way.
 */
@Immutable data class SportWay(val sport: String, val direction: SegmentDirection?, val bothWays: Boolean)

/**
 * Whether this segment, run as drawn, clearly climbs or descends (segments/segment-direction): its served climb, which the importer judges
 * along its whole trail, else its served elevation by the net-change rule; null when neither says.
 */
internal fun SegmentView.servedClimb(): SegmentClimb? =
    SegmentClimb.ofWord(climb) ?: SegmentClimb.of(elevationGain, elevationLoss, distanceMeters)

/** A direction the importer adds to a two-way trail's name, in parentheses at its end (segments/trail-candidates). */
private val DIRECTION_SUFFIX = Regex(""" \(([a-z]+)\)$""")
