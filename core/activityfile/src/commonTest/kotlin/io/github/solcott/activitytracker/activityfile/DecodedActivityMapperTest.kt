package io.github.solcott.activitytracker.activityfile

import io.github.solcott.activitytracker.lexicon.app.ActivityType
import io.github.solcott.activitytracker.lexicon.app.DecoModel
import io.github.solcott.activitytracker.lexicon.app.DiveAlert
import io.github.solcott.activitytracker.lexicon.app.DiveAlertKind
import io.github.solcott.activitytracker.lexicon.app.GasSwitch
import io.github.solcott.activitytracker.lexicon.app.Lap
import io.github.solcott.activitytracker.lexicon.app.LapIntensity
import io.github.solcott.activitytracker.lexicon.app.LapTrigger
import io.github.solcott.activitytracker.lexicon.app.Length
import io.github.solcott.activitytracker.lexicon.app.Sport
import io.github.solcott.activitytracker.lexicon.app.SubSport
import io.github.solcott.activitytracker.lexicon.app.SwimStroke
import io.github.solcott.activitytracker.lexicon.app.WorkoutSet
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
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
    fun carriesTheDivesSettingsAndAlerts() {
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
                decoModel = DecoModel.ZHL16C,
                ppO2WarnBar = 1.4,
                ppO2CriticalBar = 1.6,
                ppO2DecoBar = 1.6,
                lowSetpointBar = 0.7,
                highSetpointBar = 1.3,
                safetyStopSeconds = 180,
            )
        val records = listOf(0L, 60L, 120L).map { record(it).copy(depthMeters = 10.0) }
        val activity =
            DecodedActivityMapper.map(
                DecodedActivity(
                    null,
                    listOf(summary("diving", elapsed = 120.0)),
                    emptyList(),
                    records,
                    header,
                    diveAlerts = listOf(DecodedDiveAlert((start + 60.seconds).toEpochMilliseconds(), DiveAlertKind.NDL_REACHED)),
                )
            )
        val dive = activity.dive!!
        assertEquals(DecoModel.ZHL16C, dive.decoModel)
        assertEquals(Triple(1.4, 1.6, 1.6), Triple(dive.ppO2WarnBar, dive.ppO2CriticalBar, dive.ppO2DecoBar))
        assertEquals(0.7 to 1.3, dive.lowSetpointBar to dive.highSetpointBar)
        assertEquals(180, dive.safetyStopSeconds)
        assertEquals(listOf(DiveAlert(60, DiveAlertKind.NDL_REACHED)), activity.streams?.diveAlerts)
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
        assertEquals(listOf(0, 300), tcx.streams?.laps?.map { it.start }, "laps that are the file's only summaries are kept too")
    }

    private fun at(sec: Long) = (start + sec.seconds).toEpochMilliseconds()

    @Test
    fun tcxIntervalLapsKeepTheirCaloriesAndIntensity() {
        val activity = DecodedActivityMapper.map(TcxParser.parse(TcxFixtures.intervalRun))
        val laps = activity.streams?.laps.orEmpty()
        assertEquals(TcxFixtures.INTERVAL_LAPS, laps.size)
        assertEquals(List(4) { listOf(LapIntensity.ACTIVE, LapIntensity.REST) }.flatten(), laps.map { it.intensity })
        assertEquals(List(4) { listOf(25, 5) }.flatten(), laps.map { it.calories })
        assertEquals(List(4) { listOf(LapTrigger.DISTANCE, LapTrigger.MANUAL) }.flatten(), laps.map { it.trigger })
        assertEquals(List(4) { listOf(400, 100) }.flatten(), laps.map { it.distance })
        assertEquals((0 until TcxFixtures.INTERVAL_LAPS).map { it * 60 }, laps.map { it.start })
    }

    @Test
    fun aOneLapTcxRideKeepsNoLapsAndItsTotals() {
        val activity = DecodedActivityMapper.map(TcxParser.parse(TcxFixtures.oneLapRide))
        assertNull(activity.streams?.laps)
        assertEquals(5000.0, activity.distanceMeters)
        assertEquals(600L, activity.movingSeconds)
        assertEquals(120, activity.calories)
    }

    @Test
    fun lapTriggersAndIntensitiesBecomeTheLexiconsTokens() {
        fun lap(sec: Long, trigger: String?, intensity: String? = null) =
            summary(elapsed = 300.0).copy(startTimeMillis = at(sec), lapTrigger = trigger, intensity = intensity)
        val laps =
            listOf(
                lap(0, "DISTANCE", "WARMUP"),
                lap(300, "distance", "active"),
                lap(600, "MANUAL", "COOLDOWN"),
                lap(900, "POSITION_LAP"),
                lap(1200, "positionMarked", "OTHER"),
                lap(1500, "Location", "Resting"),
                lap(1800, "FITNESS_EQUIPMENT", "NEW_KIND"),
            )
        val activity =
            DecodedActivityMapper.map(DecodedActivity(fileId(), listOf(summary(elapsed = 2100.0)), laps, listOf(record(0), record(2100))))
        val stored = activity.streams?.laps.orEmpty()
        assertEquals(
            listOf(
                LapTrigger.DISTANCE,
                LapTrigger.DISTANCE,
                LapTrigger.MANUAL,
                LapTrigger.POSITION,
                LapTrigger.POSITION,
                LapTrigger.POSITION,
                LapTrigger.FITNESS_EQUIPMENT,
            ),
            stored.map { it.trigger },
        )
        assertEquals(
            listOf(LapIntensity.WARMUP, LapIntensity.ACTIVE, LapIntensity.COOLDOWN, null, "other", LapIntensity.REST, "newKind"),
            stored.map { it.intensity },
            "unknown values are kept, in camelCase",
        )
    }

    private fun poolSwim(poolLength: Double?, subSport: String = "lap_swimming", lengths: List<DecodedLength> = poolLengths()) =
        DecodedActivity(
            fileId(),
            listOf(summary("swimming", subSport, elapsed = 260.0, distance = 200.0).copy(poolLengthMeters = poolLength)),
            emptyList(),
            listOf(record(0, lat = null, lon = null, hr = 120), record(260, lat = null, lon = null, hr = 130)),
            lengths = lengths,
        )

    /** Four 30 s freestyle lengths, a 20 s rest, four 30 s breaststroke lengths. */
    private fun poolLengths(): List<DecodedLength> {
        var t = 0L
        return (0 until 9).map { i ->
            val idle = i == 4
            val seconds = if (idle) 20L else 30L
            DecodedLength(
                    at(t),
                    seconds.toDouble(),
                    if (idle) null else if (i < 4) "FREESTYLE" else "breaststroke",
                    15,
                    if (idle) "IDLE" else "active",
                )
                .also { t += seconds }
        }
    }

    @Test
    fun aPoolSwimKeepsItsLengthsAndPoolLength() {
        val activity = DecodedActivityMapper.map(poolSwim(25.0))
        assertEquals(2500, activity.poolLengthCentimeters)
        val lengths = activity.streams?.lengths.orEmpty()
        assertEquals(9, lengths.size)
        assertEquals(Length(0, 30, SwimStroke.FREESTYLE, 15), lengths.first())
        assertEquals(Length(120, 20, rest = true), lengths[4], "an idle length is a rest, without a stroke")
        assertEquals(SwimStroke.BREASTSTROKE, lengths.last().stroke)
        assertEquals(lengths.sortedBy { it.start }, lengths)
    }

    @Test
    fun aYardPoolIsKeptInCentimeters() {
        assertEquals(2286, DecodedActivityMapper.map(poolSwim(22.86)).poolLengthCentimeters)
    }

    @Test
    fun anOpenWaterSwimHasNoPoolLength() {
        val activity = DecodedActivityMapper.map(poolSwim(null, subSport = "open_water", lengths = emptyList()))
        assertNull(activity.poolLengthCentimeters)
        assertNull(activity.streams?.lengths)
    }

    @Test
    fun aSwimWithoutLengthsStoresNone() {
        val activity = DecodedActivityMapper.map(poolSwim(25.0, lengths = emptyList()))
        assertEquals(2500, activity.poolLengthCentimeters)
        assertNull(activity.streams?.lengths)
    }

    @Test
    fun aStrengthWorkoutKeepsItsSets() {
        fun set(
            sec: Long,
            seconds: Double,
            type: String,
            category: String? = null,
            exercise: String? = null,
            reps: Int? = null,
            kg: Double? = null,
        ) = DecodedSet(at(sec), at(sec) + (seconds * 1000).toLong(), seconds, type, category, exercise, reps, kg)
        val sets =
            listOf(
                set(0, 40.0, "ACTIVE", "BENCH_PRESS", "BARBELL_BENCH_PRESS", 8, 60.0),
                set(40, 90.0, "REST"),
                set(130, 40.0, "active", "benchPress", "barbellBenchPress", 8, 60.0),
                set(170, 30.0, "ACTIVE", "PUSH_UP", "PUSH_UP", 20),
                set(200, 30.0, "ACTIVE", "ROW", "SINGLE_ARM_CABLE_ROW", 12, 20.0),
                set(230, 30.0, "ACTIVE", "SQUAT"),
                set(260, 30.0, "ACTIVE", "UNKNOWN"),
                // A set with only its end and duration.
                DecodedSet(null, at(320), 30.0, "ACTIVE", "plank", null, null, null),
            )
        val workout =
            DecodedActivity(
                fileId(),
                listOf(summary("training", "strength_training", elapsed = 320.0, distance = null)),
                emptyList(),
                emptyList(),
                sets = sets,
            )
        val stored = DecodedActivityMapper.map(workout).streams?.sets.orEmpty()
        assertEquals(WorkoutSet(0, 40, category = "bench_press", exercise = "barbell_bench_press", reps = 8, weight = 60_000), stored[0])
        assertEquals(WorkoutSet(40, 90, rest = true), stored[1])
        assertEquals(stored[0].copy(start = 130), stored[2], "every SDK's spelling gives the same tokens")
        assertEquals(WorkoutSet(170, 30, category = "push_up", exercise = "push_up", reps = 20), stored[3], "bodyweight: no weight")
        assertEquals("single_arm_cable_row", stored[4].exercise, "an exercise the app has no label for is kept")
        assertEquals(WorkoutSet(230, 30, category = "squat"), stored[5], "a category without a name")
        assertEquals(WorkoutSet(260, 30), stored[6], "FIT's unknown category is no category")
        assertEquals(WorkoutSet(290, 30, category = "plank"), stored[7])
    }

    @Test
    fun aWorkoutWithoutSetsStoresNone() {
        val cardio = DecodedActivity(fileId(), listOf(summary("training", "cardio_training")), emptyList(), listOf(record(0), record(60)))
        assertNull(DecodedActivityMapper.map(cardio).streams?.sets)
    }

    /** A FIT triathlon: swim, transition, ride, transition, run, the run with five laps. */
    private fun triathlon(): DecodedActivity {
        val legs =
            listOf(
                Triple("SWIMMING", 0L, 1800.0) to 1500.0,
                Triple("TRANSITION", 1800L, 120.0) to 0.0,
                Triple("CYCLING", 1920L, 3600.0) to 40_000.0,
                Triple("TRANSITION", 5520L, 90.0) to 0.0,
                Triple("RUNNING", 5610L, 2700.0) to 10_000.0,
            )
        val sessions = legs.map { (leg, meters) ->
            summary(leg.first, elapsed = leg.third, timer = leg.third, distance = meters).copy(startTimeMillis = at(leg.second))
        }
        val laps =
            sessions.take(4) +
                (0 until 5).map { i ->
                    summary("RUNNING", elapsed = 540.0, timer = 540.0, distance = 2000.0).copy(startTimeMillis = at(5610L + i * 540))
                }
        return DecodedActivity(fileId(), sessions, laps, listOf(record(0), record(8310)))
    }

    @Test
    fun aTriathlonKeepsItsLegsAndTheRunsLaps() {
        val activity = DecodedActivityMapper.map(triathlon())
        assertEquals(ActivityType(Sport.MULTISPORT, SubSport.TRIATHLON), activity.type)
        val legs = activity.streams?.legs.orEmpty()
        assertEquals(listOf(Sport.SWIM, Sport.MULTISPORT, Sport.RIDE, Sport.MULTISPORT, Sport.RUN), legs.map { it.sport })
        assertEquals(listOf(null, true, null, true, null), legs.map { it.transition })
        assertEquals(listOf(1800, 120, 3600, 90, 2700), legs.map { it.elapsed })
        assertEquals(listOf(1500, 0, 40_000, 0, 10_000), legs.map { it.distance })
        val run = legs.last()
        val runLaps = activity.streams?.laps.orEmpty().filter { it.start >= run.start }
        assertEquals(5, runLaps.size)
        assertTrue(runLaps.all { it.start + it.elapsed <= run.start + run.elapsed }, "each lap within its leg")
    }

    @Test
    fun aTcxDuathlonKeepsItsLegs() {
        val activity = DecodedActivityMapper.map(TcxParser.parse(TcxFixtures.duathlon))
        assertEquals(ActivityType(Sport.MULTISPORT, SubSport.DUATHLON), activity.type)
        val legs = activity.streams?.legs.orEmpty()
        assertEquals(listOf(Sport.RUN, Sport.MULTISPORT, Sport.RIDE, Sport.MULTISPORT, Sport.RUN), legs.map { it.sport })
        assertEquals(TcxFixtures.DUATHLON_LEGS.map { it.start }, legs.map { it.start })
        assertEquals(TcxFixtures.DUATHLON_POINTS, activity.streams?.time?.size, "the streams span every leg")
        assertEquals(20_000.0, activity.distanceMeters)
    }

    @Test
    fun aSingleSportFileHasNoLegs() {
        val run = DecodedActivityMapper.map(DecodedActivity(fileId(), listOf(summary()), emptyList(), listOf(record(0), record(1800))))
        assertNull(run.streams?.legs)
    }

    private fun skiDay(sport: String, subSport: String?, splits: List<DecodedSplit>): DecodedActivity {
        val laps = (0 until 3).map { summary(sport, subSport, elapsed = 1440.0).copy(startTimeMillis = at(it * 1440L)) }
        return DecodedActivity(
            fileId(),
            listOf(summary(sport, subSport, elapsed = 4320.0)),
            laps,
            listOf(record(0), record(4320)),
            splits = splits,
        )
    }

    @Test
    fun aResortDaysRunsAndLiftsReplaceItsLaps() {
        val splits =
            (0 until 8).flatMap { i ->
                listOf(
                    DecodedSplit("SKI_LIFT_SPLIT", at(i * 540L), 300.0, 300.0, 800.0, 300.0, null, null, null, null, null, null),
                    DecodedSplit("skiRunSplit", at(i * 540L + 300), 240.0, 240.0, 1500.0, null, 300.0, null, 18.0, 150, 170, 40),
                )
            }
        val activity =
            DecodedActivityMapper.map(
                skiDay(
                    "ALPINE_SKIING",
                    null,
                    splits + DecodedSplit("CLIMB_ACTIVE", at(0), 60.0, null, null, null, null, null, null, null, null, null),
                )
            )
        val laps = activity.streams?.laps.orEmpty()
        assertEquals(16, laps.size, "other split types are left out")
        assertEquals(List(8) { listOf(LapIntensity.REST, LapIntensity.ACTIVE) }.flatten(), laps.map { it.intensity })
        assertEquals(
            Lap(
                300,
                240,
                moving = 240,
                distance = 1500,
                maxSpeed = 18_000,
                avgHeartRate = 150,
                maxHeartRate = 170,
                descent = 300,
                calories = 40,
                intensity = LapIntensity.ACTIVE,
            ),
            laps[1],
        )
        assertEquals(300, laps[0].ascent)
    }

    @Test
    fun aBackcountrySkiWithoutRunsKeepsItsLaps() {
        val activity = DecodedActivityMapper.map(skiDay("ALPINE_SKIING", "BACKCOUNTRY", emptyList()))
        assertEquals(listOf(0, 1440, 2880), activity.streams?.laps?.map { it.start })
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
