package io.github.solcott.activitytracker.activityfile

import com.garmin.fit.AntplusDeviceType
import com.garmin.fit.BatteryStatus
import com.garmin.fit.BleDeviceType
import com.garmin.fit.BufferEncoder
import com.garmin.fit.DateTime
import com.garmin.fit.DeviceInfoMesg
import com.garmin.fit.DiveGasMesg
import com.garmin.fit.DiveGasMode
import com.garmin.fit.DiveGasStatus
import com.garmin.fit.DiveSettingsMesg
import com.garmin.fit.DiveSummaryMesg
import com.garmin.fit.Event
import com.garmin.fit.EventMesg
import com.garmin.fit.EventType
import com.garmin.fit.File
import com.garmin.fit.FileIdMesg
import com.garmin.fit.Fit
import com.garmin.fit.GarminProduct
import com.garmin.fit.JumpMesg
import com.garmin.fit.LapMesg
import com.garmin.fit.LocalDeviceType
import com.garmin.fit.Manufacturer
import com.garmin.fit.Mesg
import com.garmin.fit.MesgNum
import com.garmin.fit.RecordMesg
import com.garmin.fit.SessionMesg
import com.garmin.fit.SourceType
import com.garmin.fit.Sport as FitSport
import com.garmin.fit.SubSport
import com.garmin.fit.TankSummaryMesg
import com.garmin.fit.TankUpdateMesg
import com.garmin.fit.UserProfileMesg
import com.garmin.fit.WaterType
import com.garmin.fit.ZonesTargetMesg
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.time.Instant
import kotlin.time.toJavaInstant

/** Small FIT activity files, written with Garmin's encoder so the decoder reads the same bytes a device would produce. */
object FitFixtures {
    const val RUN_POINTS = 11

    private const val SEMICIRCLES_PER_DEGREE = 2_147_483_648.0 / 180.0

    /** A 1 km run north from 47.6, -122.3: one point every 30 s, heart rate climbing from 120 to 190 bpm (avg 155). */
    fun run(start: Instant, extra: List<Mesg> = emptyList()): ByteArray =
        encode(start, FitSport.RUNNING, SubSport.GENERIC, distance = 1000f, avgHr = 155, points = RUN_POINTS, extra = extra) { i, record ->
            record.positionLat = ((47.6 + i * 0.0009) * SEMICIRCLES_PER_DEGREE).roundToInt()
            record.positionLong = (-122.3 * SEMICIRCLES_PER_DEGREE).roundToInt()
            record.enhancedAltitude = 50f + i
            record.heartRate = (120 + i * 7).toShort()
            record.distance = i * 100f
        }

    /** The athlete's settings a Garmin watch writes into each activity: `zones_target`, and `user_profile` with resting heart rate. */
    fun athleteSettings(maxHr: Int, thresholdHr: Int, ftp: Int, restingHr: Int): List<Mesg> =
        listOf(
            ZonesTargetMesg().apply {
                maxHeartRate = maxHr.toShort()
                thresholdHeartRate = thresholdHr.toShort()
                functionalThresholdPower = ftp
            },
            UserProfileMesg().apply { restingHeartRate = restingHr.toShort() },
        )

    const val INTERVAL_LAPS = 8

    /**
     * Eight 1-minute laps of a track run with a running dynamics pod (a point every 10 s): respiration rate, vertical oscillation, stance
     * time and its balance, step length and vertical ratio on every record. Each lap is 300 m.
     */
    fun intervalRun(start: Instant): ByteArray =
        encode(
            start,
            FitSport.RUNNING,
            SubSport.TRACK,
            distance = INTERVAL_LAPS * 300f,
            avgHr = 160,
            points = INTERVAL_LAPS * 6 + 1,
            interval = 10,
            laps = (0 until INTERVAL_LAPS).map { FitLap(it * 60L, 60f, 300f) },
        ) { i, record ->
            record.distance = i * 50f
            record.heartRate = (150 + i % 20).toShort()
            record.enhancedRespirationRate = 30.5f
            record.verticalOscillation = 85.4f
            record.stanceTime = 245.5f
            record.stanceTimeBalance = 49.75f
            record.stepLength = 1250f
            record.verticalRatio = 6.83f
        }

    /** A trainer ride: power and cadence, no position. */
    fun indoorRide(start: Instant): ByteArray =
        encode(start, FitSport.CYCLING, SubSport.INDOOR_CYCLING, distance = 5000f, avgHr = 140, points = 5) { _, record ->
            record.power = 200
            record.cadence = 90.toShort()
        }

    const val MTB_POINTS = 11

    /**
     * Ten minutes of mountain biking on an Edge (a point a minute) with a Garmin HRM over Bluetooth, and an SRAM power meter (battery low)
     * and AXS derailleur over ANT+. Each device writes `device_info` at the start and the end. Rear shifts to gear 5 (21T) at the start,
     * gear 7 (17T) at 1 min and gear 6 (19T) at 2 min; two jumps; the session's power, shifting and trail totals.
     */
    @Suppress("LongMethod") // One file, message by message.
    fun mtbRide(start: Instant): ByteArray {
        val startedAt = DateTime(start.toJavaInstant())
        fun at(seconds: Long) = DateTime(startedAt).apply { add(seconds) }
        fun devices(seconds: Long, hrmBattery: Int) =
            listOf<Mesg>(
                device(at(seconds), 0, SourceType.LOCAL, Manufacturer.GARMIN, GarminProduct.EDGE_840),
                device(at(seconds), 1, SourceType.LOCAL, Manufacturer.GARMIN, GarminProduct.EDGE_840) {
                    localDeviceType = LocalDeviceType.BAROMETER
                },
                device(at(seconds), 2, SourceType.BLUETOOTH_LOW_ENERGY, Manufacturer.GARMIN, GarminProduct.HRM_200) {
                    bleDeviceType = BleDeviceType.HEART_RATE
                    batteryStatus = BatteryStatus.GOOD
                    batteryLevel = hrmBattery.toShort()
                },
                device(at(seconds), 3, SourceType.ANTPLUS, Manufacturer.SRAM, 1052) {
                    antplusDeviceType = AntplusDeviceType.BIKE_POWER
                    batteryStatus = BatteryStatus.LOW
                },
                device(at(seconds), 4, SourceType.ANTPLUS, Manufacturer.SRAM, 1060) {
                    antplusDeviceType = AntplusDeviceType.SHIFTING
                    batteryStatus = BatteryStatus.OK
                },
            )
        fun shift(seconds: Long, gear: Int, teeth: Int) =
            EventMesg().apply {
                timestamp = at(seconds)
                event = Event.REAR_GEAR_CHANGE
                eventType = EventType.MARKER
                frontGearNum = 1.toShort()
                frontGear = 32.toShort()
                rearGearNum = gear.toShort()
                rearGear = teeth.toShort()
            }
        fun jump(seconds: Long, hangTime: Float, distance: Float) =
            JumpMesg().apply {
                timestamp = at(seconds)
                this.hangTime = hangTime
                this.distance = distance
            }
        val extra =
            listOf<Mesg>(
                EventMesg().apply {
                    // A lap button press, before the lap messages the encoder writes.
                    timestamp = at(300)
                    event = Event.LAP
                    eventType = EventType.STOP
                }
            ) +
                devices(0, hrmBattery = 80) +
                listOf(shift(0, 5, 21), shift(60, 7, 17), shift(120, 6, 19), jump(200, 0.4f, 3.5f), jump(400, 0.9f, 8.2f)) +
                devices((MTB_POINTS - 1) * 60L, hrmBattery = 75)
        return encode(
            start,
            FitSport.CYCLING,
            SubSport.MOUNTAIN,
            distance = 3000f,
            avgHr = 150,
            points = MTB_POINTS,
            interval = 60,
            extra = extra,
            laps =
                listOf(
                    FitLap(0, 300f, 1_200f) {
                        avgPower = 170
                        avgHeartRate = 140.toShort()
                        maxHeartRate = 160.toShort()
                        avgCadence = 78.toShort()
                        totalAscent = 80
                        totalDescent = 20
                        enhancedAvgSpeed = 4f
                        enhancedMaxSpeed = 9.5f
                    },
                    FitLap(300, 300f, 1_800f) { avgPower = 190 },
                ),
            session = {
                totalCalories = 450
                totalTrainingEffect = 3.4f
                totalAnaerobicTrainingEffect = 1.2f
                avgPower = 180
                maxPower = 650
                normalizedPower = 210
                thresholdPower = 250
                intensityFactor = 0.84f
                trainingStressScore = 45f
                totalWork = 300_000L
                avgCadence = 80.toShort()
                maxCadence = 110.toShort()
                totalDescent = 120
                rearGearShiftCount = 3
                jumpCount = 2
                totalGrit = 12.5f
                avgFlow = 2.5f
            },
        ) { _, record ->
            record.power = 180
            record.cadence = 80.toShort()
            // 52% from the right pedal.
            record.leftRightBalance = (0x80 or 52).toShort()
        }
    }

    @Suppress("LongParameterList")
    private fun device(
        timestamp: DateTime,
        index: Int,
        source: SourceType,
        manufacturer: Int,
        product: Int,
        configure: DeviceInfoMesg.() -> Unit = {},
    ) =
        DeviceInfoMesg().apply {
            this.timestamp = timestamp
            deviceIndex = index.toShort()
            sourceType = source
            this.manufacturer = manufacturer
            this.product = product
            configure()
        }

    /**
     * What the sample Descent files lack: RMV, a tank with its gas used and its readings every two minutes, a backup-only gas, a switch to
     * a slot that doesn't exist, a lap summary written before the session's, ppO₂ and a deco stop on the records, and the dive summary's N₂
     * loading and rates. Ten minutes at 0-20-0 m in fresh water on air, then nitrox 50 from the backup slot at 7 min; GF 35/75.
     */
    @Suppress("CyclomaticComplexMethod") // One file, message by message.
    fun dive(start: Instant): ByteArray {
        val startedAt = DateTime(start.toJavaInstant())
        fun at(seconds: Long) = DateTime(startedAt).apply { add(seconds) }
        fun summary(reference: Int) =
            DiveSummaryMesg().apply {
                timestamp = at(DIVE_POINTS * 60L)
                referenceMesg = reference
                referenceIndex = 0
                avgDepth = 12.5f
                maxDepth = 20f
                surfaceInterval = 3_600L
                bottomTime = 480f
                avgRmv = 14.2f
                avgPressureSac = 1.1f
                startN2 = 10
                endN2 = 55
                avgAscentRate = 0.15f
                maxAscentRate = 0.3f
                avgDescentRate = 0.2f
                maxDescentRate = 0.4f
                descentTime = 300f
            }
        val extra =
            listOf<Mesg>(
                DiveSettingsMesg().apply {
                    gfLow = 35.toShort()
                    gfHigh = 75.toShort()
                    waterType = WaterType.FRESH
                    waterDensity = 1000f
                },
                gas(0, 21, DiveGasStatus.ENABLED),
                gas(1, 50, DiveGasStatus.BACKUP_ONLY),
                gas(2, 21, DiveGasStatus.DISABLED),
                EventMesg().apply {
                    timestamp = at(420)
                    event = Event.DIVE_GAS_SWITCHED
                    eventType = EventType.MARKER
                    data = 1L
                },
                EventMesg().apply {
                    timestamp = at(480)
                    event = Event.DIVE_GAS_SWITCHED
                    eventType = EventType.MARKER
                    data = 9L
                },
                summary(MesgNum.LAP),
                summary(MesgNum.SESSION).apply {
                    diveNumber = 7L
                    startCns = 0.toShort()
                    endCns = 9.toShort()
                    o2Toxicity = 12
                },
                TankSummaryMesg().apply {
                    timestamp = at(DIVE_POINTS * 60L)
                    sensor = 42L
                    startPressure = 200f
                    endPressure = 60f
                    volumeUsed = 1_680f
                },
            ) +
                (0..DIVE_POINTS step 2).map { i ->
                    TankUpdateMesg().apply {
                        timestamp = at(i * 60L + 5)
                        sensor = 42L
                        pressure = 200f - i * 14f
                    }
                }
        return encode(start, FitSport.DIVING, SubSport.MULTI_GAS_DIVING, 0f, 80, DIVE_POINTS + 1, interval = 60, extra) { i, record ->
            record.depth = 20f - abs(i - DIVE_POINTS / 2) * 4f
            record.temperature = (18 - i % 3).toByte()
            record.po2 = if (i < 7) 0.21f * (1 + record.depth / 10) else 0.5f * (1 + record.depth / 10)
            record.cnsLoad = i.toShort()
            record.n2Load = 10 + i * 4
            // In deco from 5 min: a 3 m stop for 2 min, 4 min to surface.
            record.ndlTime = if (i < 5) 600L - i * 60 else 0L
            record.nextStopDepth = if (i < 5) 0f else 3f
            record.nextStopTime = if (i < 5) 0L else 120L
            record.timeToSurface = if (i < 5) 60L else 240L
        }
    }

    const val DIVE_POINTS = 10

    /** A lap the encoder writes, [startSeconds] after the activity's start. */
    class FitLap(val startSeconds: Long, val elapsedSeconds: Float, val distanceMeters: Float, val configure: LapMesg.() -> Unit = {})

    private fun gas(index: Int, oxygen: Int, status: DiveGasStatus) =
        DiveGasMesg().apply {
            messageIndex = index
            oxygenContent = oxygen.toShort()
            heliumContent = 0.toShort()
            this.status = status
            mode = DiveGasMode.OPEN_CIRCUIT
        }

    @Suppress("LongParameterList")
    private fun encode(
        start: Instant,
        sport: FitSport,
        subSport: SubSport,
        distance: Float,
        avgHr: Int,
        points: Int,
        interval: Long = 30,
        extra: List<Mesg> = emptyList(),
        laps: List<FitLap>? = null,
        session: SessionMesg.() -> Unit = {},
        fill: (Int, RecordMesg) -> Unit,
    ): ByteArray {
        val startedAt = DateTime(start.toJavaInstant())
        val elapsed = (points - 1) * interval.toFloat()
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
                    timestamp = DateTime(startedAt).apply { add(i * interval) }
                    fill(i, this)
                }
            )
        }
        extra.forEach(encoder::write)
        for (lap in laps ?: listOf(FitLap(0, elapsed, distance))) {
            encoder.write(
                LapMesg().apply {
                    startTime = DateTime(startedAt).apply { add(lap.startSeconds) }
                    timestamp = DateTime(startTime).apply { add(lap.elapsedSeconds.toLong()) }
                    totalElapsedTime = lap.elapsedSeconds
                    totalTimerTime = lap.elapsedSeconds
                    totalDistance = lap.distanceMeters
                    lap.configure(this)
                }
            )
        }
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
                session()
            }
        )
        return encoder.close()
    }
}
