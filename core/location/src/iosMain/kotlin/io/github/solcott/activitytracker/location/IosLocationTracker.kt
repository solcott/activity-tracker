package io.github.solcott.activitytracker.location

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import kotlin.time.Instant
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.useContents
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import platform.CoreLocation.CLActivityTypeFitness
import platform.CoreLocation.CLLocation
import platform.CoreLocation.CLLocationManager
import platform.CoreLocation.CLLocationManagerDelegateProtocol
import platform.CoreLocation.kCLAuthorizationStatusAuthorizedAlways
import platform.CoreLocation.kCLAuthorizationStatusAuthorizedWhenInUse
import platform.CoreLocation.kCLLocationAccuracyBest
import platform.Foundation.NSDate
import platform.Foundation.timeIntervalSince1970
import platform.darwin.NSObject

@ContributesBinding(AppScope::class)
@Inject
class IosLocationTracker : LocationTracker {
    private val manager = CLLocationManager()

    /** CLLocationManager holds its delegate weakly: this keeps the one listening alive until its updates stop. */
    private var listening: CLLocationManagerDelegateProtocol? = null

    override fun hasPermission(): Boolean =
        manager.authorizationStatus.let { it == kCLAuthorizationStatusAuthorizedWhenInUse || it == kCLAuthorizationStatusAuthorizedAlways }

    @OptIn(ExperimentalForeignApi::class)
    override fun updates(): Flow<LocationSample> = callbackFlow {
        // Core Location often hands over its last known fix first, from before this started: maybe minutes old and far away.
        val startedAt = NSDate().timeIntervalSince1970
        val delegate =
            object : NSObject(), CLLocationManagerDelegateProtocol {
                override fun locationManager(
                    manager: CLLocationManager,
                    didUpdateLocations: List<*>,
                ) {
                    // A negative horizontal accuracy means the coordinate is invalid, not that it's exact.
                    val valid =
                        didUpdateLocations.filterIsInstance<CLLocation>().filter {
                            it.horizontalAccuracy >= 0 && it.timestamp.timeIntervalSince1970 >= startedAt - CACHED_FIX_TOLERANCE_S
                        }
                    valid.forEach { location ->
                        val (lat, lon) = location.coordinate.useContents { latitude to longitude }
                        trySend(
                            LocationSample(
                                latitude = lat,
                                longitude = lon,
                                altitude = location.altitude.takeIf { location.verticalAccuracy >= 0 },
                                speed = location.speed.takeIf { it >= 0 },
                                accuracy = location.horizontalAccuracy.takeIf { it >= 0 },
                                time = Instant.fromEpochMilliseconds((location.timestamp.timeIntervalSince1970 * 1000).toLong()),
                            )
                        )
                    }
                }
            }
        listening = delegate
        manager.delegate = delegate
        manager.desiredAccuracy = kCLLocationAccuracyBest
        // Recording goes on in the background (record/gps-recording). Info.plist's `UIBackgroundModes: location` must be there, or
        // allowsBackgroundLocationUpdates crashes the app. "When in use" is enough, since a recording always starts in the foreground.
        manager.allowsBackgroundLocationUpdates = true
        manager.pausesLocationUpdatesAutomatically = false
        manager.activityType = CLActivityTypeFitness
        manager.showsBackgroundLocationIndicator = true
        if (!hasPermission()) manager.requestWhenInUseAuthorization()
        manager.startUpdatingLocation()
        awaitClose {
            manager.stopUpdatingLocation()
            manager.allowsBackgroundLocationUpdates = false
            manager.delegate = null
            if (listening === delegate) listening = null
        }
    }
}

/** How far before updates start a fix may be dated and still count as fresh (a fix computed as they started). */
private const val CACHED_FIX_TOLERANCE_S = 1.0
