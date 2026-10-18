package io.github.solcott.activitytracker.activityfile

import io.github.solcott.activitytracker.lexicon.app.ActivityDive
import io.github.solcott.activitytracker.lexicon.app.ActivityDiveGas
import io.github.solcott.activitytracker.lexicon.app.ActivityDiveTank
import io.github.solcott.activitytracker.lexicon.app.ActivityStreamData
import io.github.solcott.activitytracker.lexicon.app.DiveAlert
import io.github.solcott.activitytracker.lexicon.app.DiveAlertKind
import io.github.solcott.activitytracker.lexicon.app.GasSwitch
import io.github.solcott.activitytracker.lexicon.app.Sport
import io.github.solcott.activitytracker.lexicon.app.SubSport
import io.github.solcott.activitytracker.lexicon.app.TankPressure
import io.github.solcott.activitytracker.location.LocationSample
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlinx.datetime.FixedOffsetTimeZone
import kotlinx.datetime.UtcOffset

/** Dives to export as UDDF, built as `ActivityExporter` builds them: stored streams and track joined by [ExportPoints.join]. */
object UddfExportFixtures {
    val start: Instant = Instant.parse("2026-09-20T15:10:00Z")
    /** UTC−6, where the dive starts at 09:10. */
    val zone = FixedOffsetTimeZone(UtcOffset(hours = -6))

    private const val ELAPSED = 2880
    private const val STEP = 60
    private const val SWITCH = 1920
    private val seconds = (0..ELAPSED step STEP).toList()

    /** 48 minutes to 31.2 m on air, then 50% nitrox from 1920 s through a 6 m deco stop, with two tanks on transmitters. */
    val streams =
        ActivityStreamData(
            time = seconds,
            depth = seconds.map { s -> if (s == 0 || s == ELAPSED) 0 else if (s < SWITCH) 3120 else 600 },
            temperature = seconds.map { s -> if (s < SWITCH) 125 else 180 },
            heartRate = seconds.map { s -> 70 + s / STEP },
            ppO2 = seconds.map { s -> if (s < SWITCH) 860 else 800 },
            cns = seconds.map { s -> 2 + s / (STEP * 4) },
            ndl = seconds.map { s -> if (s < SWITCH - 2 * STEP) 600 - s / 10 else 0 },
            decoStopDepth = seconds.map { s -> if (s in (SWITCH - 2 * STEP)..(ELAPSED - STEP)) 600 else null },
            decoStopTime = seconds.map { s -> if (s in (SWITCH - 2 * STEP)..(ELAPSED - STEP)) 180 else null },
            gasSwitches = listOf(GasSwitch(0, 21, 0), GasSwitch(SWITCH, 50, 0)),
            // UDDF names all but the battery; the last falls between waypoints.
            diveAlerts =
                listOf(
                    DiveAlert(600, DiveAlertKind.MICROBUBBLES),
                    DiveAlert(1200, DiveAlertKind.BATTERY_LOW),
                    DiveAlert(2400, DiveAlertKind.ASCENT_CRITICAL),
                    DiveAlert(2410, DiveAlertKind.NEAR_SURFACE),
                ),
            tankPressures =
                listOf(
                    TankPressure(seconds.map { s -> 200_000 - s * 40 }),
                    TankPressure(seconds.map { s -> if (s < SWITCH) null else 210_000 - (s - SWITCH) * 50 }),
                ),
        )

    /** The entry and exit, kept as a two-point track. */
    val track =
        listOf(
            LocationSample(17.3155, -87.5347, null, null, null, start),
            LocationSample(17.3161, -87.5352, null, null, null, start + ELAPSED.seconds),
        )

    val record =
        ActivityDive(
            maxDepth = 3120,
            avgDepth = 2210,
            minTemperature = 125,
            surfaceInterval = 5400,
            diveNumber = 212,
            gases = listOf(ActivityDiveGas(21, 0), ActivityDiveGas(50, 0)),
            tanks =
                listOf(
                    ActivityDiveTank(startPressure = 200_000, endPressure = 84_800, volume = 11_100, gas = ActivityDiveGas(21, 0)),
                    ActivityDiveTank(startPressure = 210_000, endPressure = 162_000, volume = 5_700, gas = ActivityDiveGas(50, 0)),
                ),
            site = "Blue Hole",
            airTemperature = 291,
            visibility = 2500,
            altitude = 0,
            noFlyTime = 72_000,
            desaturationTime = 50_400,
            highestPpO2 = 1610,
        )

    val decoDive =
        ExportActivity(
            name = "Blue Hole",
            sport = Sport.DIVE,
            subSport = SubSport.MULTI_GAS,
            tcxSport = TcxSport.Other,
            startedAt = start,
            elapsed = ELAPSED.seconds,
            distanceMeters = null,
            avgHeartRate = null,
            maxHeartRate = null,
            isDive = true,
            points = ExportPoints.join(start, track, streams),
            dive = record,
            gasSwitches = streams.gasSwitches.orEmpty(),
            diveAlerts = streams.diveAlerts.orEmpty(),
            device = "Shearwater Perdix 2",
        )

    /** [decoDive] as imported before switches, deco state and tank series were kept: depth and temperature only, no track. */
    val olderDive =
        decoDive.copy(
            points =
                ExportPoints.join(
                    start,
                    null,
                    ActivityStreamData(time = streams.time, depth = streams.depth, temperature = streams.temperature),
                ),
            dive = record.copy(tanks = null, site = null),
            gasSwitches = emptyList(),
            diveAlerts = emptyList(),
            device = null,
        )

    fun write(activity: ExportActivity): String = UddfWriter.write(activity, zone, createdAt = start)
}
