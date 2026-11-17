package io.github.solcott.activitytracker.location

import kotlin.math.roundToInt

// Positions as the lexicons and tables store them: integer degrees times 10^7.

/** 1e-7 degrees in one degree. */
const val E7_PER_DEGREE = 1e7

/** Degrees to the nearest 1e-7 degree. */
fun Double.toE7(): Int = (this * E7_PER_DEGREE).roundToInt()

/** 1e-7 degrees to degrees. */
fun Int.e7ToDegrees(): Double = this / E7_PER_DEGREE

/** The point at [lat] and [lng], in 1e-7 degrees. */
fun pointE7(lat: Int, lng: Int): GeoPoint = GeoPoint(lat.e7ToDegrees(), lng.e7ToDegrees())
