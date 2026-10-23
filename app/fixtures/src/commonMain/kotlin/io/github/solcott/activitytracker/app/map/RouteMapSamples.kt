package io.github.solcott.activitytracker.app.map

import io.github.solcott.activitytracker.app.ShownRoute
import io.github.solcott.activitytracker.app.fixtures.Fixed
import io.github.solcott.activitytracker.app.fixtures.contentSamples
import io.github.solcott.activitytracker.lexicon.app.ActivityType
import io.github.solcott.activitytracker.lexicon.app.Sport

object RouteMapSamples {
    private val content = RouteMapContent(ShownRoute(Fixed.POLYLINE, gaps = null, ends = null), "Morning Ride", ActivityType(Sport.RIDE))

    val all: Map<String, RouteMapState>
        get() = contentSamples(null, content) { RouteMapState(it) }
}
