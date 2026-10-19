package io.github.solcott.activitytracker.data.activity

import io.github.solcott.activitytracker.data.streams.StreamAnalytics
import io.github.solcott.activitytracker.location.LocationSample
import io.github.solcott.activitytracker.location.TrackMath

/**
 * Where a single activity's map line breaks: between positions recorded far apart in both time and space, so the map draws no straight line
 * across ground the device didn't record (a dead GPS, a tunnel, a shuttle). A combined activity breaks between its members instead
 * ([CombinedRoute.segments]).
 */
object RouteBreaks {
    /**
     * [route] (in time order) cut between consecutive positions further apart in time than the charts' gap limit
     * ([StreamAnalytics.gapLimit] of the route's times) and more than [MIN_BREAK_METERS] apart. A stop where the athlete stood still stays
     * whole. At most [MAX_BREAKS] cuts, the ones between the positions furthest apart.
     */
    fun segments(route: List<LocationSample>): List<List<LocationSample>> {
        if (route.size < 2) return listOf(route).filter { it.isNotEmpty() }
        val start = route.first().time
        val limit = StreamAnalytics.gapLimit(route.map { (it.time - start).inWholeSeconds.toInt() })
        val breaks =
            route
                .zipWithNext()
                .mapIndexedNotNull { i, (a, b) ->
                    val meters = TrackMath.distanceMeters(a, b)
                    (i + 1 to meters).takeIf { (b.time - a.time).inWholeSeconds > limit && meters > MIN_BREAK_METERS }
                }
                .sortedByDescending { it.second }
                .take(MAX_BREAKS)
                .map { it.first }
                .sorted()
        val bounds = listOf(0) + breaks + route.size
        return bounds.zipWithNext { from, to -> route.subList(from, to) }
    }

    /** Positions closer than this stay joined across any gap: the athlete didn't go anywhere. */
    const val MIN_BREAK_METERS = 200.0

    /** The most breaks a polyline carries (`feed.activity#polylineGaps`'s `maxLength`). */
    const val MAX_BREAKS = 10
}
