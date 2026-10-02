package io.github.solcott.activitytracker.location

import kotlin.time.Instant
import kotlinx.coroutines.flow.Flow

data class LocationSample(
    val latitude: Double,
    val longitude: Double,
    /** Meters above sea level, if known. */
    val altitude: Double?,
    /** Meters per second, if reported by the platform. */
    val speed: Double?,
    /** Horizontal accuracy in meters. */
    val accuracy: Double?,
    val time: Instant,
)

/** Platform GPS. Collecting [updates] starts location updates; cancelling stops them. */
interface LocationTracker {
    /** Whether location permission is currently granted. Requesting it is the UI's job. */
    fun hasPermission(): Boolean

    fun updates(): Flow<LocationSample>
}
