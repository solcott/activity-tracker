package io.github.solcott.activitytracker.location

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.flow.first

/**
 * Where the athlete is, read once to choose where a map opens (segments/segment-browse), never to record. Phones read it from their GPS
 * ([TrackerPlaceLocator]); the desktop app asks its operating system and replaces that binding; web, with no map, has none.
 */
interface PlaceLocator {
    /** How long a map holds its first view for [here] before opening at its fallback instead, and moving later if [here] still answers. */
    val hold: Duration

    /**
     * Whether the location may be read: false where there's none, true when it may be tried, null until the athlete is asked in the app.
     */
    fun permission(): Boolean?

    /** One location, or null when reading it failed. The caller bounds how long it waits; this only throws on cancellation. */
    suspend fun here(): GeoPoint?
}

/**
 * A [PlaceLocator] over the platform's GPS: the first fix [tracker] reports. Over [LocationTracker]s that aren't supported, it has none.
 */
@ContributesBinding(AppScope::class)
@Inject
class TrackerPlaceLocator(private val tracker: LocationTracker) : PlaceLocator {
    override val hold: Duration = 5.seconds

    override fun permission(): Boolean? =
        when {
            !tracker.isSupported -> false
            tracker.hasPermission() -> true
            else -> null
        }

    override suspend fun here(): GeoPoint? =
        try {
            tracker.updates().first().let { GeoPoint(it.latitude, it.longitude) }
        } catch (e: CancellationException) {
            throw e
        } catch (@Suppress("TooGenericExceptionCaught") _: Exception) {
            // No fix at all (the updates ended) or the platform failed: either way, no location.
            null
        }
}
