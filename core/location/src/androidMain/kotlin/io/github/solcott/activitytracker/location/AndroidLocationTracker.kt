package io.github.solcott.activitytracker.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.os.Looper
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlin.time.Instant

/**
 * Fused location updates while the app is in the foreground.
 * TODO: move collection into a foreground service so recording continues with the screen off.
 */
@ContributesBinding(AppScope::class)
@Inject
class AndroidLocationTracker(private val context: Context) : LocationTracker {
    private val client by lazy { LocationServices.getFusedLocationProviderClient(context) }

    override fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    @SuppressLint("MissingPermission")
    override fun updates(): Flow<LocationSample> =
        callbackFlow {
            check(hasPermission()) { "Location permission not granted" }
            val callback =
                object : LocationCallback() {
                    override fun onLocationResult(result: LocationResult) {
                        result.locations.forEach { location ->
                            trySend(
                                LocationSample(
                                    latitude = location.latitude,
                                    longitude = location.longitude,
                                    altitude = location.altitude.takeIf { location.hasAltitude() },
                                    speed = location.speed.toDouble().takeIf { location.hasSpeed() },
                                    accuracy = location.accuracy.toDouble().takeIf { location.hasAccuracy() },
                                    time = Instant.fromEpochMilliseconds(location.time),
                                ),
                            )
                        }
                    }
                }
            val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 1_000).build()
            client.requestLocationUpdates(request, callback, Looper.getMainLooper())
            awaitClose { client.removeLocationUpdates(callback) }
        }
}
