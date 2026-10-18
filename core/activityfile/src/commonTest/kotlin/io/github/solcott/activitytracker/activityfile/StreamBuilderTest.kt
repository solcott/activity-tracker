package io.github.solcott.activitytracker.activityfile

import io.github.solcott.activitytracker.lexicon.app.GasSwitch
import io.github.solcott.activitytracker.lexicon.app.GearChange
import io.github.solcott.activitytracker.lexicon.app.Lap
import io.github.solcott.activitytracker.lexicon.app.Length
import io.github.solcott.activitytracker.lexicon.app.Sport
import io.github.solcott.activitytracker.lexicon.app.StreamChannel
import io.github.solcott.activitytracker.lexicon.app.SwimStroke
import io.github.solcott.activitytracker.lexicon.app.WorkoutSet
import io.github.solcott.activitytracker.location.LocationSample
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

class StreamBuilderTest {
    private val start = Instant.parse("2026-09-20T07:00:00Z")

    @Suppress("LongParameterList")
    private fun sample(
        sec: Long,
        lat: Double? = null,
        hr: Int? = null,
        power: Int? = null,
        cadence: Int? = null,
        speed: Double? = null,
        distance: Double? = null,
        alt: Double? = null,
    ) = DecodedSample((start + sec.seconds).toEpochMilliseconds(), lat, lat?.let { -122.3 }, alt, hr, cadence, power, speed, distance, null)

    @Test
    fun keepsIndoorChannelsInLexiconUnits() {
        val records =
            listOf(sample(0, hr = 120, power = 200, cadence = 90, speed = 8.5, distance = 0.0), sample(2, hr = 125, distance = 17.0))
        val data = assertNotNull(StreamBuilder.fromRecords(records, start.toEpochMilliseconds(), emptyList()))
        assertEquals(listOf(0, 2), data.time)
        assertEquals(listOf(120, 125), data.heartRate)
        assertEquals(listOf(200, null), data.power)
        assertEquals(listOf(8500, null), data.speed)
        assertEquals(listOf(0, 1700), data.distance)
        assertNull(data.altitude)
        assertEquals(
            listOf(StreamChannel.DISTANCE, StreamChannel.SPEED, StreamChannel.HEART_RATE, StreamChannel.POWER, StreamChannel.CADENCE),
            data.channels,
        )
    }

    @Test
    fun derivesDistanceAndSpeedFromPositionsWhenTheFileLacksThem() {
        // 1e-4 degrees of latitude is about 11.1 m.
        val records = (0L..4L).map { sample(it, lat = 47.6 + it * 1e-4, alt = 100.0) }
        val data = assertNotNull(StreamBuilder.fromRecords(records, start.toEpochMilliseconds(), emptyList()))
        assertEquals(4448, data.distance!!.last()!!, 5)
        assertEquals(11119, data.speed!![2]!!, 20)
        assertEquals(10000, data.altitude!!.first())
    }

    @Test
    fun keepsGearChangesRelativeToTheStart() {
        val changes = listOf(DecodedGearChange((start + 30.seconds).toEpochMilliseconds(), rear = true, 2, null, 5, null))
        val data = assertNotNull(StreamBuilder.fromRecords(listOf(sample(0, hr = 100)), start.toEpochMilliseconds(), changes))
        assertEquals(listOf(GearChange(30, 2, 5)), data.gears)
    }

    private fun at(sec: Long) = (start + sec.seconds).toEpochMilliseconds()

    private fun dive(sec: Long, depth: Double = 20.0) = sample(sec).copy(depthMeters = depth)

    @Suppress("LongParameterList") // Everything fromRecords takes besides the records.
    private fun build(
        records: List<DecodedSample>,
        switches: List<DecodedGasSwitch> = emptyList(),
        gases: List<DiveGas> = emptyList(),
        tanks: List<DiveTank> = emptyList(),
        updates: List<DecodedTankUpdate> = emptyList(),
        laps: List<DecodedSummary> = emptyList(),
        elapsed: Double? = null,
    ) = assertNotNull(StreamBuilder.fromRecords(records, at(0), emptyList(), switches, gases, tanks, updates, laps, elapsed))

    @Test
    fun keepsDiveStateInLexiconUnits() {
        val records =
            listOf(
                dive(0)
                    .copy(
                        ppO2Bar = 0.42,
                        cnsPercent = 1,
                        n2LoadPercent = 10,
                        ndlSeconds = 900,
                        nextStopDepthMeters = 0.0,
                        nextStopSeconds = 0,
                        ttsSeconds = 60,
                    ),
                dive(10)
                    .copy(
                        ppO2Bar = 1.3,
                        measuredPpO2Bars = listOf(1.18, 1.20, 1.22),
                        ndlSeconds = 0,
                        nextStopDepthMeters = 6.0,
                        nextStopSeconds = 120,
                        ttsSeconds = 480,
                    ),
            )
        val data = build(records)
        assertEquals(listOf(420, 1200), data.ppO2, "millibar; the cells' average wins over the calculated value")
        assertEquals(listOf(1, null), data.cns)
        assertEquals(listOf(10, null), data.n2Load)
        assertEquals(listOf(900, 0), data.ndl)
        assertEquals(listOf(null, 600), data.decoStopDepth, "centimeters, and none outside deco")
        assertEquals(listOf(null, 120), data.decoStopTime)
        assertEquals(listOf(60, 480), data.tts)
    }

    @Test
    fun aRecreationalDiveHasNoDecoChannels() {
        val data = build(listOf(dive(0).copy(temperatureCelsius = 24.0), dive(10)))
        assertEquals(listOf(StreamChannel.TEMPERATURE, StreamChannel.DEPTH), data.channels)
    }

    @Test
    fun keepsRespirationRunningDynamicsAndBalance() {
        val records =
            listOf(
                sample(0, hr = 150)
                    .copy(
                        respirationRate = 30.55,
                        verticalOscillationMm = 85.4,
                        stanceTimeMs = 245.5,
                        stepLengthMm = 1250.0,
                        verticalRatioPercent = 6.83,
                        stanceTimeBalancePercent = 49.75,
                        leftRightBalance = 0x80 or 52,
                    ),
                // A balance without the right-side flag says nothing about which leg.
                sample(1, hr = 151).copy(leftRightBalance = 48),
            )
        val data = build(records)
        assertEquals(306, data.respirationRate!!.first(), "tenths of a breath")
        assertEquals(85, data.verticalOscillation!!.first())
        assertEquals(246, data.stanceTime!!.first())
        assertEquals(1250, data.stepLength!!.first())
        assertEquals(683, data.verticalRatio!!.first(), "hundredths of a percent")
        assertEquals(4975, data.stanceTimeBalance!!.first())
        assertEquals(listOf(52, null), data.leftRightBalance)
    }

    @Test
    fun keepsCyclingDynamicsPhysiologyEBikeAndStrydChannelsInLexiconUnits() {
        val records =
            listOf(
                sample(0, hr = 140)
                    .copy(
                        leftTorqueEffectiveness = 80.0,
                        rightTorqueEffectiveness = 76.5,
                        leftPedalSmoothness = 22.0,
                        rightPedalSmoothness = 20.5,
                        leftPcoMm = -2,
                        rightPcoMm = 3,
                        coreTemperatureCelsius = 38.25,
                        saturatedHemoglobinPercent = 65.5,
                        totalHemoglobinConc = 12.34,
                        ebikeBatteryPercent = 90,
                        ebikeAssistMode = 2,
                        ebikeAssistLevelPercent = 50,
                        formPowerWatts = 70.0,
                        legSpringStiffnessKnPerM = 9.5,
                    )
            )
        val data = build(records)
        assertEquals(listOf(8000), data.torqueEffectivenessLeft, "hundredths of a percent")
        assertEquals(listOf(7650), data.torqueEffectivenessRight)
        assertEquals(listOf(2200), data.pedalSmoothnessLeft)
        assertEquals(listOf(2050), data.pedalSmoothnessRight)
        assertEquals(listOf(-2), data.pcoLeft)
        assertEquals(listOf(3), data.pcoRight)
        assertEquals(listOf(3825), data.coreTemperature, "hundredths of a degree")
        assertEquals(listOf(655), data.smO2, "tenths of a percent")
        assertEquals(listOf(1234), data.totalHemoglobin, "hundredths of g/dL")
        assertEquals(listOf(90), data.ebikeBattery)
        assertEquals(listOf(2), data.ebikeAssistMode)
        assertEquals(listOf(50), data.ebikeAssistLevel)
        assertEquals(listOf(70), data.formPower)
        assertEquals(listOf(950), data.legSpringStiffness, "hundredths of kN/m")
    }

    @Test
    fun aFileWithoutTheNewChannelsLeavesThemOut() {
        val data = build(listOf(sample(0, hr = 140), sample(1, hr = 141)))
        assertEquals(listOf(StreamChannel.HEART_RATE), data.channels)
    }

    @Test
    fun theFirstGasIsASwitchAtZero() {
        val records = listOf(dive(0), dive(1920), dive(2400))
        val air = DiveGas(21, 0)
        val ean50 = DiveGas(50, 0)
        assertEquals(
            listOf(GasSwitch(0, 21, 0), GasSwitch(1920, 50, 0)),
            build(records, listOf(DecodedGasSwitch(at(1920), ean50)), listOf(air, ean50)).gasSwitches,
        )
        assertEquals(listOf(GasSwitch(0, 32, 0)), build(records, gases = listOf(DiveGas(32, 0))).gasSwitches, "a single-gas dive")
        assertEquals(
            listOf(GasSwitch(0, 21, 0), GasSwitch(1920, 50, 0), GasSwitch(2400, 21, 0)),
            build(
                    records,
                    listOf(DecodedGasSwitch(at(5), air), DecodedGasSwitch(at(1920), ean50), DecodedGasSwitch(at(2400), air)),
                    listOf(air, ean50),
                )
                .gasSwitches,
            "a switch to the first gas once under way moves to 0; a switch back stays",
        )
        assertNull(build(records).gasSwitches, "no gases, no switches")
    }

    @Test
    fun tankReadingsGoOnTheNearestSample() {
        val records = (0L..6L).map { dive(it * 10) }
        val tanks =
            listOf(
                DiveTank(200.0, 150.0, null, sensor = "a"),
                DiveTank(null, null, null, sensor = "b"),
                DiveTank(210.0, 200.0, null, sensor = "c"),
            )
        val updates =
            listOf(
                DecodedTankUpdate(at(1), "a", 200.0),
                DecodedTankUpdate(at(3), "a", 199.0),
                DecodedTankUpdate(at(22), "a", 180.0),
                DecodedTankUpdate(at(95), "a", 150.0),
                DecodedTankUpdate(at(60), "c", 200.0),
            )
        val pressures = build(records, tanks = tanks, updates = updates).tankPressures!!
        assertEquals(
            listOf(200_000, null, 180_000, null, null, null, null),
            pressures[0].pressure,
            "the nearest reading wins; 35 s off has no place",
        )
        assertEquals(List(7) { null }, pressures[1].pressure, "a tank with no readings keeps its place")
        assertEquals(listOf(null, null, null, null, null, null, 200_000), pressures[2].pressure)
        assertNull(build(records, tanks = tanks).tankPressures)
    }

    @Test
    fun keepsLapsButNotOneSpanningTheActivity() {
        fun lap(startSec: Long, elapsed: Double, distance: Double) =
            DecodedSummary(
                null,
                null,
                at(startSec),
                elapsed,
                elapsed - 2,
                distance,
                12.0,
                4.0,
                6.5,
                150,
                170,
                210,
                88,
                null,
                totalDescentMeters = 3.0,
            )
        val records = listOf(sample(0, hr = 140), sample(600, hr = 150))
        val laps = build(records, laps = listOf(lap(300, 300.0, 1500.0), lap(0, 300.0, 1400.0)), elapsed = 600.0).laps!!
        assertEquals(listOf(0, 300), laps.map { it.start }, "in order")
        assertEquals(Lap(0, 300, 298, 1400, 4000, 6500, 150, 170, 210, 88, 12, 3), laps.first())
        assertNull(build(records, laps = listOf(lap(0, 600.4, 3000.0)), elapsed = 600.0).laps)
        assertEquals(
            1,
            build(records, laps = listOf(lap(0, 400.0, 2000.0)), elapsed = 600.0).laps?.size,
            "one lap that stops short is a lap",
        )
    }

    @Test
    fun keepsLengthsSetsAndLegsInTimeOrder() {
        val data =
            assertNotNull(
                StreamBuilder.fromRecords(
                    listOf(sample(0, hr = 100)),
                    at(0),
                    emptyList(),
                    lengths =
                        listOf(DecodedLength(at(30), 25.0, null, null, "idle"), DecodedLength(at(0), 30.0, "freestyle", 14, "active")),
                    sets = listOf(DecodedSet(at(0), at(40), 40.0, "active", "bench_press", "bench_press", 8, 60.0)),
                    legs =
                        listOf(
                            summary("transition", at(600), 60.0),
                            summary("running", at(0), 600.0),
                            summary("cycling", at(660), 1200.0),
                        ),
                )
            )
        assertEquals(listOf(Length(0, 30, SwimStroke.FREESTYLE, 14), Length(30, 25, rest = true)), data.lengths)
        assertEquals(listOf(WorkoutSet(0, 40, category = "bench_press", exercise = "bench_press", reps = 8, weight = 60_000)), data.sets)
        assertEquals(listOf(Sport.RUN, Sport.MULTISPORT, Sport.RIDE), data.legs?.map { it.sport })
        assertEquals(listOf(StreamChannel.HEART_RATE, StreamChannel.LENGTHS, StreamChannel.SETS, StreamChannel.LEGS), data.channels)
    }

    @Test
    fun keepsAWorkoutsSetsWithoutSamples() {
        val data =
            assertNotNull(
                StreamBuilder.fromRecords(
                    emptyList(),
                    at(0),
                    emptyList(),
                    sets = listOf(DecodedSet(at(0), null, 40.0, "rest", null, null, null, null)),
                )
            )
        assertEquals(listOf(WorkoutSet(0, 40, rest = true)), data.sets)
        assertEquals(emptyList(), data.time)
    }

    private fun summary(sport: String, startMillis: Long, elapsed: Double) =
        DecodedSummary(sport, null, startMillis, elapsed, elapsed, null, null, null, null, null, null, null, null, null)

    @Test
    fun nullWithNothingToChart() {
        assertNull(StreamBuilder.fromRecords(listOf(sample(0)), start.toEpochMilliseconds(), emptyList()))
    }

    @Test
    fun recordingKeepsDistanceAltitudeAndSpeed() {
        val samples = (0L..2L).map { LocationSample(47.6 + it * 1e-4, -122.3, 50.0, 11.0, 5.0, start + it.seconds) }
        val data = assertNotNull(StreamBuilder.fromLocations(samples, start))
        assertEquals(listOf(0, 1, 2), data.time)
        assertEquals(listOf(5000, 5000, 5000), data.altitude)
        assertEquals(listOf(11000, 11000, 11000), data.speed)
        assertEquals(2224, data.distance!!.last()!!, 2)
    }

    private fun assertEquals(expected: Int, actual: Int, tolerance: Int) =
        kotlin.test.assertTrue(kotlin.math.abs(expected - actual) <= tolerance, "expected $expected ± $tolerance, was $actual")
}
