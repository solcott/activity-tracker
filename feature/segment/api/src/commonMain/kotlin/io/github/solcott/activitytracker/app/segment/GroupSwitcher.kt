package io.github.solcott.activitytracker.app.segment

import androidx.compose.runtime.Immutable
import io.github.solcott.activitytracker.app.text.AppText
import io.github.solcott.activitytracker.lexicon.AtUri
import io.github.solcott.activitytracker.lexicon.app.GetSegment
import io.github.solcott.activitytracker.lexicon.app.SegmentSportGroup
import io.github.solcott.activitytracker.lexicon.app.SegmentView
import io.github.solcott.activitytracker.location.GeoPoint
import io.github.solcott.activitytracker.location.SegmentGeometry
import io.github.solcott.activitytracker.location.SegmentHeading
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList

/**
 * A segment of the segment group the switcher offers: its [sport], whether it runs the shown segment's way ([forward]), and its [heading].
 */
@Immutable data class GroupOption(val uri: AtUri, val sport: String, val forward: Boolean, val heading: SegmentHeading)

/** One direction of the switcher: the way the shown segment runs ([forward]) or the other, named by [heading]. */
@Immutable data class GroupDirection(val forward: Boolean, val heading: SegmentHeading)

/**
 * The segment page's choice of the segment group's sport groups and directions (segments/segment-groups), over [options], every segment of
 * the segment group including the shown one, [current]. Choosing shows the chosen segment on the same page.
 */
@Immutable
data class GroupSwitcher(val options: ImmutableList<GroupOption>, val current: AtUri) {
    private val shown: GroupOption
        get() = options.first { it.uri == current }

    /** Whether there's anything to switch to: the segment group has another segment. */
    val offered: Boolean
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
                .map { GroupDirection(it.forward, it.heading) }
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
        /** The switcher for [segment] and the rest of its segment group, [members]; null when its line doesn't decode. */
        fun of(segment: SegmentView, members: List<GetSegment.GroupMember>): GroupSwitcher? {
            val reference = lineOf(segment.record.line) ?: return null
            val options =
                listOf(GroupOption(segment.uri, segment.record.sport, forward = true, heading = SegmentHeading.of(reference))) +
                    members.mapNotNull { member ->
                        lineOf(member.line)?.let { line ->
                            GroupOption(member.uri, member.sport, SegmentGroupRow.sameWay(reference, line), SegmentHeading.of(line))
                        }
                    }
            return GroupSwitcher(options.toImmutableList(), segment.uri)
        }

        private fun lineOf(line: String): List<GeoPoint>? = SegmentGeometry.decodePolyline(line)?.takeIf { it.size >= 2 }
    }
}

/** What the segment group switcher says (segments/segment-groups, dev/shared-display-text). */
object GroupSwitcherTexts {
    /** A sport group's choice ("Ride"). */
    fun sport(sport: String): AppText = SegmentTexts.sportGroup(sport)

    /** A direction's choice ("Eastbound", "Clockwise"). */
    fun direction(direction: GroupDirection): AppText = AppText.named("segment_heading_${direction.heading.word}")
}
