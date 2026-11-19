package io.github.solcott.activitytracker.app.segment

import androidx.compose.runtime.Immutable
import io.github.solcott.activitytracker.app.text.AppText
import io.github.solcott.activitytracker.lexicon.AtUri
import io.github.solcott.activitytracker.lexicon.app.GetSegment
import io.github.solcott.activitytracker.lexicon.app.SegmentSportGroup
import io.github.solcott.activitytracker.lexicon.app.SegmentView
import io.github.solcott.activitytracker.location.GeoPoint
import io.github.solcott.activitytracker.location.SegmentDirection
import io.github.solcott.activitytracker.location.SegmentGeometry
import io.github.solcott.activitytracker.location.SegmentHeading
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList

/**
 * A segment of the segment group the switcher offers: its [sport], whether it runs the shown segment's way ([forward]), its [heading], and
 * its [direction] as the switcher names it (segments/segment-direction).
 */
@Immutable
data class GroupOption(
    val uri: AtUri,
    val sport: String,
    val forward: Boolean,
    val heading: SegmentHeading,
    val direction: SegmentDirection = SegmentDirection.of(heading, null),
)

/**
 * One direction of the switcher: the way the shown segment runs ([forward]) or the other, ordered by [heading] and named by [direction]:
 * uphill or downhill when the line clearly climbs or descends, else by [heading].
 */
@Immutable
data class GroupDirection(
    val forward: Boolean,
    val heading: SegmentHeading,
    val direction: SegmentDirection = SegmentDirection.of(heading, null),
)

/**
 * The segment page's choice of the segment group's sport groups and directions (segments/segment-groups), over [options], every segment of
 * the segment group including the shown one, [current]. Choosing shows the chosen segment on the same page.
 */
@Immutable
data class GroupSwitcher(
    val options: ImmutableList<GroupOption>,
    val current: AtUri,
    /** Whether the shown segment is one-way (segments/segment-direction), and so its sport group's one direction is explained. */
    val shownOneWay: Boolean = false,
) {
    private val shown: GroupOption
        get() = options.first { it.uri == current }

    /**
     * Whether the shown sport group goes only the shown segment's way, by its source data's rules: one-way, never alpine, with no member
     * the other way. Its one direction is shown as a choice that can't be made ("Uphill only").
     */
    val oneWay: Boolean
        get() = shownOneWay && shown.sport != SegmentSportGroup.ALPINE && options.none { it.sport == shown.sport && !it.forward }

    /** Whether the page shows the switcher: the segment group has another segment, or the shown one is [oneWay]. */
    val offered: Boolean
        get() = options.size > 1 || oneWay

    /** Whether the sport groups are offered: only when the segment group has another segment. */
    val showsSports: Boolean
        get() = options.size > 1

    /** The segment group's sport groups, in app order. */
    val sports: List<String>
        get() = options.map { it.sport }.distinct().sortedBy { SegmentSportGroup.all.indexOf(it).takeIf { i -> i >= 0 } ?: Int.MAX_VALUE }

    /** The shown segment's sport group. */
    val currentSport: String
        get() = shown.sport

    /**
     * The directions the shown sport group runs, in a fixed order (by [SegmentHeading]: northbound, eastbound, southbound, westbound,
     * clockwise, counterclockwise), so switching never moves them.
     */
    val directions: List<GroupDirection>
        get() =
            options
                .filter { it.sport == shown.sport }
                .distinctBy { it.forward }
                .map { GroupDirection(it.forward, it.heading, it.direction) }
                .sortedBy { it.heading.ordinal }

    /** [uri] when it is one of the segment group's segments, so the page may show it; null otherwise. */
    fun offering(uri: AtUri): AtUri? = uri.takeIf { options.any { option -> option.uri == it } }

    /** Whether [direction] is the shown segment's. */
    fun isCurrent(direction: GroupDirection): Boolean = direction.forward

    /** The event for choosing [sport]: its segment the shown one's way, else its first; none for the shown sport group. */
    fun sportEvent(sport: String): SegmentEvent? =
        options
            .filter { it.sport == sport && it.uri != current }
            .takeIf { sport != shown.sport }
            ?.let { choices -> choices.firstOrNull { it.forward } ?: choices.firstOrNull() }
            ?.let { SegmentEvent.SwitchGroup(it.uri) }

    /** The event for choosing [direction] in the shown sport group; none for the shown direction. */
    fun directionEvent(direction: GroupDirection): SegmentEvent? =
        options
            .takeIf { !direction.forward }
            ?.firstOrNull { it.sport == shown.sport && !it.forward }
            ?.let { SegmentEvent.SwitchGroup(it.uri) }

    companion object {
        /**
         * The switcher for [segment] and the rest of its segment group, [members]; null when its line doesn't decode. Every member shares
         * the shown segment's line, so one running the other way climbs as much as the shown one descends.
         */
        fun of(segment: SegmentView, members: List<GetSegment.GroupMember>): GroupSwitcher? {
            val reference = lineOf(segment.record.line) ?: return null
            val climb = segment.servedClimb()
            fun option(uri: AtUri, sport: String, forward: Boolean, line: List<GeoPoint>): GroupOption {
                val heading = SegmentHeading.of(line)
                return GroupOption(uri, sport, forward, heading, SegmentDirection.of(heading, if (forward) climb else climb?.reversed()))
            }
            val options =
                listOf(option(segment.uri, segment.record.sport, forward = true, reference)) +
                    members.mapNotNull { member ->
                        lineOf(member.line)?.let { line ->
                            option(member.uri, member.sport, SegmentGroupRow.sameWay(reference, line), line)
                        }
                    }
            return GroupSwitcher(options.toImmutableList(), segment.uri, shownOneWay = segment.oneWay == true)
        }

        private fun lineOf(line: String): List<GeoPoint>? = SegmentGeometry.decodePolyline(line)?.takeIf { it.size >= 2 }
    }
}

/** What the segment group switcher says (segments/segment-groups, dev/shared-display-text). */
object GroupSwitcherTexts {
    /** A sport group's choice ("Ride"). */
    fun sport(sport: String): AppText = SegmentTexts.sportGroup(sport)

    /** A direction's choice ("Uphill", "Eastbound", "Clockwise"; segments/segment-direction). */
    fun direction(direction: GroupDirection): AppText = AppText.named("segment_heading_${direction.direction.word}")

    /** A one-way sport group's one direction, shown as a choice that can't be made ("Uphill only"). */
    fun oneWay(direction: GroupDirection): AppText = AppText.named("segment_one_way_${direction.direction.word}")
}
