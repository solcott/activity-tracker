package io.github.solcott.activitytracker.activityfile

import com.garmin.fit.Decode
import com.garmin.fit.Mesg
import com.garmin.fit.MesgListener
import io.github.solcott.activitytracker.lexicon.app.ActivityDive
import io.github.solcott.activitytracker.lexicon.app.ActivityDiveGas
import io.github.solcott.activitytracker.lexicon.app.ActivityDiveTank
import io.github.solcott.activitytracker.lexicon.app.GasSwitch
import io.github.solcott.activitytracker.lexicon.app.GearChange
import io.github.solcott.activitytracker.lexicon.app.Lap
import io.github.solcott.activitytracker.lexicon.app.Sport
import io.github.solcott.activitytracker.lexicon.app.SubSport
import io.github.solcott.activitytracker.lexicon.app.TrainingLoad
import io.github.solcott.activitytracker.lexicon.app.WaterType
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

    /**
     * [dive] as a decompression dive: air, then 50% nitrox at 60 s and oxygen at 100 s (a gas the record doesn't list, as from a merged
     * file), two tanks with transmitters, deco state on every record, in salt water.
     */
    val decoDive =
        dive.copy(
            subSport = SubSport.MULTI_GAS,
            points =
                dive.points.mapIndexed { i, point ->
                    point.copy(
                        ppO2Bar = 0.2 + i * 0.1,
                        cnsPercent = 5 + i,
                        n2LoadPercent = 10 + i * 4,
                        ndlSeconds = if (i < 6) 600L - i * 100 else 0L,
                        nextStopDepthMeters = if (i in 6..11) 6.0 else null,
                        nextStopSeconds = if (i in 6..11) 60L else null,
                        ttsSeconds = 300L - i * 20,
                        tankPressuresBar = listOf(200.0 - i * 5, if (i < 6) null else 210.0 - (i - 6) * 10),
                    )
                },
            dive =
                dive.dive!!.copy(
                    closedCircuit = false,
                    tanks =
                        listOf(
                            ActivityDiveTank(startPressure = 200_000, endPressure = 140_000),
                            ActivityDiveTank(startPressure = 210_000, endPressure = 150_000),
                        ),
                    waterType = WaterType.SALT,
                    waterDensity = 1025,
                    startN2 = 10,
                    endN2 = 58,
                    descentTime = 20,
                    avgAscentRate = 150,
                    maxDescentRate = 400,
                ),
            gasSwitches = listOf(GasSwitch(0, 21, 0), GasSwitch(60, 50, 0), GasSwitch(100, 100, 0)),
        )

    /** [ride] as two laps of 5 s, with the record's power, calories and shifts and the zones record's training load. */
    val intervalRide =
        ride.copy(
            laps =
                listOf(
                    Lap(
                        start = 0,
                        elapsed = 5,
                        moving = 4,
                        distance = 44,
                        avgSpeed = 11_000,
                        avgHeartRate = 122,
                        avgPower = 220,
                        ascent = 4,
                    ),
                    Lap(start = 5, elapsed = 5, distance = 55, maxSpeed = 12_500, maxHeartRate = 129, avgCadence = 92, descent = 1),
                ),
            totals =
                ExportTotals(
                    elevationLossMeters = 3,
                    avgCadence = 89,
                    maxCadence = 94,
                    avgPower = 245,
                    maxPower = 290,
                    normalizedPower = 250,
                    work = 2,
                    calories = 300,
                    frontShifts = 1,
                    rearShifts = 1,
                    jumps = 4,
                ),
            trainingLoad =
                TrainingLoad(
                    ftp = 250,
                    intensityFactor = 84,
                    trainingStressScore = 1124,
                    aerobicTrainingEffect = 34,
                    anaerobicTrainingEffect = 12,
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
