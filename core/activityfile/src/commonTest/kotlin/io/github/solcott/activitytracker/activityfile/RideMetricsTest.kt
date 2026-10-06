package io.github.solcott.activitytracker.activityfile

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/** What files without the device's totals (TCX, GPX, a FIT file missing them) get from their samples. */
class RideMetricsTest {
    private val start = Instant.parse("2026-10-02T19:52:33Z")

    private fun sample(sec: Long, power: Int? = null, cadence: Int? = null, altitude: Double? = null) =
        DecodedSample((start + sec.seconds).toEpochMilliseconds(), null, null, altitude, null, cadence, power, null, null, null)

    private fun map(
        records: List<DecodedSample>,
        gearChanges: List<DecodedGearChange> = emptyList(),
        sensors: List<DecodedSensor> = emptyList(),
    ) = DecodedActivityMapper.map(DecodedActivity(null, emptyList(), emptyList(), records, sensors = sensors, gearChanges = gearChanges))

    @Test
    fun powerComesFromTheSamplesWhenNoSummaryHasIt() {
        // 30 s at 100 W, then 30 s at 300 W.
        val ride = map((0L until 60L).map { sample(it, power = if (it < 30) 100 else 300) })

        val power = ride.power!!
        assertEquals(200, power.avgWatts)
        assertEquals(300, power.maxWatts)
        assertEquals(223, power.normalizedWatts, "the 4th-power mean of the 30 s rolling averages")
        assertEquals(12_000.0, power.workJoules)
        assertNull(power.intensityFactor, "needs the athlete's FTP, which only the device knows")
        assertNull(power.trainingStressScore)
    }

    @Test
    fun aPauseCountsOnce() {
        // A 10-minute gap is auto-pause, not ten minutes at 100 W.
        val power = map(listOf(sample(0, power = 100), sample(600, power = 300))).power!!
        assertEquals(200, power.avgWatts)
        assertEquals(400.0, power.workJoules)
        assertNull(power.normalizedWatts, "under 30 s of power")
    }

    @Test
    fun noPowerMeterMeansNoPower() {
        assertNull(map(listOf(sample(0, cadence = 80), sample(1, cadence = 90))).power)
    }

    @Test
    fun averageCadenceLeavesOutCoasting() {
        val ride = map(listOf(sample(0, cadence = 80), sample(1, cadence = 0), sample(2, cadence = 100), sample(3, cadence = 0)))
        assertEquals(90, ride.avgCadence)
        assertEquals(100, ride.maxCadence)
    }

    @Test
    fun descentIgnoresAltitudeNoise() {
        val altitudes = listOf(100.0, 110.0, 95.0, 80.0, 82.0)
        val ride = map(altitudes.mapIndexed { i, alt -> sample(i.toLong(), altitude = alt) })
        assertEquals(30.0, ride.elevationLossMeters)
    }

    @Test
    fun shiftsAreCountedFromTheEventsWhenTheSessionHasNoCount() {
        fun change(sec: Long, rear: Boolean, gear: Int, teeth: Int) =
            DecodedGearChange(
                (start + sec.seconds).toEpochMilliseconds(),
                rear,
                frontGear = 1,
                frontTeeth = 34,
                rearGear = gear,
                rearTeeth = teeth,
            )
        val ride =
            map(
                listOf(sample(0, cadence = 80), sample(100, cadence = 80)),
                gearChanges = listOf(change(0, true, 1, 52), change(30, true, 2, 42), change(40, false, 2, 42), change(60, true, 1, 52)),
            )

        val shifting = ride.shifting!!
        assertEquals(3, shifting.rearShifts)
        assertEquals(1, shifting.frontShifts)
        assertEquals(listOf(34), shifting.chainrings)
        assertEquals(listOf(42, 52), shifting.cogs)
        assertEquals(listOf(GearTime(1, 52, 70), GearTime(2, 42, 30)), shifting.timeInGears, "the last gear holds until the last sample")
    }

    @Test
    fun noShiftingWithoutGearChanges() {
        assertNull(map(listOf(sample(0, cadence = 80))).shifting)
    }

    @Test
    fun sensorsKeepTheirLastMessageAndLeaveOutTheDevicesOwn() {
        fun sensor(index: Int?, source: String, type: String?, manufacturer: String, product: String?, battery: String? = null) =
            DecodedSensor(index, source, type, manufacturer, product, battery, null)
        val sensors =
            map(
                    listOf(sample(0)),
                    sensors =
                        listOf(
                            sensor(0, "local", null, "garmin", "edge840"),
                            sensor(1, "local", "barometer", "garmin", "edge840"),
                            sensor(2, "antplus", "bikeRadar", "garmin", "varia", battery = "good"),
                            sensor(3, "antplus", "control", "garmin", "1234"),
                            sensor(2, "antplus", "bikeRadar", "garmin", "varia", battery = "critical"),
                        ),
                )
                .sensors

        assertEquals(
            listOf(
                ImportedSensor(SensorKind.Radar, "Garmin", "Varia", SensorBattery.Critical, null),
                ImportedSensor(SensorKind.Other, "Garmin", null, null, null),
            ),
            sensors,
        )
    }

    @Test
    fun namesDevicesInEverySdksSpelling() {
        assertEquals("Edge 840", DeviceNames.product("EDGE_840"))
        assertEquals("Edge 840", DeviceNames.product("edge840"))
        assertEquals("HRM Pro", DeviceNames.product("hrmPro"))
        assertEquals("HRM 200", DeviceNames.product("HRM_200"))
        assertEquals("Rally 100/200", DeviceNames.product("RALLY_200"), "as Garmin Connect names the pedals, single- or dual-sided")
        assertEquals("Rally 100/200", DeviceNames.product("rally200"))
        assertEquals("Forerunner 965", DeviceNames.product("Forerunner 965"), "a display name is kept as written")
        assertNull(DeviceNames.product("1052"), "a product number only its maker can name")
        assertEquals("Wahoo Fitness", DeviceNames.manufacturer("WAHOO_FITNESS"))
        assertEquals("Wahoo Fitness", DeviceNames.manufacturer("wahooFitness"))
        assertEquals("SRAM", DeviceNames.manufacturer("sram"))
        assertNull(DeviceNames.manufacturer("unknown"))
    }
}
