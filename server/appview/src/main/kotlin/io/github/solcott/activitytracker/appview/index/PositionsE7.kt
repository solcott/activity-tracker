package io.github.solcott.activitytracker.appview.index

// Positions as the lexicons and tables store them: integer degrees times 10^7. Conversions are core/location's (`E7.kt`).

internal const val MAX_LAT_E7 = 900_000_000
internal const val MAX_LNG_E7 = 1_800_000_000

/** Whether [lat] and [lng], in 1e-7 degrees, are a valid position. */
internal fun validPositionE7(lat: Int, lng: Int): Boolean = lat in -MAX_LAT_E7..MAX_LAT_E7 && lng in -MAX_LNG_E7..MAX_LNG_E7
