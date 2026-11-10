package io.github.solcott.activitytracker.app

import androidx.compose.runtime.Immutable
import io.github.solcott.activitytracker.location.GeoPoint

/**
 * Where a route map marks the activity's [start] and [end] when that isn't its line's own ends: the owner's map draws the untrimmed track
 * but marks where the route others see starts and ends. Null where the athlete's privacy settings hide it, or while they can't be read.
 */
@Immutable
data class RouteEnds(val start: GeoPoint?, val end: GeoPoint?) {
    companion object {
        val HIDDEN = RouteEnds(null, null)
    }
}
