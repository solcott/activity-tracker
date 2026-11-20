package io.github.solcott.activitytracker.desktop.location

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import io.github.solcott.activitytracker.location.GeoPoint
import io.github.solcott.activitytracker.location.PlaceLocator
import io.github.solcott.activitytracker.location.TrackerPlaceLocator
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/** One way to ask this computer's operating system where it is. Blocking calls are its own to make interruptible. */
fun interface LocationBackend {
    /** One location, or null when the OS has none to give; may throw, which also means none. */
    suspend fun locate(): GeoPoint?
}

/**
 * Where the desktop app's athlete is, from the operating system's location service (segments/segment-browse): Location Services through a
 * small helper app on macOS ([MacLocationBackend]), the Windows location service ([WindowsLocationBackend]), GeoClue on Linux
 * ([GeoClueLocationBackend]). The OS shows its own permission prompt, so the app asks nothing itself. Read only to open map browse, never
 * kept or sent, and it doesn't make desktop a recording platform: `LocationTracker` stays unsupported there.
 */
@ContributesBinding(AppScope::class, replaces = [TrackerPlaceLocator::class])
class DesktopPlaceLocator(private val backend: LocationBackend?) : PlaceLocator {
    @Inject constructor() : this(locationBackendForThisComputer())

    /** A granted location answers in well under a second; past this the athlete is likely reading the OS's prompt. */
    override val hold: Duration = 2.seconds

    override fun permission(): Boolean = backend != null

    override suspend fun here(): GeoPoint? =
        try {
            backend?.locate()?.takeIf { it.isOnEarth() }
        } catch (e: CancellationException) {
            throw e
        } catch (@Suppress("TooGenericExceptionCaught") _: Exception) {
            null
        }
}

/** The backend for this computer's OS, or null where there's none (an unknown OS, or a macOS build without its helper). */
fun locationBackendForThisComputer(): LocationBackend? {
    val os = System.getProperty("os.name").orEmpty().lowercase()
    return when {
        os.startsWith("mac") -> MacLocationBackend.fromSystemProperty()
        os.startsWith("windows") -> WindowsLocationBackend()
        os.startsWith("linux") -> GeoClueLocationBackend()
        else -> null
    }
}

/** The largest latitude and longitude there are, in degrees. */
private const val MAX_LATITUDE = 90.0
private const val MAX_LONGITUDE = 180.0

/** Whether this is a real place: finite and within the globe's ranges, so a garbled answer never moves the map. */
internal fun GeoPoint.isOnEarth(): Boolean =
    lat.isFinite() && lng.isFinite() && lat in -MAX_LATITUDE..MAX_LATITUDE && lng in -MAX_LONGITUDE..MAX_LONGITUDE

/** [lat], [lng] as a [GeoPoint] when they're a real place, else null. */
internal fun pointOrNull(lat: Double?, lng: Double?): GeoPoint? =
    if (lat == null || lng == null) null else GeoPoint(lat, lng).takeIf { it.isOnEarth() }
