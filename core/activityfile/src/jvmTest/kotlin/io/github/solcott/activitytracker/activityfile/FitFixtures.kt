package io.github.solcott.activitytracker.activityfile

import com.garmin.fit.BufferEncoder
import com.garmin.fit.DateTime
import com.garmin.fit.File
import com.garmin.fit.FileIdMesg
import com.garmin.fit.Fit
import com.garmin.fit.GarminProduct
import com.garmin.fit.LapMesg
import com.garmin.fit.Manufacturer
import com.garmin.fit.RecordMesg
import com.garmin.fit.SessionMesg
import com.garmin.fit.Sport as FitSport
import com.garmin.fit.SubSport
import kotlin.math.roundToInt
import kotlin.time.Instant
import kotlin.time.toJavaInstant

/** Small FIT activity files, written with Garmin's encoder so the decoder reads the same bytes a device would produce. */
object FitFixtures {
    const val RUN_POINTS = 11

    private const val SEMICIRCLES_PER_DEGREE = 2_147_483_648.0 / 180.0

    /** A 1 km run north from 47.6, -122.3: one point every 30 s, heart rate climbing from 120 to 190 bpm (avg 155). */
    fun run(start: Instant): ByteArray =
        encode(start, FitSport.RUNNING, SubSport.GENERIC, distance = 1000f, avgHr = 155, points = RUN_POINTS) { i, record ->
            record.positionLat = ((47.6 + i * 0.0009) * SEMICIRCLES_PER_DEGREE).roundToInt()
            record.positionLong = (-122.3 * SEMICIRCLES_PER_DEGREE).roundToInt()
            record.enhancedAltitude = 50f + i
            record.heartRate = (120 + i * 7).toShort()
            record.distance = i * 100f
        }

    /** A trainer ride: power and cadence, no position. */
    fun indoorRide(start: Instant): ByteArray =
        encode(start, FitSport.CYCLING, SubSport.INDOOR_CYCLING, distance = 5000f, avgHr = 140, points = 5) { _, record ->
            record.power = 200
            record.cadence = 90.toShort()
        }

    private fun encode(
        start: Instant,
        sport: FitSport,
        subSport: SubSport,
        distance: Float,
        avgHr: Int,
        points: Int,
        fill: (Int, RecordMesg) -> Unit,
    ): ByteArray {
        val startedAt = DateTime(start.toJavaInstant())
        val elapsed = (points - 1) * 30f
        val end = DateTime(startedAt).apply { add(elapsed.toLong()) }
        val encoder = BufferEncoder(Fit.ProtocolVersion.V2_0)
        encoder.write(
            FileIdMesg().apply {
                type = File.ACTIVITY
                manufacturer = Manufacturer.GARMIN
                product = GarminProduct.FENIX7
                timeCreated = startedAt
                serialNumber = 1234L
            }
        )
        repeat(points) { i ->
            encoder.write(
                RecordMesg().apply {
                    timestamp = DateTime(startedAt).apply { add(i * 30L) }
                    fill(i, this)
                }
            )
        }
        encoder.write(
            LapMesg().apply {
                timestamp = end
                startTime = DateTime(startedAt)
                totalElapsedTime = elapsed
                totalTimerTime = elapsed
                totalDistance = distance
            }
        )
        encoder.write(
            SessionMesg().apply {
                timestamp = end
                startTime = DateTime(startedAt)
                this.sport = sport
                this.subSport = subSport
                totalElapsedTime = elapsed
                totalTimerTime = elapsed
                totalDistance = distance
                avgHeartRate = avgHr.toShort()
            }
        )
        return encoder.close()
    }
}
