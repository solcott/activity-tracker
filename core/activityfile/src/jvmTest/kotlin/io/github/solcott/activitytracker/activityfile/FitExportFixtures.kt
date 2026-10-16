package io.github.solcott.activitytracker.activityfile

import com.garmin.fit.Decode
import com.garmin.fit.Mesg
import com.garmin.fit.MesgListener
import io.github.solcott.activitytracker.lexicon.app.ActivityDive
import io.github.solcott.activitytracker.lexicon.app.ActivityDiveGas
import io.github.solcott.activitytracker.lexicon.app.ActivityDiveTank
import io.github.solcott.activitytracker.lexicon.app.GearChange
import io.github.solcott.activitytracker.lexicon.app.Sport
import io.github.solcott.activitytracker.lexicon.app.SubSport
import java.io.ByteArrayInputStream
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlinx.datetime.TimeZone

/** Activities to export as FIT, and Garmin's Java SDK to read the result. */
object FitExportFixtures {
    val start: Instant = Instant.parse("2026-09-20T07:00:00Z")

    /** 10 s of a road ride north from 47.6, -122.3 with every channel, and two shifts. */
    val ride =
        ExportActivity(
            name = "Morning Ride",
            sport = Sport.RIDE,
            subSport = SubSport.ROAD,
            tcxSport = TcxSport.Biking,
            startedAt = start,
            elapsed = 10.seconds,
            moving = 9.seconds,
            distanceMeters = 99.0,
            avgHeartRate = 125,
            maxHeartRate = 129,
            elevationGainMeters = 9.0,
            avgSpeedMps = 11.0,
            maxSpeedMps = 12.5,
            avgTemperatureCelsius = 18.0,
            isDive = false,
            points =
                (0 until 10).map {
                    ExportPoint(
                        time = start + it.seconds,
                        latitude = 47.6 + it * 0.0001,
                        longitude = -122.3,
                        altitudeMeters = 100.0 + it,
                        distanceMeters = it * 11.0,
                        speedMps = 11.0,
                        heartRate = 120 + it,
                        cadence = 85 + it,
                        powerWatts = 200 + it * 10,
                        temperatureCelsius = 18.0,
                    )
                },
            gearChanges = listOf(GearChange(time = 2, front = 2, rear = 5), GearChange(time = 6, front = 1, rear = 5)),
        )

    /** The same ride indoors: streams only, no positions. */
    val indoorRide =
        ride.copy(
            subSport = SubSport.INDOOR,
            points = ride.points.map { it.copy(latitude = null, longitude = null, altitudeMeters = null) },
            gearChanges = emptyList(),
        )

    /** A 2-minute closed-circuit dive to 30 m on air and 50% nitrox, with one tank. */
    val dive =
        ExportActivity(
            name = "Reef",
            sport = Sport.DIVE,
            subSport = SubSport.CCR,
            tcxSport = TcxSport.Other,
            startedAt = start,
            elapsed = 120.seconds,
            distanceMeters = null,
            avgHeartRate = null,
            maxHeartRate = null,
            isDive = true,
            points =
                (0..120 step 10).map {
                    ExportPoint(time = start + it.seconds, depthMeters = if (it in 1..119) 30.0 else 0.0, temperatureCelsius = 12.0)
                },
            dive =
                ActivityDive(
                    maxDepth = 3000,
                    avgDepth = 2500,
                    bottomTime = 90,
                    surfaceInterval = 3600,
                    diveNumber = 42,
                    closedCircuit = true,
                    gases = listOf(ActivityDiveGas(21, 0), ActivityDiveGas(50, 0)),
                    gradientFactorLow = 30,
                    gradientFactorHigh = 70,
                    startCns = 5,
                    endCns = 18,
                    otu = 25,
                    tanks = listOf(ActivityDiveTank(startPressure = 200_000, endPressure = 80_000, volumeUsed = 1200)),
                ),
        )

    fun write(activity: ExportActivity): ByteArray = FitWriter.write(activity, TimeZone.UTC, createdAt = start)

    /** Every message in [bytes], after checking the file's integrity with Garmin's SDK. */
    fun decode(bytes: ByteArray): List<Mesg> {
        assertTrue(Decode().checkFileIntegrity(ByteArrayInputStream(bytes)), "integrity")
        val messages = mutableListOf<Mesg>()
        Decode().read(ByteArrayInputStream(bytes), MesgListener { messages += it })
        return messages
    }
}
