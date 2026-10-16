package io.github.solcott.activitytracker.activityfile

import io.github.solcott.activitytracker.lexicon.app.ActivityType
import io.github.solcott.activitytracker.lexicon.app.GasSwitch
import io.github.solcott.activitytracker.lexicon.app.Sport
import io.github.solcott.activitytracker.lexicon.app.SubSport
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

class DecodedActivityMapperTest {
    private val start = Instant.parse("2026-09-20T07:00:00Z")

    @Suppress("LongParameterList")
    private fun summary(
        sport: String? = "running",
        subSport: String? = null,
        elapsed: Double? = 1800.0,
        timer: Double? = 1700.0,
        distance: Double? = 5000.0,
        ascent: Double? = 42.0,
        avgHr: Int? = 150,
        maxHr: Int? = 175,
        maxSpeed: Double? = 4.2,
        temperature: Double? = 12.0,
    ) =
        DecodedSummary(
            sport,
            subSport,
            start.toEpochMilliseconds(),
            elapsed,
            timer,
            distance,
            ascent,
            null,
            maxSpeed,
            avgHr,
            maxHr,
            null,
            null,
            temperature,
        )

    @Suppress("LongParameterList")
    private fun record(
        sec: Long,
        lat: Double? = 47.6 + sec * 1e-4,
        lon: Double? = -122.3,
        alt: Double? = null,
        hr: Int? = null,
        distance: Double? = null,
    ) = DecodedSample((start + sec.seconds).toEpochMilliseconds(), lat, lon, alt, hr, null, null, null, distance, null)

    private fun fileId(type: String? = "activity") = DecodedSource(type, "garmin", "fenix7", start.toEpochMilliseconds())

    @Test
    fun sessionTotalsWin() {
        val activity = DecodedActivityMapper.map(DecodedActivity(fileId(), listOf(summary()), emptyList(), listOf(record(0), record(10))))

        assertEquals(ActivityType(Sport.RUN), activity.type)
        assertEquals(start, activity.startedAt)
        assertEquals(30.minutes, activity.elapsed)
        assertEquals(1700L, activity.movingSeconds)
        assertEquals(5000.0, activity.distanceMeters)
        assertEquals(42.0, activity.elevationGainMeters)
        assertEquals(150, activity.avgHeartRate)
        assertEquals(175, activity.maxHeartRate)
        assertEquals(4.2, activity.maxSpeedMps)
        assertEquals(12.0, activity.avgTemperatureCelsius)
        assertEquals("Garmin Fenix 7", activity.device)
        assertEquals(2, activity.samples.size)
    }

    @Test
    fun withoutASessionTotalsComeFromRecords() {
        val records =
            listOf(
                record(0, alt = 100.0, hr = 120, distance = 0.0),
                record(60, alt = 101.0, hr = 140, distance = 200.0), // inside the hysteresis: no climb yet
                record(120, alt = 110.0, hr = 160, distance = 400.0),
            )
        val activity = DecodedActivityMapper.map(DecodedActivity(fileId(), emptyList(), emptyList(), records.reversed()))

        assertEquals(ActivityType(Sport.OTHER), activity.type)
        assertEquals(start, activity.startedAt)
        assertEquals(2.minutes, activity.elapsed)
        assertEquals(120L, activity.movingSeconds, "computed from the samples, which keep moving at about 11 m/s")
        assertEquals(400.0, activity.distanceMeters)
        assertEquals(10.0, activity.elevationGainMeters)
        assertEquals(140, activity.avgHeartRate)
        assertEquals(160, activity.maxHeartRate)
        assertEquals(start, activity.samples.first().time, "samples are in time order")
    }

    @Test
    fun indoorActivityHasNoSamples() {
        val activity =
            DecodedActivityMapper.map(
                DecodedActivity(
                    fileId(),
                    listOf(summary(sport = "training", subSport = "indoor_cycling")),
                    emptyList(),
                    listOf(record(0, lat = null, lon = null)),
                )
            )
        assertEquals(ActivityType(Sport.RIDE, SubSport.INDOOR), activity.type)
        assertTrue(activity.samples.isEmpty())
    }

    @Test
    fun multipleSessionsAddUp() {
        val activity =
            DecodedActivityMapper.map(
                DecodedActivity(
                    fileId(),
                    listOf(summary(avgHr = 140, timer = 1000.0), summary(sport = "cycling", avgHr = 160, timer = 3000.0)),
                    emptyList(),
                    emptyList(),
                )
            )
        assertEquals(ActivityType(Sport.MULTISPORT, SubSport.DUATHLON), activity.type, "a run and a ride")
        assertEquals(10_000.0, activity.distanceMeters)
        assertEquals(4000L, activity.movingSeconds)
        assertEquals(155, activity.avgHeartRate)
    }

    @Test
    fun triathlonSkipsTransitions() {
        val legs = listOf("swimming", "transition", "cycling", "transition", "running").map { summary(sport = it) }
        val activity = DecodedActivityMapper.map(DecodedActivity(fileId(), legs, emptyList(), emptyList()))
        assertEquals(ActivityType(Sport.MULTISPORT, SubSport.TRIATHLON), activity.type)
    }

    @Test
    fun sessionsOfOneSportStayThatSport() {
        val laps = listOf(summary(sport = "cycling", subSport = "mountain"), summary(sport = "cycling", subSport = "mountain"))
        val activity = DecodedActivityMapper.map(DecodedActivity(fileId(), laps, emptyList(), emptyList()))
        assertEquals(ActivityType(Sport.RIDE, SubSport.MOUNTAIN), activity.type)
    }

    @Test
    fun mapsSportNamesInEverySdksSpelling() {
        fun assertType(expected: ActivityType, sport: String?, subSport: String? = null) =
            assertEquals(expected, DecodedActivityMapper.activityType(sport, subSport), "$sport / $subSport")

        assertType(ActivityType(Sport.RIDE, SubSport.E_BIKE), "E_BIKING")
        assertType(ActivityType(Sport.RIDE, SubSport.E_BIKE), "eBiking")
        assertType(ActivityType(Sport.RIDE, SubSport.E_MOUNTAIN), "E_BIKING", "E_BIKE_MOUNTAIN")
        assertType(ActivityType(Sport.XC_SKI), "CROSS_COUNTRY_SKIING")
        assertType(ActivityType(Sport.XC_SKI, SubSport.SKATE), "crossCountrySkiing", "skateSkiing")
        assertType(ActivityType(Sport.HIKE), "hiking")
        assertType(ActivityType(Sport.SWIM, SubSport.POOL), "SWIMMING", "LAP_SWIMMING")
        assertType(ActivityType(Sport.SWIM, SubSport.OPEN_WATER), "swimming", "openWater")
        assertType(ActivityType(Sport.ROW), "rowing")
        assertType(ActivityType(Sport.ROW, SubSport.INDOOR), "fitness_equipment", "indoor_rowing")
        assertType(ActivityType(Sport.WHEELCHAIR, SubSport.RUN_PACE), "WHEELCHAIR_PUSH_RUN")
        assertType(ActivityType(Sport.WHEELCHAIR, SubSport.WALK_PACE), "wheelchairPushWalk")
        assertType(ActivityType(Sport.FLYING, SubSport.PARAGLIDING), "paragliding")
        assertType(ActivityType(Sport.SOCCER, SubSport.FUTSAL), "futsal")
        assertType(ActivityType(Sport.AUSTRALIAN_FOOTBALL), "FOOTBALL_AUSTRALIAN")
        assertType(ActivityType(Sport.MOBILITY, SubSport.STRETCHING), "training", "stretching")
        assertType(ActivityType(Sport.RECOVERY, SubSport.COLD_PLUNGE), "coldPlunge")
        assertType(ActivityType(Sport.OTHER), null)
        assertType(ActivityType(Sport.OTHER), "training")
        assertType(ActivityType(Sport.OTHER), "some_future_sport")
    }

    @Test
    fun subSportsRefineTheirSport() {
        fun assertType(expected: ActivityType, sport: String?, subSport: String?) =
            assertEquals(expected, DecodedActivityMapper.activityType(sport, subSport), "$sport / $subSport")

        assertType(ActivityType(Sport.RIDE, SubSport.MOUNTAIN), "CYCLING", "MOUNTAIN")
        assertType(ActivityType(Sport.RIDE, SubSport.GRAVEL), "cycling", "gravelCycling")
        assertType(ActivityType(Sport.RIDE, SubSport.INDOOR), "cycling", "indoor_cycling")
        assertType(ActivityType(Sport.RIDE, SubSport.VIRTUAL), "cycling", "virtual_activity")
        assertType(ActivityType(Sport.RIDE), "cycling", "generic")
        assertType(ActivityType(Sport.HANDCYCLE, SubSport.INDOOR), "cycling", "indoor_hand_cycling")
        assertType(ActivityType(Sport.RUN, SubSport.TRAIL), "running", "trail")
        assertType(ActivityType(Sport.RUN, SubSport.VIRTUAL), "running", "virtual_activity")
        assertType(ActivityType(Sport.RUN, SubSport.TREADMILL), "training", "treadmill")
        assertType(ActivityType(Sport.WALK, SubSport.TREADMILL), "walking", "treadmill")
        assertType(ActivityType(Sport.WALK, SubSport.NORDIC), "walking", "nordic_walking")
        assertType(ActivityType(Sport.XC_SKI, SubSport.ROLLER), "cross_country_skiing", "roller_skiing")
        assertType(ActivityType(Sport.ROW, SubSport.VIRTUAL), "rowing", "virtual_activity")
        assertType(ActivityType(Sport.STRENGTH), "training", "strength_training")
        assertType(ActivityType(Sport.YOGA), "training", "yoga")
        assertType(ActivityType(Sport.STAIR_STEPPER), "fitness_equipment", "stair_climbing")
        assertType(ActivityType(Sport.CLIMB, SubSport.BOULDERING), "rock_climbing", "bouldering")
        assertType(ActivityType(Sport.SKI, SubSport.BACKCOUNTRY), "alpine_skiing", "backcountry")
        assertType(ActivityType(Sport.DIVE, SubSport.APNEA), "diving", "apnea_diving")
        assertType(ActivityType(Sport.FIELD_HOCKEY), "hockey", "field_hockey")
        assertType(ActivityType(Sport.PICKLEBALL), "racket", "pickleball")
        assertType(ActivityType(Sport.MOTOCROSS), "motorcycling", "motocross")
        assertType(ActivityType(Sport.RIDE), "cycling", "treadmill") // a sub-sport that doesn't fit is ignored
    }

    @Test
    fun mapsGpxAndStravaTypeNames() {
        assertEquals(ActivityType(Sport.RIDE, SubSport.MOUNTAIN), DecodedActivityMapper.activityType("MountainBikeRide"))
        assertEquals(ActivityType(Sport.RIDE, SubSport.GRAVEL), DecodedActivityMapper.activityType("GravelRide"))
        assertEquals(ActivityType(Sport.RIDE, SubSport.VIRTUAL), DecodedActivityMapper.activityType("VirtualRide"))
        assertEquals(ActivityType(Sport.RUN, SubSport.TRAIL), DecodedActivityMapper.activityType("TrailRun"))
        assertEquals(ActivityType(Sport.STRENGTH), DecodedActivityMapper.activityType("WeightTraining"))
        assertEquals(ActivityType(Sport.SUP), DecodedActivityMapper.activityType("StandUpPaddling"))
        assertEquals(ActivityType(Sport.CANOE), DecodedActivityMapper.activityType("Canoeing"))
        assertEquals(ActivityType(Sport.STRENGTH, SubSport.CROSSFIT), DecodedActivityMapper.activityType("Crossfit"))
        assertEquals(ActivityType(Sport.RIDE, SubSport.VELOMOBILE), DecodedActivityMapper.activityType("Velomobile"))
        assertEquals(ActivityType(Sport.RIDE), DecodedActivityMapper.activityType("Biking"), "TCX")
    }

    @Test
    fun rejectsOtherFileTypes() {
        assertFailsWith<ActivityFileException> {
            DecodedActivityMapper.map(DecodedActivity(fileId(type = "COURSE"), listOf(summary()), emptyList(), emptyList()))
        }
    }

    @Test
    fun rejectsFilesWithNoActivityData() {
        assertFailsWith<ActivityFileException> {
            DecodedActivityMapper.map(DecodedActivity(fileId(), emptyList(), emptyList(), emptyList()))
        }
    }

    @Test
    fun diveDepthsComeFromTheProfile() {
        fun depth(sec: Long, meters: Double) =
            DecodedSample((start + sec.seconds).toEpochMilliseconds(), null, null, null, null, null, null, null, null, 20.0, meters)
        // A minute at the surface either side of the dive must not pull the average toward zero.
        val records = listOf(depth(0, 0.0), depth(60, 0.0), depth(120, 10.0), depth(180, 10.0), depth(240, 0.0), depth(300, 0.0))
        val header = DecodedDive(null, null, null, null, null, null, null, listOf(DiveGas(21, 0)), closedCircuit = false)
        val dive = DecodedActivityMapper.map(DecodedActivity(null, listOf(summary("diving")), emptyList(), records, header)).dive!!

        assertEquals(10.0, dive.maxDepthMeters, "the deepest sample, with no header value")
        assertEquals(20.0 / 3, dive.avgDepthMeters!!, 1e-9)
        assertEquals(listOf(0L, 60L, 120L, 180L, 240L, 300L), dive.profile.map { it.seconds })
    }

    @Test
    fun aDiveComputersOwnFiguresWin() {
        val records = listOf(record(0).copy(depthMeters = 0.0), record(1).copy(depthMeters = 10.0), record(2).copy(depthMeters = 0.0))
        val header = DecodedDive(18.0, null, null, null, null, null, null, listOf(DiveGas(21, 0)), false, avgDepthMeters = 12.0)
        val activity =
            DecodedActivityMapper.map(
                DecodedActivity(null, listOf(summary("DIVING", "MULTI_GAS_DIVING", distance = 0.0)), emptyList(), records, header)
            )

        val dive = activity.dive!!
        assertEquals(12.0, dive.avgDepthMeters, "the computer's average over the profile's")
        assertEquals(18.0, dive.maxDepthMeters)
        assertEquals(null, activity.distanceMeters, "a dive computer's 0 m is no distance")
        assertEquals(ActivityType(Sport.DIVE, SubSport.SINGLE_GAS), activity.type, "one gas breathed, whatever the watch's dive mode")
    }

    @Test
    fun carriesTheDivesSummaryTanksSwitchesAndNotes() {
        val records = listOf(record(0).copy(depthMeters = 0.0), record(60).copy(depthMeters = 10.0), record(120).copy(depthMeters = 0.0))
        val tank = DiveTank(200.0, 60.0, null, volumeLiters = 12.0, gas = DiveGas(32, 0), sensor = "t")
        val header =
            DecodedDive(
                18.0,
                null,
                null,
                null,
                null,
                null,
                null,
                listOf(DiveGas(32, 0)),
                false,
                tanks = listOf(tank),
                startN2 = 10,
                endN2 = 55,
                maxAscentRateMps = 0.3,
                descentTimeSeconds = 300.0,
                waterType = "salt",
                waterDensityKgPerM3 = 1025.0,
                airTemperatureCelsius = 30.0,
                visibilityMeters = 15.0,
                altitudeMeters = 450.0,
                noFlyTimeSeconds = 64_800.0,
                desaturationTimeSeconds = 72_000.0,
                highestPpO2Bar = 1.4,
            )
        val activity =
            DecodedActivityMapper.map(
                DecodedActivity(
                    null,
                    listOf(summary("diving", elapsed = 120.0)),
                    emptyList(),
                    records,
                    header,
                    gasSwitches = listOf(DecodedGasSwitch(start.toEpochMilliseconds(), DiveGas(32, 0))),
                    tankUpdates = listOf(DecodedTankUpdate((start + 60.seconds).toEpochMilliseconds(), "t", 150.0)),
                    notes = "  Turtle at the wall\n",
                )
            )
        val dive = activity.dive!!
        assertEquals(listOf(tank), dive.tanks)
        assertEquals(10 to 55, dive.startN2 to dive.endN2)
        assertEquals(0.3, dive.maxAscentRateMps)
        assertEquals(300.0, dive.descentTimeSeconds)
        assertEquals("salt" to 1025.0, dive.waterType to dive.waterDensityKgPerM3)
        assertEquals(30.0, dive.airTemperatureCelsius)
        assertEquals(15.0, dive.visibilityMeters)
        assertEquals(450.0, dive.altitudeMeters)
        assertEquals(64_800.0 to 72_000.0, dive.noFlyTimeSeconds to dive.desaturationTimeSeconds)
        assertEquals(1.4, dive.highestPpO2Bar)
        assertEquals("Turtle at the wall", activity.description)
        assertEquals(listOf(GasSwitch(0, 32, 0)), activity.streams?.gasSwitches)
        assertEquals(listOf(null, 150_000, null), activity.streams?.tankPressures?.single()?.pressure)
    }

    @Test
    fun carriesCaloriesTrainingEffectAndRealLaps() {
        val session = summary(elapsed = 600.0).copy(totalCalories = 450, aerobicTrainingEffect = 3.4, anaerobicTrainingEffect = 0.0)
        fun lap(startSec: Long) = summary(elapsed = 300.0).copy(startTimeMillis = (start + startSec.seconds).toEpochMilliseconds())
        val records = listOf(record(0, hr = 120), record(600, hr = 140))
        val activity = DecodedActivityMapper.map(DecodedActivity(fileId(), listOf(session), listOf(lap(0), lap(300)), records))
        assertEquals(450, activity.calories)
        assertEquals(3.4, activity.aerobicTrainingEffect)
        assertEquals(null, activity.anaerobicTrainingEffect, "0 is unset")
        assertEquals(listOf(0, 300), activity.streams?.laps?.map { it.start })
        assertEquals(null, activity.description)

        val oneLap = DecodedActivityMapper.map(DecodedActivity(fileId(), listOf(session), listOf(summary(elapsed = 600.0)), records))
        assertEquals(null, oneLap.streams?.laps, "one lap spanning the activity isn't kept")
        val tcx = DecodedActivityMapper.map(DecodedActivity(fileId(), emptyList(), listOf(lap(0), lap(300)), records))
        assertEquals(null, tcx.streams?.laps, "a file whose laps are its only summaries keeps none")
    }

    @Test
    fun onlyDivesHaveDiveData() {
        assertEquals(null, DecodedActivityMapper.map(DecodedActivity(fileId(), listOf(summary()), emptyList(), listOf(record(0)))).dive)
    }

    @Test
    fun semicircles() {
        assertEquals(180.0, FitUnits.semicirclesToDegrees(Int.MAX_VALUE), 1e-6)
        assertEquals(-90.0, FitUnits.semicirclesToDegrees(Int.MIN_VALUE / 2), 1e-9)
    }
}
