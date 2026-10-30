package io.github.solcott.activitytracker.app

import androidx.compose.runtime.Immutable

/**
 * Where a route map marks the activity's [start] and [end], as (latitude, longitude), when that isn't its line's own ends: the owner's map
 * draws the untrimmed track but marks where the route others see starts and ends. Null where the athlete's privacy settings hide it, or
 * while they can't be read.
 */
@Immutable
data class RouteEnds(val start: Pair<Double, Double>?, val end: Pair<Double, Double>?) {
    companion object {
        val HIDDEN = RouteEnds(null, null)
    }
}
