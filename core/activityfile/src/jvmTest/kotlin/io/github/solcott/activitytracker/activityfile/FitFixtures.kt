package io.github.solcott.activitytracker.activityfile

import com.garmin.fit.AntplusDeviceType
import com.garmin.fit.BatteryStatus
import com.garmin.fit.BenchPressExerciseName
import com.garmin.fit.BleDeviceType
import com.garmin.fit.Bool
import com.garmin.fit.BufferEncoder
import com.garmin.fit.DateTime
import com.garmin.fit.DeviceInfoMesg
import com.garmin.fit.DisplayMeasure
import com.garmin.fit.DiveAlert
import com.garmin.fit.DiveGasMesg
import com.garmin.fit.DiveGasMode
import com.garmin.fit.DiveGasStatus
import com.garmin.fit.DiveSettingsMesg
import com.garmin.fit.DiveSummaryMesg
import com.garmin.fit.Event
import com.garmin.fit.EventMesg
import com.garmin.fit.EventType
import com.garmin.fit.ExerciseCategory
import com.garmin.fit.File
import com.garmin.fit.FileIdMesg
import com.garmin.fit.Fit
import com.garmin.fit.GarminProduct
import com.garmin.fit.Intensity
import com.garmin.fit.JumpMesg
import com.garmin.fit.LapMesg
import com.garmin.fit.LapTrigger
import com.garmin.fit.LengthMesg
import com.garmin.fit.LengthType
import com.garmin.fit.LocalDeviceType
import com.garmin.fit.Manufacturer
import com.garmin.fit.Mesg
import com.garmin.fit.MesgNum
import com.garmin.fit.PushUpExerciseName
import com.garmin.fit.RecordMesg
import com.garmin.fit.SessionMesg
import com.garmin.fit.SetMesg
import com.garmin.fit.SetType
import com.garmin.fit.SourceType
import com.garmin.fit.SplitMesg
import com.garmin.fit.SplitType
import com.garmin.fit.Sport as FitSport
import com.garmin.fit.SubSport
import com.garmin.fit.SwimStroke
import com.garmin.fit.TankSummaryMesg
import com.garmin.fit.TankUpdateMesg
import com.garmin.fit.TissueModelType
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
     * loading and rates. Ten minutes at 0-20-0 m in fresh water on air, then nitrox 50 from the backup slot at 7 min; GF 35/75. Its
     * settings are ZHL-16C, ppO₂ limits of 1.4/1.6/1.6, setpoints of 0.7 and 1.3 and a 3-minute safety stop; it alerts the no-deco limit
     * reached at 5 min (dismissed by a key 5 s later) and the safety stop complete at 9 min.
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
                diveSettings(),
                alert(at(300), DiveAlert.NDL_REACHED),
                alert(at(305), DiveAlert.ALERT_DISMISSED_BY_KEY),
                alert(at(540), DiveAlert.SAFETY_STOP_COMPLETE),
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

    /**
     * [dive]'s settings: GF 35/75 in fresh water, ZHL-16C, ppO₂ limits of 1.4/1.6/1.6, setpoints of 0.7 and 1.3, a 3-minute safety stop.
     */
    private fun diveSettings() =
        DiveSettingsMesg().apply {
            gfLow = 35.toShort()
            gfHigh = 75.toShort()
            waterType = WaterType.FRESH
            waterDensity = 1000f
            model = TissueModelType.ZHL_16C
            po2Warn = 1.4f
            po2Critical = 1.6f
            po2Deco = 1.6f
            safetyStopEnabled = Bool.TRUE
            safetyStopTime = 180
            ccrLowSetpoint = 0.7f
            ccrHighSetpoint = 1.3f
        }

    private fun alert(time: DateTime, alert: DiveAlert) =
        EventMesg().apply {
            timestamp = time
            event = Event.DIVE_ALERT
            eventType = EventType.MARKER
            data = alert.value.toLong()
        }

    /**
     * A pool swim in a [poolLength] m pool (22.86 for 25 yd, with [unit] statute): four freestyle lengths of 30 s and 15 strokes, a 20 s
     * idle length at the wall, then four breaststroke lengths of 35 s and 10 strokes. Its laps are the two sets and the rest between.
     */
    fun poolSwim(start: Instant, poolLength: Float = 25f, unit: DisplayMeasure = DisplayMeasure.METRIC): ByteArray {
        val startedAt = DateTime(start.toJavaInstant())
        fun at(seconds: Long) = DateTime(startedAt).apply { add(seconds) }
        var t = 0L
        val lengths = POOL_LENGTHS.map { (stroke, seconds, strokes) ->
            LengthMesg().apply {
                startTime = at(t)
                timestamp = at(t + seconds)
                totalElapsedTime = seconds.toFloat()
                totalTimerTime = seconds.toFloat()
                if (stroke == null) {
                    lengthType = LengthType.IDLE
                } else {
                    lengthType = LengthType.ACTIVE
                    swimStroke = stroke
                    totalStrokes = strokes
                }
                t += seconds
            }
        }
        return encode(
            start,
            FitSport.SWIMMING,
            SubSport.LAP_SWIMMING,
            distance = poolLength * 8,
            avgHr = 130,
            points = (t / 10 + 1).toInt(),
            interval = 10,
            extra = lengths,
            laps =
                listOf(
                    FitLap(0, 120f, poolLength * 4) { intensity = Intensity.ACTIVE },
                    FitLap(120, 20f, 0f) { intensity = Intensity.REST },
                    FitLap(140, 140f, poolLength * 4) { intensity = Intensity.ACTIVE },
                ),
            session = {
                this.poolLength = poolLength
                poolLengthUnit = unit
                numLengths = POOL_LENGTHS.size
            },
        ) { i, record ->
            record.heartRate = (120 + i % 15).toShort()
        }
    }

    /** Each length of [poolSwim]: its stroke (null when idle), seconds and stroke count. */
    val POOL_LENGTHS: List<Triple<SwimStroke?, Long, Int>> =
        List(4) { Triple(SwimStroke.FREESTYLE, 30L, 15) } + Triple(null, 20L, 0) + List(4) { Triple(SwimStroke.BREASTSTROKE, 35L, 10) }

    /**
     * A strength workout: three 40 s sets of 8 barbell bench presses at 60 kg with 90 s rests between, a 60 s rest, a 30 s set of 20
     * push-ups with no weight, a 60 s rest, and a 30 s set logged only as the squat category.
     */
    fun strengthWorkout(start: Instant): ByteArray {
        val startedAt = DateTime(start.toJavaInstant())
        var t = 0L
        fun set(seconds: Long, configure: SetMesg.() -> Unit) =
            SetMesg().apply {
                startTime = DateTime(startedAt).apply { add(t) }
                timestamp = DateTime(startedAt).apply { add(t + seconds) }
                duration = seconds.toFloat()
                setType = SetType.ACTIVE
                configure()
                t += seconds
            }
        fun rest(seconds: Long) = set(seconds) { setType = SetType.REST }
        fun bench() =
            set(40) {
                setCategory(0, ExerciseCategory.BENCH_PRESS)
                setCategorySubtype(0, BenchPressExerciseName.BARBELL_BENCH_PRESS)
                repetitions = 8
                weight = 60f
            }
        val sets =
            listOf(
                bench(),
                rest(90),
                bench(),
                rest(90),
                bench(),
                rest(60),
                set(30) {
                    setCategory(0, ExerciseCategory.PUSH_UP)
                    setCategorySubtype(0, PushUpExerciseName.PUSH_UP)
                    repetitions = 20
                },
                rest(60),
                set(30) { setCategory(0, ExerciseCategory.SQUAT) },
            )
        return encode(
            start,
            FitSport.TRAINING,
            SubSport.STRENGTH_TRAINING,
            distance = 0f,
            avgHr = 110,
            points = (t / 30 + 1).toInt(),
            extra = sets,
        ) { i, record ->
            record.heartRate = (100 + i * 2).toShort()
        }
    }

    const val SKI_RUNS = 8

    /**
     * A resort ski day: eight times a 300 s lift ride up 300 m, then a 240 s run of 1500 m down 300 m, as Garmin's run and lift splits, and
     * the watch's own lap per lift and run pair.
     */
    fun resortSkiDay(start: Instant): ByteArray {
        val startedAt = DateTime(start.toJavaInstant())
        fun at(seconds: Long) = DateTime(startedAt).apply { add(seconds) }
        val splits =
            (0 until SKI_RUNS).flatMap { i ->
                val lift = i * 540L
                listOf(
                    SplitMesg().apply {
                        splitType = SplitType.SKI_LIFT_SPLIT
                        startTime = at(lift)
                        timestamp = at(lift + 300)
                        totalElapsedTime = 300f
                        totalTimerTime = 300f
                        totalDistance = 800f
                        totalAscent = 300
                    },
                    SplitMesg().apply {
                        splitType = SplitType.SKI_RUN_SPLIT
                        startTime = at(lift + 300)
                        timestamp = at(lift + 540)
                        totalElapsedTime = 240f
                        totalTimerTime = 240f
                        totalDistance = 1500f
                        totalDescent = 300
                        maxSpeed = 18f
                    },
                )
            }
        return encode(
            start,
            FitSport.ALPINE_SKIING,
            SubSport.GENERIC,
            distance = SKI_RUNS * 2300f,
            avgHr = 115,
            points = SKI_RUNS * 9 + 1,
            interval = 60,
            extra = splits,
            laps = (0 until SKI_RUNS).map { FitLap(it * 540L, 540f, 2300f) },
        ) { i, record ->
            record.enhancedAltitude = 2000f + (if (i % 9 <= 5) i % 9 * 60f else (9 - i % 9) * 100f)
        }
    }

    /** A backcountry ski tour with three laps of its own and no run splits. */
    fun backcountrySki(start: Instant): ByteArray =
        encode(
            start,
            FitSport.ALPINE_SKIING,
            SubSport.BACKCOUNTRY,
            distance = 6000f,
            avgHr = 140,
            points = 61,
            interval = 60,
            laps = listOf(FitLap(0, 1800f, 3000f), FitLap(1800, 1200f, 2000f), FitLap(3000, 600f, 1000f)),
        ) { i, record ->
            record.enhancedAltitude = 2000f + i * 10f
        }

    /** Each leg of [triathlon]: its sport and sub-sport, start and seconds, meters, and laps of equal length. */
    class TriLeg(val sport: FitSport, val subSport: SubSport, val start: Long, val seconds: Long, val meters: Float, val laps: Int = 1)

    val TRI_LEGS =
        listOf(
            TriLeg(FitSport.SWIMMING, SubSport.OPEN_WATER, 0, 1800, 1500f),
            TriLeg(FitSport.TRANSITION, SubSport.GENERIC, 1800, 120, 0f),
            TriLeg(FitSport.CYCLING, SubSport.ROAD, 1920, 3600, 40_000f),
            TriLeg(FitSport.TRANSITION, SubSport.GENERIC, 5520, 90, 0f),
            TriLeg(FitSport.RUNNING, SubSport.GENERIC, 5610, 2700, 10_000f, laps = 5),
        )

    /**
     * An Olympic triathlon as a multisport watch writes it: a session per leg and transition ([TRI_LEGS]), each leg's laps (the run's five
     * of 2 km, by distance), and a record every 30 s.
     */
    fun triathlon(start: Instant): ByteArray {
        val startedAt = DateTime(start.toJavaInstant())
        fun at(seconds: Long) = DateTime(startedAt).apply { add(seconds) }
        val end = TRI_LEGS.last().let { it.start + it.seconds }
        val encoder = BufferEncoder(Fit.ProtocolVersion.V2_0)
        encoder.write(
            FileIdMesg().apply {
                type = File.ACTIVITY
                manufacturer = Manufacturer.GARMIN
                product = GarminProduct.FENIX7
                timeCreated = startedAt
            }
        )
        for (t in 0..end step 30) {
            val leg = TRI_LEGS.last { it.start <= t }
            val before = TRI_LEGS.filter { it.start < leg.start }.sumOf { it.meters.toDouble() }
            encoder.write(
                RecordMesg().apply {
                    timestamp = at(t)
                    distance = (before + leg.meters * (t - leg.start).coerceAtMost(leg.seconds) / leg.seconds).toFloat()
                    heartRate = (130 + t % 30).toShort()
                }
            )
        }
        for (leg in TRI_LEGS) {
            val lapSeconds = leg.seconds / leg.laps
            repeat(leg.laps) { i ->
                encoder.write(
                    LapMesg().apply {
                        startTime = at(leg.start + i * lapSeconds)
                        timestamp = at(leg.start + (i + 1) * lapSeconds)
                        sport = leg.sport
                        subSport = leg.subSport
                        totalElapsedTime = lapSeconds.toFloat()
                        totalTimerTime = lapSeconds.toFloat()
                        totalDistance = leg.meters / leg.laps
                        lapTrigger = if (leg.laps > 1) LapTrigger.DISTANCE else LapTrigger.SESSION_END
                    }
                )
            }
        }
        for (leg in TRI_LEGS) {
            encoder.write(
                SessionMesg().apply {
                    startTime = at(leg.start)
                    timestamp = at(leg.start + leg.seconds)
                    sport = leg.sport
                    subSport = leg.subSport
                    totalElapsedTime = leg.seconds.toFloat()
                    totalTimerTime = leg.seconds.toFloat()
                    totalDistance = leg.meters
                    avgHeartRate = 140.toShort()
                }
            )
        }
        return encoder.close()
    }

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
    internal fun encode(
        start: Instant,
        sport: FitSport,
        subSport: SubSport,
        distance: Float,
        avgHr: Int,
        points: Int,
        interval: Long = 30,
        before: List<Mesg> = emptyList(),
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
        before.forEach(encoder::write)
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
                if (avgHr > 0) avgHeartRate = avgHr.toShort()
                session()
            }
        )
        return encoder.close()
    }
}
