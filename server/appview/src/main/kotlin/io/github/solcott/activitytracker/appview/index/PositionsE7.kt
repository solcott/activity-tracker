package io.github.solcott.activitytracker.appview.index

import io.github.solcott.activitytracker.location.GeoPoint
import kotlin.math.roundToInt

// Positions as the lexicons and tables store them: integer degrees times 10^7.

/** Degrees to 1e-7 degrees. */
internal const val E7 = 1e7

internal const val MAX_LAT_E7 = 900_000_000
internal const val MAX_LNG_E7 = 1_800_000_000

/** The point of [lat] and [lng], in 1e-7 degrees. */
internal fun pointE7(lat: Int, lng: Int) = GeoPoint(lat / E7, lng / E7)

/** Whether [lat] and [lng], in 1e-7 degrees, are a valid position. */
internal fun validPositionE7(lat: Int, lng: Int): Boolean = lat in -MAX_LAT_E7..MAX_LAT_E7 && lng in -MAX_LNG_E7..MAX_LNG_E7

/** Degrees to the nearest 1e-7 degree. */
internal fun Double.e7(): Int = (this * E7).roundToInt()
