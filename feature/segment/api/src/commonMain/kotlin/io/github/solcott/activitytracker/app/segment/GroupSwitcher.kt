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

/** A segment of the route the switcher offers: its [sport], whether it runs the shown segment's way ([forward]), and its [heading]. */
@Immutable data class RouteOption(val uri: AtUri, val sport: String, val forward: Boolean, val heading: SegmentHeading)

/** One direction of the switcher: the way the shown segment runs ([forward]) or the other, named by [heading]. */
@Immutable data class RouteDirection(val forward: Boolean, val heading: SegmentHeading)

/**
 * The segment page's choice of the route's sport groups and directions (segments/segment-routes), over [options], every segment of the
 * route including the shown one, [current]. Choosing shows the chosen segment on the same page.
 */
@Immutable
data class RouteSwitcher(val options: ImmutableList<RouteOption>, val current: AtUri) {
    private val shown: RouteOption
        get() = options.first { it.uri == current }

    /** Whether there's anything to switch to: the route has another segment. */
    val offered: Boolean
        get() = options.size > 1

    /** The route's sport groups, in app order. */
    val sports: List<String>
        get() = options.map { it.sport }.distinct().sortedBy { SegmentSportGroup.all.indexOf(it).takeIf { i -> i >= 0 } ?: Int.MAX_VALUE }

    /** The shown segment's sport group. */
    val currentSport: String
        get() = shown.sport

    /**
     * The directions the shown sport group runs, in a fixed order (by [SegmentHeading]: northbound, eastbound, southbound, westbound,
     * clockwise, counterclockwise), so switching never moves them.
     */
    val directions: List<RouteDirection>
        get() =
            options
                .filter { it.sport == shown.sport }
                .distinctBy { it.forward }
                .map { RouteDirection(it.forward, it.heading) }
                .sortedBy { it.heading.ordinal }

    /** [uri] when it is one of the route's segments, so the page may show it; null otherwise. */
    fun offering(uri: AtUri): AtUri? = uri.takeIf { options.any { option -> option.uri == it } }

    /** Whether [direction] is the shown segment's. */
    fun isCurrent(direction: RouteDirection): Boolean = direction.forward

    /** The event for choosing [sport]: its segment the shown one's way, else its first; none for the shown sport group. */
    fun sportEvent(sport: String): SegmentEvent? =
        options
            .filter { it.sport == sport && it.uri != current }
            .takeIf { sport != shown.sport }
            ?.let { choices -> choices.firstOrNull { it.forward } ?: choices.firstOrNull() }
            ?.let { SegmentEvent.SwitchRoute(it.uri) }

    /** The event for choosing [direction] in the shown sport group; none for the shown direction. */
    fun directionEvent(direction: RouteDirection): SegmentEvent? =
        options
            .takeIf { !direction.forward }
            ?.firstOrNull { it.sport == shown.sport && !it.forward }
            ?.let { SegmentEvent.SwitchRoute(it.uri) }

    companion object {
        /** The switcher for [segment] and the rest of its route, [members]; null when its line doesn't decode. */
        fun of(segment: SegmentView, members: List<GetSegment.RouteMember>): RouteSwitcher? {
            val reference = lineOf(segment.record.line) ?: return null
            val options =
                listOf(RouteOption(segment.uri, segment.record.sport, forward = true, heading = SegmentHeading.of(reference))) +
                    members.mapNotNull { member ->
                        lineOf(member.line)?.let { line ->
                            RouteOption(member.uri, member.sport, SegmentRoute.sameWay(reference, line), SegmentHeading.of(line))
                        }
                    }
            return RouteSwitcher(options.toImmutableList(), segment.uri)
        }

        private fun lineOf(line: String): List<GeoPoint>? = SegmentGeometry.decodePolyline(line)?.takeIf { it.size >= 2 }
    }
}

/** What the route switcher says (segments/segment-routes, dev/shared-display-text). */
object RouteSwitcherTexts {
    /** A sport group's choice ("Ride"). */
    fun sport(sport: String): AppText = SegmentTexts.sportGroup(sport)

    /** A direction's choice ("Eastbound", "Clockwise"). */
    fun direction(direction: RouteDirection): AppText = AppText.named("segment_heading_${direction.heading.word}")
}
