package io.github.solcott.activitytracker.location

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import kotlin.time.Instant
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/** `navigator.geolocation`. The browser prompts for permission on first use. */
@ContributesBinding(AppScope::class)
@Inject
class WebLocationTracker : LocationTracker {
    override fun hasPermission(): Boolean = true

    override fun updates(): Flow<LocationSample> = callbackFlow {
        val id =
            watchPosition(
                onPosition = { lat, lon, alt, speed, accuracy, timestamp ->
                    trySend(
                        LocationSample(
                            latitude = lat,
                            longitude = lon,
                            altitude = alt.takeUnless { it.isNaN() },
                            speed = speed.takeUnless { it.isNaN() },
                            accuracy = accuracy,
                            time = Instant.fromEpochMilliseconds(timestamp.toLong()),
                        )
                    )
                },
                onError = { message -> close(IllegalStateException("Geolocation error: $message")) },
            )
        awaitClose { clearWatch(id) }
    }
}

private fun watchPosition(
    onPosition: (Double, Double, Double, Double, Double, Double) -> Unit,
    onError: (String) -> Unit,
): Int =
    js(
        """navigator.geolocation.watchPosition(
        (p) => onPosition(p.coords.latitude, p.coords.longitude, p.coords.altitude ?? NaN, p.coords.speed ?? NaN, p.coords.accuracy, p.timestamp),
        (e) => onError(e.message),
        { enableHighAccuracy: true })"""
    )

private fun clearWatch(id: Int): Unit = js("navigator.geolocation.clearWatch(id)")
