package io.github.solcott.activitytracker.app.detail

import io.github.solcott.activitytracker.activityfile.DiveAlerts
import io.github.solcott.activitytracker.activityfile.PerSecond
import io.github.solcott.activitytracker.data.streams.StreamAnalytics
import io.github.solcott.activitytracker.data.training.ZoneModel
import io.github.solcott.activitytracker.data.training.ZoneModels
import io.github.solcott.activitytracker.data.training.ZoneSnapshots
import io.github.solcott.activitytracker.data.units.UnitConversions
import io.github.solcott.activitytracker.data.units.UnitSystem
import io.github.solcott.activitytracker.lexicon.app.ActivityStreamData
import io.github.solcott.activitytracker.lexicon.app.GetActivityStreams
import io.github.solcott.activitytracker.lexicon.app.Lap
import io.github.solcott.activitytracker.lexicon.app.Sport
import io.github.solcott.activitytracker.lexicon.app.TrainingLoad
import io.github.solcott.activitytracker.lexicon.app.ZoneSnapshot
import kotlin.math.roundToLong
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Works out an activity's [ActivityCharts] from its streams. Pure, so it runs off the main thread and tests directly. */
object ChartsBuilder {
    private const val CENTI = 100.0

    private const val DECI = 10.0
    private const val MILLI = 1000.0

    /** [build] on a background thread: a long ride has tens of thousands of samples. */
    @Suppress("InjectDispatcher") // Pure CPU work with nothing to swap in tests; presenters have no dispatcher to inject.
    suspend fun buildOffMain(streams: GetActivityStreams.Output, units: UnitSystem): ActivityCharts =
        withContext(Dispatchers.Default) { build(streams, units) }

    /** [units] are the viewer's, for the lap table. */
    fun build(streams: GetActivityStreams.Output, units: UnitSystem = UnitSystem.Metric): ActivityCharts {
        val data = streams.data
        val time = data.time
        val power = data.power
        val powerBySecond = power?.let { StreamAnalytics.perSecond(time, it) }.orEmpty()
        return ActivityCharts(
            visibility = streams.visibility,
            durationSeconds = time.lastOrNull() ?: 0,
            lines = lines(data).toImmutableList(),
            heartRateZones = data.heartRate?.let { zoneTimes(time, it, streams.heartRateZones, ZoneModels.heartRate) },
            powerZones = power?.let { zoneTimes(time, it, streams.powerZones, ZoneModels.power) },
            normalizedPower = PerSecond.normalizedPower(powerBySecond),
            // Over clock time, as Garmin reads it: a pause counts at 0 W, so no window joins the efforts either side of a stop.
            powerCurve =
                power
                    ?.let { StreamAnalytics.meanMax(StreamAnalytics.clockSeconds(time, it)) }
                    .orEmpty()
                    .map { (seconds, watts) -> CurvePoint(seconds, watts) }
                    .toImmutableList(),
            gears = data.gears.orEmpty().map { GearPoint(it.time, it.front, it.rear) }.toImmutableList(),
            gasSwitches = data.gasSwitches.orEmpty().map { GasSwitchPoint(it.time, it.oxygen, it.helium) }.toImmutableList(),
            alerts =
                data.diveAlerts
                    .orEmpty()
                    .filter { DiveAlerts.isWarning(it.kind) }
                    .sortedBy { it.time }
                    .map { DiveAlertPoint(it.time, it.kind) }
                    .toImmutableList(),
            ceiling = ceiling(data),
            tankPressures =
                data.tankPressures
                    .orEmpty()
                    .mapNotNull { tank -> line(StreamKind.TankPressure, time, tank.pressure.map { it?.div(MILLI) }) }
                    .toImmutableList(),
            laps = data.laps.orEmpty().mapIndexed { i, lap -> lapRow(i + 1, lap, units) }.toImmutableList(),
            trainingLoad = streams.trainingLoad?.let(::trainingLoad),
            lengths = StructureRows.lengths(data.lengths.orEmpty()),
            sets = StructureRows.sets(data.sets.orEmpty()),
            legs = StructureRows.legs(data.legs.orEmpty()),
        )
    }

    /** [charts] for an activity of [sport]: a ski or snowboard day counts its runs, the laps that aren't lift rides. */
    fun forSport(charts: ActivityCharts, sport: String): ActivityCharts =
        if ((sport == Sport.SKI || sport == Sport.SNOWBOARD) && charts.laps.isNotEmpty()) {
            charts.copy(skiRuns = charts.laps.count { !it.isRest })
        } else {
            charts
        }

    /** Lines in a fixed order: the everyday metrics, then the rarer device metrics, then a dive's. */
    private fun lines(data: ActivityStreamData): List<LineSeries> {
        val distance = data.distance
        val altitude = data.altitude
        val grade = if (distance != null && altitude != null) StreamAnalytics.grade(distance, altitude) else null
        fun ints(values: List<Int?>?, scale: Double = 1.0) = values?.map { it?.div(scale) }
        return listOfNotNull(
            line(StreamKind.HeartRate, data.time, ints(data.heartRate)),
            line(StreamKind.Power, data.time, ints(data.power)),
            line(StreamKind.LeftRightBalance, data.time, ints(data.leftRightBalance)),
            line(StreamKind.Speed, data.time, ints(data.speed)),
            line(StreamKind.Cadence, data.time, ints(data.cadence)),
            line(StreamKind.Elevation, data.time, ints(data.altitude, CENTI)),
            line(StreamKind.Grade, data.time, grade),
            line(StreamKind.Temperature, data.time, ints(data.temperature)),
            line(StreamKind.RespirationRate, data.time, ints(data.respirationRate, DECI)),
            line(StreamKind.VerticalOscillation, data.time, ints(data.verticalOscillation)),
            line(StreamKind.StanceTime, data.time, ints(data.stanceTime)),
            line(StreamKind.StanceTimeBalance, data.time, ints(data.stanceTimeBalance, CENTI)),
            line(StreamKind.StepLength, data.time, ints(data.stepLength, MILLI)),
            line(StreamKind.VerticalRatio, data.time, ints(data.verticalRatio, CENTI)),
            line(StreamKind.Depth, data.time, ints(data.depth, CENTI)),
            line(StreamKind.PpO2, data.time, ints(data.ppO2, MILLI)),
            line(StreamKind.Cns, data.time, ints(data.cns)),
            pair(
                StreamKind.TorqueEffectiveness,
                data.time,
                ints(data.torqueEffectivenessLeft, CENTI),
                ints(data.torqueEffectivenessRight, CENTI),
            ),
            pair(StreamKind.PedalSmoothness, data.time, ints(data.pedalSmoothnessLeft, CENTI), ints(data.pedalSmoothnessRight, CENTI)),
            pair(StreamKind.PlatformCentreOffset, data.time, ints(data.pcoLeft), ints(data.pcoRight)),
            // Tenths of a degree, as the temperature line takes them.
            line(StreamKind.CoreTemperature, data.time, ints(data.coreTemperature, DECI)),
            line(StreamKind.SmO2, data.time, ints(data.smO2, DECI)),
            line(StreamKind.TotalHemoglobin, data.time, ints(data.totalHemoglobin, CENTI)),
            line(StreamKind.FormPower, data.time, ints(data.formPower)),
            line(StreamKind.LegSpringStiffness, data.time, ints(data.legSpringStiffness, CENTI)),
            line(StreamKind.EbikeBattery, data.time, ints(data.ebikeBattery)),
            line(StreamKind.EbikeAssistMode, data.time, ints(data.ebikeAssistMode)),
            line(StreamKind.EbikeAssistLevel, data.time, ints(data.ebikeAssistLevel)),
        )
    }

    /** The left leg's line with the right's as its [LineSeries.right]; the right alone when the left is missing. */
    private fun pair(kind: StreamKind, time: List<Int>, left: List<Double?>?, right: List<Double?>?): LineSeries? {
        val leftLine = line(kind, time, left)
        val rightLine = line(kind, time, right)
        return leftLine?.copy(right = rightLine) ?: rightLine
    }

    /** The deco stop's depth, in meters, wherever it's set: a gap in the data is a gap in the line. */
    private fun ceiling(data: ActivityStreamData): ImmutableList<ChartSample> {
        val stops = data.decoStopDepth?.takeIf { depths -> depths.any { (it ?: 0) > 0 } } ?: return persistentListOf()
        return StreamAnalytics.averaged(data.time, stops.map { it?.div(CENTI) })
            .map { (seconds, value, gapBefore) -> ChartSample(seconds, value, gapBefore) }
            .toImmutableList()
    }

    private fun lapRow(number: Int, lap: Lap, units: UnitSystem) =
        LapRow(
            number = number,
            elapsedSeconds = lap.elapsed,
            movingSeconds = lap.moving,
            distance = lap.distance?.let { UnitConversions.toDistanceUnit(it.toDouble(), units) },
            // TCX laps carry speed only in the optional LX extension, so without it derive mm/s from distance and time.
            speed =
                (lap.avgSpeed?.toLong() ?: lap.distance?.let { (it * MILLI / (lap.moving ?: lap.elapsed).coerceAtLeast(1)).roundToLong() })
                    ?.let { UnitConversions.toSpeedUnit(it, units) },
            avgHeartRate = lap.avgHeartRate,
            avgPower = lap.avgPower,
            avgCadence = lap.avgCadence,
            ascent = lap.ascent?.let { UnitConversions.toElevationUnit(it.toDouble(), units) },
            intensity = lap.intensity,
            descent = lap.descent?.let { UnitConversions.toElevationUnit(it.toDouble(), units) },
        )

    private fun trainingLoad(load: TrainingLoad) =
        TrainingLoadStats(
            ftp = load.ftp,
            intensityFactor = load.intensityFactor?.div(CENTI),
            trainingStressScore = load.trainingStressScore?.div(DECI),
            aerobicTrainingEffect = load.aerobicTrainingEffect?.div(DECI),
            anaerobicTrainingEffect = load.anaerobicTrainingEffect?.div(DECI),
            exerciseLoad = load.exerciseLoad,
            staminaStart = load.staminaStart,
            staminaEnd = load.staminaEnd,
            staminaMin = load.staminaMin,
            sweatLossMl = load.sweatLoss,
            hrvRmssd = load.hrvRmssd,
        )

    private fun line(kind: StreamKind, time: List<Int>, values: List<Double?>?): LineSeries? {
        val present = values?.filterNotNull()?.takeIf { it.size >= 2 } ?: return null
        val points = StreamAnalytics.averaged(time, values).map { (seconds, value, gapBefore) -> ChartSample(seconds, value, gapBefore) }
        // Averaged over time, grade weighs slow climbs over fast descents, so a loop reads as uphill: it gives its maximum only.
        val average = present.average().takeUnless { kind == StreamKind.Grade }
        return LineSeries(kind, points.toImmutableList(), average, present.max(), min = present.min())
    }

    /**
     * Null when there's no snapshot (the athlete hadn't set thresholds, or the viewer may not see them) or it's a model this client lacks.
     */
    private fun zoneTimes(time: List<Int>, values: List<Int?>, snapshot: ZoneSnapshot?, models: List<ZoneModel>): ImmutableList<ZoneTime>? {
        val zones = snapshot?.let { ZoneSnapshots.zones(it, models) } ?: return null
        val seconds = StreamAnalytics.timeInZones(time, values, zones.map { it.low })
        return zones.mapIndexed { i, zone -> ZoneTime(zone.label, zone.name, zone.low, zone.high, seconds[i]) }.toImmutableList()
    }
}
