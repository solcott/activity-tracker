package io.github.solcott.activitytracker.location

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/** A circle hidden from routes: a privacy zone's (already offset) center and radius. */
data class PrivacyCircle(val latitude: Double, val longitude: Double, val radiusMeters: Double) {
    fun contains(latitude: Double, longitude: Double): Boolean =
        TrackMath.distanceMeters(this.latitude, this.longitude, latitude, longitude) <= radiusMeters

    companion object {
        private const val E7 = 1e7
        private const val EARTH_RADIUS_M = 6_371_000.0
        private const val DEGREES_PER_HALF_TURN = 180.0

        fun fromE7(latitudeE7: Int, longitudeE7: Int, radiusMeters: Int) =
            PrivacyCircle(latitudeE7 / E7, longitudeE7 / E7, radiusMeters.toDouble())

        fun toE7(degrees: Double): Int = (degrees * E7).roundToInt()

        /**
         * A point uniformly random in the disk of radius [radiusMeters] / 2 around ([latitude], [longitude]): a new zone's stored center,
         * so the hole repeated activities leave doesn't center on the place chosen, which stays at least half the radius inside the zone.
         */
        fun offsetCenter(latitude: Double, longitude: Double, radiusMeters: Double, random: Random = Random.Default): Pair<Double, Double> {
            val distance = radiusMeters / 2 * sqrt(random.nextDouble())
            val bearing = 2 * PI * random.nextDouble()
            val dLat = distance * cos(bearing) / EARTH_RADIUS_M
            val dLon = distance * sin(bearing) / (EARTH_RADIUS_M * cos(latitude * PI / DEGREES_PER_HALF_TURN))
            return latitude + dLat.toDegrees() to longitude + dLon.toDegrees()
        }

        private fun Double.toDegrees() = this * DEGREES_PER_HALF_TURN / PI
    }
}
