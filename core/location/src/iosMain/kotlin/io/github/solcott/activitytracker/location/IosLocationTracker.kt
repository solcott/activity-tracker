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
import platform.CoreLocation.CLLocation
import platform.CoreLocation.CLLocationManager
import platform.CoreLocation.CLLocationManagerDelegateProtocol
import platform.CoreLocation.kCLAuthorizationStatusAuthorizedAlways
import platform.CoreLocation.kCLAuthorizationStatusAuthorizedWhenInUse
import platform.CoreLocation.kCLLocationAccuracyBest
import platform.Foundation.timeIntervalSince1970
import platform.darwin.NSObject

@ContributesBinding(AppScope::class)
@Inject
class IosLocationTracker : LocationTracker {
    private val manager = CLLocationManager()

    override fun hasPermission(): Boolean =
        manager.authorizationStatus.let { it == kCLAuthorizationStatusAuthorizedWhenInUse || it == kCLAuthorizationStatusAuthorizedAlways }

    @OptIn(ExperimentalForeignApi::class)
    override fun updates(): Flow<LocationSample> = callbackFlow {
        val delegate =
            object : NSObject(), CLLocationManagerDelegateProtocol {
                override fun locationManager(
                    manager: CLLocationManager,
                    didUpdateLocations: List<*>,
                ) {
                    didUpdateLocations.filterIsInstance<CLLocation>().forEach { location ->
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
        manager.delegate = delegate
        manager.desiredAccuracy = kCLLocationAccuracyBest
        if (!hasPermission()) manager.requestWhenInUseAuthorization()
        manager.startUpdatingLocation()
        awaitClose {
            manager.stopUpdatingLocation()
            manager.delegate = null
        }
    }
}
