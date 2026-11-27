package io.github.solcott.activitytracker.data.activity.trim

import io.github.solcott.activitytracker.data.activity.ActivityDraft
import io.github.solcott.activitytracker.lexicon.app.ActivityRecord
import io.github.solcott.activitytracker.lexicon.app.ActivityStreamData
import io.github.solcott.activitytracker.lexicon.app.Lap
import io.github.solcott.activitytracker.lexicon.app.TrainingLoad
import io.github.solcott.activitytracker.location.LocationSample
import kotlin.math.roundToInt
import kotlin.math.roundToLong
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/**
 * Cuts an activity to a [KeptSpan] (feed/activity-trim), as if it had been recorded over that span only. The start moves to the span's
 * start and every time is re-based to it; samples, points, lengths and laps outside it go, and a lap across a boundary is cut there.
 * Distance, moving time, gain and loss are the original less what was cut ([TrimCut]); averages and maxima come from what's kept
 * ([KeptStats]); totals only a device can measure (calories, training load) scale with the kept share of moving time. The title,
 * description, photos, gear, sport, devices, visibility and source stay as they are.
 */
object ActivityTrimmer {
    /** [data] over [span] only, its times re-based to the span's start. */
    fun streams(data: ActivityStreamData, span: KeptSpan): ActivityStreamData {
        val keep = data.time.indices.filter { data.time[it] in span.from..span.to }
        fun <T> List<T?>.kept(): List<T?> = keep.map { getOrNull(it) }
        fun moved(t: Int) = t - span.from
        fun within(start: Int, elapsed: Int) = start >= span.from && start + elapsed <= span.to
        val base =
            data.copy(
                time = keep.map { moved(data.time[it]) },
                gears = data.gears?.filter { it.time in span.from..span.to }?.map { it.copy(time = moved(it.time)) },
                gasSwitches =
                    data.gasSwitches?.let { switches ->
                        // The gas breathed at the new start is a switch at 0.
                        val breathing = switches.lastOrNull { it.time <= span.from }?.copy(time = 0)
                        listOfNotNull(breathing) +
                            switches.filter { it.time > span.from && it.time <= span.to }.map { it.copy(time = moved(it.time)) }
                    },
                diveAlerts = data.diveAlerts?.filter { it.time in span.from..span.to }?.map { it.copy(time = moved(it.time)) },
                tankPressures = data.tankPressures?.map { it.copy(pressure = it.pressure.kept()) },
                lengths = data.lengths?.filter { within(it.start, it.elapsed) }?.map { it.copy(start = moved(it.start)) },
                sets = data.sets?.filter { within(it.start, it.elapsed) }?.map { it.copy(start = moved(it.start)) },
                legs = data.legs?.filter { within(it.start, it.elapsed) }?.map { it.copy(start = moved(it.start)) },
                laps = null,
            )
        val sliced = ActivityStreamData.INT_CHANNELS.fold(base) { acc, channel -> channel.with(acc, channel.get(data)?.kept()) }
        return sliced.copy(laps = data.laps?.let { laps(it, span, sliced) })
    }

    /** [route]'s points within [span] of [startedAt]. */
    fun route(route: List<LocationSample>, startedAt: Instant, span: KeptSpan): List<LocationSample> {
        val from = startedAt + span.from.seconds
        val to = startedAt + span.to.seconds
        return route.filter { it.time in from..to }
    }

    /** [record], described by [input], cut to [span]. Its polyline is left for the rewrite to build from the trimmed route. */
    @Suppress("CyclomaticComplexMethod") // One rule per total, each kept when nothing measures it.
    fun record(record: ActivityRecord, input: TrimInput, span: KeptSpan): ActivityRecord {
        if (span.isWhole(input)) return record
        val cut = TrimCut.of(input, span)
        val kept = input.streams?.let { KeptStats.of(streams(it, span)) }
        val elapsed = span.keptElapsed(input).toLong()
        val moving = record.movingTime?.let { cut.keptMoving(it.toDouble()).roundToLong() }
        val distance = record.distance?.let { d -> cut.distance?.let { (d - it).roundToLong().coerceAtLeast(0) } ?: d }
        val share = cut.share(record.movingTime?.toDouble())
        fun Int.scaled() = (this * share).roundToInt()
        return record.copy(
            startedAt = record.startedAt + span.from.seconds,
            elapsedTime = elapsed,
            movingTime = moving,
            distance = distance,
            elevationGain = record.elevationGain?.let { (it - cut.gain).roundToLong().coerceAtLeast(0) },
            elevationLoss = record.elevationLoss?.let { (it - cut.loss).roundToLong().coerceAtLeast(0) },
            avgSpeed = if (distance != null && moving != null && moving > 0) distance * MM_PER_M / moving else record.avgSpeed,
            maxSpeed = kept?.maxSpeed?.toLong() ?: record.maxSpeed,
            avgHeartRate = kept?.avgHeartRate ?: record.avgHeartRate,
            maxHeartRate = kept?.maxHeartRate ?: record.maxHeartRate,
            avgTemperature = kept?.avgTemperature?.toLong() ?: record.avgTemperature,
            avgCadence = kept?.avgCadence ?: record.avgCadence,
            maxCadence = kept?.maxCadence ?: record.maxCadence,
            avgPower = kept?.avgPower ?: record.avgPower,
            maxPower = kept?.maxPower ?: record.maxPower,
            normalizedPower = kept?.normalizedPower ?: record.normalizedPower,
            work = kept?.workJoules?.let { (it / J_PER_KJ).roundToLong() } ?: record.work?.let { (it * share).roundToLong() },
            calories = record.calories?.scaled(),
            frontShifts = record.frontShifts?.scaled(),
            rearShifts = record.rearShifts?.scaled(),
            jumps = record.jumps?.scaled(),
            grit = record.grit?.scaled(),
            cycles = record.cycles?.scaled(),
        )
    }

    /** [draft] cut to [span]: its route, track, streams and totals, ready to publish. Its source file stays whole. */
    @Suppress("CyclomaticComplexMethod") // One rule per total, each kept when nothing measures it.
    fun draft(draft: ActivityDraft, span: KeptSpan): ActivityDraft {
        val input = draft.trimInput()
        if (span.isWhole(input)) return draft
        val cut = TrimCut.of(input, span)
        val streams = draft.streams?.let { streams(it, span) }
        val kept = streams?.let { KeptStats.of(it) }
        val route = route(draft.route, draft.startedAt, span)
        val moving = draft.movingSeconds?.let { cut.keptMoving(it.toDouble()).roundToLong() }
        val share = cut.share(draft.movingSeconds?.toDouble())
        fun Int.scaled() = (this * share).roundToInt()
        return draft.copy(
            startedAt = draft.startedAt + span.from.seconds,
            elapsed = span.keptElapsed(input).seconds,
            movingSeconds = moving,
            distanceMeters = draft.distanceMeters?.let { d -> cut.distance?.let { (d - it).coerceAtLeast(0.0) } ?: d },
            elevationGainMeters = draft.elevationGainMeters?.let { (it - cut.gain).coerceAtLeast(0.0) },
            elevationLossMeters = draft.elevationLossMeters?.let { (it - cut.loss).coerceAtLeast(0.0) },
            maxSpeedMps = kept?.maxSpeed?.let { it / MM_PER_M.toDouble() } ?: draft.maxSpeedMps,
            avgTemperatureCelsius = kept?.avgTemperature?.let { it / TENTHS } ?: draft.avgTemperatureCelsius,
            avgHeartRate = kept?.avgHeartRate ?: draft.avgHeartRate,
            maxHeartRate = kept?.maxHeartRate ?: draft.maxHeartRate,
            avgCadence = kept?.avgCadence ?: draft.avgCadence,
            maxCadence = kept?.maxCadence ?: draft.maxCadence,
            avgPowerWatts = kept?.avgPower ?: draft.avgPowerWatts,
            maxPowerWatts = kept?.maxPower ?: draft.maxPowerWatts,
            normalizedPowerWatts = kept?.normalizedPower ?: draft.normalizedPowerWatts,
            workJoules = kept?.workJoules ?: draft.workJoules?.let { it * share },
            calories = draft.calories?.scaled(),
            frontShifts = draft.frontShifts?.scaled(),
            rearShifts = draft.rearShifts?.scaled(),
            jumps = draft.jumps?.scaled(),
            grit = draft.grit?.let { it * share },
            cycles = draft.cycles?.scaled(),
            trainingLoad = draft.trainingLoad?.let { trainingLoad(it, input, span, draft.movingSeconds) },
            route = route,
            gpx = if (draft.route.isEmpty()) draft.gpx else ActivityDraft.gpxOf(route, draft.title, draft.type.sport),
            streams = streams,
        )
    }

    /**
     * [load], the device's training load for the activity [input] describes, cut to [span]: its stress score and exercise load scale with
     * the kept share of [movingSeconds], and the rest (thresholds, training effect, stamina) stay as the device gave them.
     */
    fun trainingLoad(load: TrainingLoad, input: TrimInput, span: KeptSpan, movingSeconds: Long?): TrainingLoad {
        if (span.isWhole(input)) return load
        val share = TrimCut.of(input, span).share(movingSeconds?.toDouble())
        return load.copy(
            trainingStressScore = load.trainingStressScore?.let { (it * share).roundToInt() },
            exerciseLoad = load.exerciseLoad?.let { (it * share).roundToInt() },
        )
    }

    /** [laps] within [span], re-based; a lap across a boundary is cut there and its figures taken from [kept], the trimmed streams. */
    private fun laps(laps: List<Lap>, span: KeptSpan, kept: ActivityStreamData): List<Lap>? {
        val series = TrimSeries.of(TrimInput(Instant.fromEpochSeconds(0), span.seconds, kept, emptyList()))
        return laps
            .mapNotNull { lap ->
                val start = maxOf(lap.start, span.from)
                val end = minOf(lap.start + lap.elapsed, span.to)
                if (end <= start) return@mapNotNull null
                val moved = lap.copy(start = start - span.from, elapsed = end - start)
                if (start == lap.start && end == lap.start + lap.elapsed) moved else cutLap(lap, moved, series, kept)
            }
            .ifEmpty { null }
    }

    @Suppress("CyclomaticComplexMethod") // One rule per lap figure, each kept when nothing measures it.
    private fun cutLap(original: Lap, lap: Lap, series: TrimSeries?, kept: ActivityStreamData): Lap {
        val from = lap.start
        val to = lap.start + lap.elapsed
        val stats = KeptStats.of(kept, from, to)
        val share = if (original.elapsed > 0) lap.elapsed.toDouble() / original.elapsed else 1.0
        val moving = series?.movingSeconds(from, to)?.roundToInt() ?: original.moving?.let { (it * share).roundToInt() }
        val distance =
            series?.distance?.let { ((series.distanceAt(to) ?: 0.0) - (series.distanceAt(from) ?: 0.0)).roundToInt() }
                ?: original.distance?.let { (it * share).roundToInt() }
        val climb = series?.gainLoss(from, to)
        return lap.copy(
            moving = moving,
            distance = distance,
            avgSpeed = if (distance != null && moving != null && moving > 0) (distance * MM_PER_M / moving).toInt() else original.avgSpeed,
            maxSpeed = stats.maxSpeed ?: original.maxSpeed,
            avgHeartRate = stats.avgHeartRate ?: original.avgHeartRate,
            maxHeartRate = stats.maxHeartRate ?: original.maxHeartRate,
            avgPower = stats.avgPower ?: original.avgPower,
            avgCadence = stats.avgCadence ?: original.avgCadence,
            ascent = climb?.gain?.toInt() ?: original.ascent?.let { (it * share).roundToInt() },
            descent = climb?.loss?.toInt() ?: original.descent?.let { (it * share).roundToInt() },
            calories = original.calories?.let { (it * share).roundToInt() },
        )
    }

    private const val MM_PER_M = 1000L
    private const val J_PER_KJ = 1000.0
    private const val TENTHS = 10.0
}

/** What a trim of this draft works over. */
fun ActivityDraft.trimInput(): TrimInput = TrimInput(startedAt, elapsed.inWholeSeconds.toInt(), streams, route)

/** This draft cut to [span] ([ActivityTrimmer.draft]). */
fun ActivityDraft.trimmed(span: KeptSpan): ActivityDraft = ActivityTrimmer.draft(this, span)
