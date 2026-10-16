package io.github.solcott.activitytracker.activityfile

import io.github.solcott.activitytracker.activityfile.FitProfile.MESSAGE_INDEX
import io.github.solcott.activitytracker.activityfile.FitProfile.TIMESTAMP
import io.github.solcott.activitytracker.lexicon.app.ActivityDive
import io.github.solcott.activitytracker.lexicon.app.ActivityType
import io.github.solcott.activitytracker.lexicon.app.GearChange
import io.github.solcott.activitytracker.lexicon.app.Lap
import io.github.solcott.activitytracker.lexicon.app.WaterType
import kotlin.math.roundToLong
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.offsetAt

/**
 * Writes an [ExportActivity] as a FIT activity file, encoded in common code by [FitOutput] with field numbers and scales from Garmin's
 * profile ([FitProfile], checked against Garmin's Java SDK in tests). The file holds a timer start, a `record` per whole second (the first
 * point in it), gear change events, a dive's settings, gases, gas switches, tank readings, tanks and summary, a timer stop, the stored laps
 * (or one spanning the activity), one session and the activity message, in the order Garmin's activity file guidance gives.
 *
 * A dive stored without switch times (one imported before they were kept) lists its later gases as enabled slots with no switch to them:
 * the file doesn't invent when.
 */
@Suppress("TooManyFunctions") // One or more per FIT message it writes.
object FitWriter {
    /** [zone] gives the activity message's local time; [createdAt] is the file ID's creation time. */
    fun write(activity: ExportActivity, zone: TimeZone, createdAt: Instant = Clock.System.now()): ByteArray {
        val out = FitOutput()
        val start = FitOutput.timestamp(activity.startedAt)
        val end = start + activity.elapsed.inWholeSeconds

        out.write(fileId(FitOutput.timestamp(createdAt)))
        out.write(FitMessage(FitProfile.FileCreator.NUM, listOf(FitProfile.FileCreator.SOFTWARE_VERSION(SOFTWARE_VERSION))))
        out.write(timerEvent(start, FitProfile.EventMessage.TYPE_START))
        writeRecords(out, activity, start)
        writeGearChanges(out, activity.gearChanges, start)
        activity.dive?.let { writeDive(out, it, activity, start, end) }
        out.write(timerEvent(end, FitProfile.EventMessage.TYPE_STOP_ALL))
        if (activity.laps.isEmpty()) {
            out.write(summary(FitProfile.Lap, activity, start, end))
        } else {
            activity.laps.forEachIndexed { index, lap -> out.write(storedLap(index, lap, start)) }
        }
        out.write(summary(FitProfile.Session, activity, start, end))
        out.write(activityMessage(activity, end, zone))
        return out.finish()
    }

    private fun fileId(created: Long) =
        with(FitProfile.FileId) {
            FitMessage(
                NUM,
                listOf(TYPE(TYPE_ACTIVITY), MANUFACTURER(MANUFACTURER_DEVELOPMENT), PRODUCT(0), TIME_CREATED(created)),
            )
        }

    private fun timerEvent(time: Long, eventType: Long) =
        with(FitProfile.EventMessage) { FitMessage(NUM, listOf(TIMESTAMP(time), EVENT(TIMER), EVENT_TYPE(eventType), EVENT_GROUP(0))) }

    /** One record per whole second from the start, the first point in it, with only the channels some point has. */
    private fun writeRecords(out: FitOutput, activity: ExportActivity, start: Long) {
        fun second(point: ExportPoint) = ((point.time - activity.startedAt).inWholeMilliseconds / MILLIS_PER_SECOND).roundToLong()
        val points = activity.points.distinctBy(::second)
        val channels = RECORD_CHANNELS.filter { (_, value) -> points.any { value(it) != null } }
        for (point in points) {
            val fields = listOf(TIMESTAMP(start + second(point))) + channels.map { (field, value) -> field(value(point)) }
            out.write(FitMessage(FitProfile.Record.NUM, fields))
        }
    }

    private val RECORD_CHANNELS: List<Pair<FitFieldDef, (ExportPoint) -> Long?>> =
        with(FitProfile.Record) {
            listOf(
                POSITION_LAT to { p -> p.latitude?.let(::semicircles) },
                POSITION_LONG to { p -> p.longitude?.let(::semicircles) },
                HEART_RATE to { p -> p.heartRate?.toLong() },
                CADENCE to { p -> p.cadence?.toLong() },
                DISTANCE to { p -> p.distanceMeters?.let { (it * CENTI).roundToLong() } },
                POWER to { p -> p.powerWatts?.toLong() },
                TEMPERATURE to { p -> p.temperatureCelsius?.roundToLong() },
                ENHANCED_SPEED to { p -> p.speedMps?.let { (it * MILLI).roundToLong() } },
                ENHANCED_ALTITUDE to { p -> p.altitudeMeters?.let { ((it + ALTITUDE_OFFSET) * ALTITUDE_SCALE).roundToLong() } },
                DEPTH to { p -> p.depthMeters?.let { (it * MILLI).roundToLong() } },
                LEFT_RIGHT_BALANCE to { p -> p.rightBalancePercent?.let { BALANCE_RIGHT or it.toLong() } },
                VERTICAL_OSCILLATION to { p -> p.verticalOscillationMm?.let { (it * DECI).roundToLong() } },
                STANCE_TIME to { p -> p.stanceTimeMs?.let { (it * DECI).roundToLong() } },
                VERTICAL_RATIO to { p -> p.verticalRatioPercent?.let { (it * CENTI).roundToLong() } },
                STANCE_TIME_BALANCE to { p -> p.stanceTimeBalancePercent?.let { (it * CENTI).roundToLong() } },
                STEP_LENGTH to { p -> p.stepLengthMm?.let { (it * DECI).roundToLong() } },
                ENHANCED_RESPIRATION_RATE to { p -> p.respirationRate?.let { (it * CENTI).roundToLong() } },
                PO2 to { p -> p.ppO2Bar?.let { (it * CENTI).roundToLong() } },
                CNS_LOAD to { p -> p.cnsPercent?.toLong() },
                N2_LOAD to { p -> p.n2LoadPercent?.toLong() },
                NDL_TIME to { p -> p.ndlSeconds },
                NEXT_STOP_DEPTH to { p -> p.nextStopDepthMeters?.let { (it * MILLI).roundToLong() } },
                NEXT_STOP_TIME to { p -> p.nextStopSeconds },
                TIME_TO_SURFACE to { p -> p.ttsSeconds },
            )
        }

    /**
     * Each change as a rear gear change when its rear gear differs from the one before (the first change's rear gear counts as a change),
     * else a front one. Both gear numbers go in the event's packed data; teeth stay zero (invalid): the app doesn't store them.
     */
    private fun writeGearChanges(out: FitOutput, changes: List<GearChange>, start: Long) =
        with(FitProfile.EventMessage) {
            var rear: Int? = null
            for (change in changes) {
                val event = if (change.rear != null && change.rear != rear) REAR_GEAR_CHANGE else FRONT_GEAR_CHANGE
                rear = change.rear ?: rear
                val data = (change.rear ?: 0).toLong() or ((change.front ?: 0).toLong() shl FRONT_GEAR_SHIFT)
                out.write(
                    FitMessage(
                        NUM,
                        listOf(TIMESTAMP(start + change.time), EVENT(event), EVENT_TYPE(TYPE_MARKER), DATA(data), EVENT_GROUP(0)),
                    )
                )
            }
        }

    private fun writeDive(out: FitOutput, dive: ActivityDive, activity: ExportActivity, start: Long, end: Long) {
        with(FitProfile.DiveSettings) {
            out.write(
                FitMessage(
                    NUM,
                    listOf(
                        TIMESTAMP(start),
                        MESSAGE_INDEX(0),
                        GF_LOW(dive.gradientFactorLow?.toLong()),
                        GF_HIGH(dive.gradientFactorHigh?.toLong()),
                        WATER_TYPE(dive.waterType?.let(WATER_TYPES::get)),
                        WATER_DENSITY(dive.waterDensity?.let { FitBaseType.floatBits(it.toDouble()) }),
                    ),
                )
            )
        }
        writeGases(out, dive, activity, start)
        writeTankUpdates(out, activity, start)
        with(FitProfile.TankSummary) {
            dive.tanks.orEmpty().forEachIndexed { index, tank ->
                out.write(
                    FitMessage(
                        NUM,
                        listOf(
                            TIMESTAMP(end),
                            SENSOR(sensorOf(index)),
                            // Millibar to bar × 100.
                            START_PRESSURE(tank.startPressure?.let { it / MBAR_PER_HUNDREDTH_BAR }),
                            END_PRESSURE(tank.endPressure?.let { it / MBAR_PER_HUNDREDTH_BAR }),
                            VOLUME_USED(tank.volumeUsed?.let { it * CENTI.toLong() }),
                        ),
                    )
                )
            }
        }
        out.write(diveSummary(dive, end, activity.elapsed.inWholeSeconds))
    }

    /**
     * A slot per gas breathed, in order of first use, then any gas the switches name that the record doesn't (merged from another file),
     * and a `dive_gas_switched` event per switch naming its slot.
     */
    private fun writeGases(out: FitOutput, dive: ActivityDive, activity: ExportActivity, start: Long) {
        val slots = (dive.gases.orEmpty().map { it.oxygen to it.helium } + activity.gasSwitches.map { it.oxygen to it.helium }).distinct()
        with(FitProfile.DiveGas) {
            val mode = if (dive.closedCircuit == true) MODE_CLOSED_CIRCUIT_DILUENT else MODE_OPEN_CIRCUIT
            slots.forEachIndexed { index, (oxygen, helium) ->
                out.write(
                    FitMessage(
                        NUM,
                        listOf(
                            MESSAGE_INDEX(index.toLong()),
                            HELIUM_CONTENT(helium.toLong()),
                            OXYGEN_CONTENT(oxygen.toLong()),
                            STATUS(STATUS_ENABLED),
                            MODE(mode),
                        ),
                    )
                )
            }
        }
        with(FitProfile.EventMessage) {
            for (switch in activity.gasSwitches) {
                val slot = slots.indexOf(switch.oxygen to switch.helium).toLong()
                out.write(
                    FitMessage(
                        NUM,
                        listOf(
                            TIMESTAMP(start + switch.time),
                            EVENT(DIVE_GAS_SWITCHED),
                            EVENT_TYPE(TYPE_MARKER),
                            DATA(slot),
                            EVENT_GROUP(0),
                        ),
                    )
                )
            }
        }
    }

    /** A `tank_update` for each tank at each second it has a reading, the tanks told apart by a sensor id of their own. */
    private fun writeTankUpdates(out: FitOutput, activity: ExportActivity, start: Long) =
        with(FitProfile.TankUpdate) {
            fun second(point: ExportPoint) = ((point.time - activity.startedAt).inWholeMilliseconds / MILLIS_PER_SECOND).roundToLong()
            for (point in activity.points.distinctBy(::second)) {
                point.tankPressuresBar.forEachIndexed { index, bar ->
                    if (bar != null) {
                        out.write(
                            FitMessage(
                                NUM,
                                listOf(TIMESTAMP(start + second(point)), SENSOR(sensorOf(index)), PRESSURE((bar * CENTI).roundToLong())),
                            )
                        )
                    }
                }
            }
        }

    /** The app keeps no transmitter ids, so tank n (from 0) is sensor n + 1 (FIT's sensor id can't be 0). */
    private fun sensorOf(tank: Int) = tank + 1L

    private val WATER_TYPES =
        with(FitProfile.DiveSettings) {
            mapOf(
                WaterType.FRESH to WATER_FRESH,
                WaterType.SALT to WATER_SALT,
                WaterType.EN13319 to WATER_EN13319,
                WaterType.CUSTOM to WATER_CUSTOM,
            )
        }

    /**
     * Garmin Connect shows `bottom_time` as the dive time (and the time out), so a dive the app stores without one (a logbook import)
     * writes its elapsed time there rather than leave Connect with no dive time.
     */
    private fun diveSummary(dive: ActivityDive, end: Long, elapsedSeconds: Long) =
        with(FitProfile.DiveSummary) {
            FitMessage(
                NUM,
                listOf(
                    TIMESTAMP(end),
                    REFERENCE_MESG(FitProfile.Session.num.toLong()),
                    REFERENCE_INDEX(0),
                    // Centimeters to meters × 1000.
                    AVG_DEPTH(dive.avgDepth?.let { it * MM_PER_CM }),
                    MAX_DEPTH(dive.maxDepth?.let { it * MM_PER_CM }),
                    SURFACE_INTERVAL(dive.surfaceInterval),
                    START_CNS(dive.startCns?.toLong()),
                    END_CNS(dive.endCns?.toLong()),
                    O2_TOXICITY(dive.otu?.toLong()),
                    DIVE_NUMBER(dive.diveNumber?.toLong()),
                    BOTTOM_TIME((dive.bottomTime ?: elapsedSeconds) * MILLI.toLong()),
                    START_N2(dive.startN2?.toLong()),
                    END_N2(dive.endN2?.toLong()),
                    DESCENT_TIME(dive.descentTime?.let { it * MILLI.toLong() }),
                    AVG_ASCENT_RATE(dive.avgAscentRate),
                    AVG_DESCENT_RATE(dive.avgDescentRate),
                    MAX_ASCENT_RATE(dive.maxAscentRate),
                    MAX_DESCENT_RATE(dive.maxDescentRate),
                ),
            )
        }

    /**
     * A lap spanning the activity or the session, which share every field we write at different field numbers. The session alone carries
     * shift counts, jumps and, as only the owner exports, the training load.
     */
    private fun summary(message: FitProfile.Summary, activity: ExportActivity, start: Long, end: Long): FitMessage {
        val sport = FitSports.of(ActivityType(activity.sport, activity.subSport))
        val distance = activity.distanceMeters ?: activity.points.asReversed().firstNotNullOfOrNull { it.distanceMeters }
        val totals = activity.totals
        fun speed(mps: Double?) = mps?.let { (it * MILLI).roundToLong() }
        return with(message) {
            FitMessage(
                num,
                listOf(
                    MESSAGE_INDEX(0),
                    TIMESTAMP(end),
                    FitProfile.EVENT(event),
                    FitProfile.EVENT_TYPE(FitProfile.EventMessage.TYPE_STOP),
                    FitProfile.START_TIME(start),
                    sport(sport.sport.toLong()),
                    subSport(sport.subSport.toLong()),
                    FitProfile.TOTAL_ELAPSED_TIME(activity.elapsed.inWholeMilliseconds),
                    FitProfile.TOTAL_TIMER_TIME((activity.moving ?: activity.elapsed).inWholeMilliseconds),
                    FitProfile.TOTAL_DISTANCE(distance?.let { (it * CENTI).roundToLong() }),
                    avgHeartRate(activity.avgHeartRate?.toLong()),
                    maxHeartRate(activity.maxHeartRate?.toLong()),
                    totalAscent(activity.elevationGainMeters?.roundToLong()),
                    avgTemperature(activity.avgTemperatureCelsius?.roundToLong()),
                    enhancedAvgSpeed(speed(activity.avgSpeedMps)),
                    enhancedMaxSpeed(speed(activity.maxSpeedMps)),
                    totalCalories(totals.calories?.toLong()),
                    avgCadence(totals.avgCadence?.toLong()),
                    maxCadence(totals.maxCadence?.toLong()),
                    avgPower(totals.avgPower?.toLong()),
                    maxPower(totals.maxPower?.toLong()),
                    normalizedPower(totals.normalizedPower?.toLong()),
                    totalWork(totals.work?.let { it * J_PER_KJ }),
                    totalDescent(totals.elevationLossMeters),
                ) +
                    trailer(activity.laps.size.coerceAtLeast(1)) +
                    if (message == FitProfile.Session) sessionOnly(activity) else emptyList(),
            )
        }
    }

    private fun sessionOnly(activity: ExportActivity): List<FitField> =
        with(FitProfile.Session) {
            val load = activity.trainingLoad
            listOf(
                FRONT_GEAR_SHIFT_COUNT(activity.totals.frontShifts?.toLong()),
                REAR_GEAR_SHIFT_COUNT(activity.totals.rearShifts?.toLong()),
                JUMP_COUNT(activity.totals.jumps?.toLong()),
                THRESHOLD_POWER(load?.ftp?.toLong()),
                // Hundredths to thousandths.
                INTENSITY_FACTOR(load?.intensityFactor?.let { it * DECI.toLong() }),
                // Both tenths, as the lexicon keeps them.
                TRAINING_STRESS_SCORE(load?.trainingStressScore?.toLong()),
                TOTAL_TRAINING_EFFECT(load?.aerobicTrainingEffect?.toLong()),
                TOTAL_ANAEROBIC_TRAINING_EFFECT(load?.anaerobicTrainingEffect?.toLong()),
            )
        }

    /** A lap as the streams store it, [index] in order, triggered by hand as far as the file knows. */
    private fun storedLap(index: Int, lap: Lap, start: Long): FitMessage =
        with(FitProfile.Lap) {
            val lapStart = start + lap.start
            FitMessage(
                num,
                listOf(
                    MESSAGE_INDEX(index.toLong()),
                    TIMESTAMP(lapStart + lap.elapsed),
                    FitProfile.EVENT(event),
                    FitProfile.EVENT_TYPE(FitProfile.EventMessage.TYPE_STOP),
                    FitProfile.START_TIME(lapStart),
                    FitProfile.TOTAL_ELAPSED_TIME(lap.elapsed * MILLI.toLong()),
                    FitProfile.TOTAL_TIMER_TIME((lap.moving ?: lap.elapsed) * MILLI.toLong()),
                    FitProfile.TOTAL_DISTANCE(lap.distance?.let { it * CENTI.toLong() }),
                    avgHeartRate(lap.avgHeartRate?.toLong()),
                    maxHeartRate(lap.maxHeartRate?.toLong()),
                    totalAscent(lap.ascent?.toLong()),
                    totalDescent(lap.descent?.toLong()),
                    enhancedAvgSpeed(lap.avgSpeed?.toLong()),
                    enhancedMaxSpeed(lap.maxSpeed?.toLong()),
                    avgPower(lap.avgPower?.toLong()),
                    avgCadence(lap.avgCadence?.toLong()),
                    MANUAL_TRIGGER,
                ),
            )
        }

    private fun activityMessage(activity: ExportActivity, end: Long, zone: TimeZone) =
        with(FitProfile.Activity) {
            val endInstant = activity.startedAt + activity.elapsed
            FitMessage(
                NUM,
                listOf(
                    TIMESTAMP(end),
                    TOTAL_TIMER_TIME((activity.moving ?: activity.elapsed).inWholeMilliseconds),
                    NUM_SESSIONS(1),
                    TYPE(TYPE_MANUAL),
                    EVENT(FitProfile.EventMessage.ACTIVITY),
                    EVENT_TYPE(FitProfile.EventMessage.TYPE_STOP),
                    LOCAL_TIMESTAMP(end + zone.offsetAt(endInstant).totalSeconds),
                ),
            )
        }

    /** Degrees to FIT's semicircles: 2^31 semicircles = 180°. */
    private fun semicircles(degrees: Double): Long = (degrees * SEMICIRCLES_PER_DEGREE).roundToLong()

    /** The app's version in `file_creator`, which tools show as the creating software. */
    private const val SOFTWARE_VERSION = 1L
    private const val MILLIS_PER_SECOND = 1000.0
    private const val DECI = 10.0
    private const val CENTI = 100.0
    private const val MILLI = 1000.0
    private const val J_PER_KJ = 1000L
    private const val MM_PER_CM = 10L
    private const val MBAR_PER_HUNDREDTH_BAR = 10L
    private const val SEMICIRCLES_PER_DEGREE = 2_147_483_648.0 / 180.0
}
