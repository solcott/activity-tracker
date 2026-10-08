package io.github.solcott.activitytracker.app.detail

import io.github.solcott.activitytracker.activityfile.PerSecond
import io.github.solcott.activitytracker.data.streams.StreamAnalytics
import io.github.solcott.activitytracker.data.training.ZoneModel
import io.github.solcott.activitytracker.data.training.ZoneModels
import io.github.solcott.activitytracker.data.training.ZoneSnapshots
import io.github.solcott.activitytracker.lexicon.app.ActivityStreamData
import io.github.solcott.activitytracker.lexicon.app.GetActivityStreams
import io.github.solcott.activitytracker.lexicon.app.ZoneSnapshot
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Works out an activity's [ActivityCharts] from its streams. Pure, so it runs off the main thread and tests directly. */
internal object ChartsBuilder {
    private const val CENTI = 100.0

    /** [build] on a background thread: a long ride has tens of thousands of samples. */
    @Suppress("InjectDispatcher") // Pure CPU work with nothing to swap in tests; presenters have no dispatcher to inject.
    suspend fun buildOffMain(streams: GetActivityStreams.Output): ActivityCharts = withContext(Dispatchers.Default) { build(streams) }

    fun build(streams: GetActivityStreams.Output): ActivityCharts {
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
            powerCurve = StreamAnalytics.meanMax(powerBySecond).map { (seconds, watts) -> CurvePoint(seconds, watts) }.toImmutableList(),
            gears = data.gears.orEmpty().map { GearPoint(it.time, it.front, it.rear) }.toImmutableList(),
        )
    }

    /** Lines in the order Strava draws them, then Garmin's extras. */
    private fun lines(data: ActivityStreamData): List<LineSeries> {
        val distance = data.distance
        val altitude = data.altitude
        val grade = if (distance != null && altitude != null) StreamAnalytics.grade(distance, altitude) else null
        return listOfNotNull(
            line(StreamKind.HeartRate, data.time, data.heartRate?.map { it?.toDouble() }),
            line(StreamKind.Power, data.time, data.power?.map { it?.toDouble() }),
            line(StreamKind.Speed, data.time, data.speed?.map { it?.toDouble() }),
            line(StreamKind.Cadence, data.time, data.cadence?.map { it?.toDouble() }),
            line(StreamKind.Elevation, data.time, data.altitude?.map { it?.div(CENTI) }),
            line(StreamKind.Grade, data.time, grade),
            line(StreamKind.Temperature, data.time, data.temperature?.map { it?.toDouble() }),
            line(StreamKind.Depth, data.time, data.depth?.map { it?.div(CENTI) }),
        )
    }

    private fun line(kind: StreamKind, time: List<Int>, values: List<Double?>?): LineSeries? {
        val present = values?.filterNotNull()?.takeIf { it.size >= 2 } ?: return null
        val points = StreamAnalytics.averaged(time, values).map { (seconds, value) -> ChartSample(seconds, value) }
        return LineSeries(kind, points.toImmutableList(), present.average(), present.max())
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
