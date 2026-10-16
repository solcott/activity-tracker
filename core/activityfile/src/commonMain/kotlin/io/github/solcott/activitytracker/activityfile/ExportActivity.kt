package io.github.solcott.activitytracker.activityfile

import io.github.solcott.activitytracker.lexicon.app.ActivityDive
import io.github.solcott.activitytracker.lexicon.app.ActivityRecord
import io.github.solcott.activitytracker.lexicon.app.ActivityStreamData
import io.github.solcott.activitytracker.lexicon.app.GasSwitch
import io.github.solcott.activitytracker.lexicon.app.GearChange
import io.github.solcott.activitytracker.lexicon.app.Lap
import io.github.solcott.activitytracker.lexicon.app.TrainingLoad
import io.github.solcott.activitytracker.location.LocationSample
import kotlin.math.roundToInt
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/** An activity to write out as a file, rebuilt from what the app stores, whatever the format. Units are SI. */
data class ExportActivity(
    /** The track's name: the activity's title, or its sport's name. */
    val name: String,
    /** The lexicon sport ("run"), for GPX `<type>`. */
    val sport: String,
    val tcxSport: TcxSport,
    val startedAt: Instant,
    val elapsed: Duration,
    /** Time spent moving, which TCX's lap time is; null falls back to [elapsed]. */
    val moving: Duration? = null,
    val distanceMeters: Double?,
    val avgHeartRate: Int?,
    val maxHeartRate: Int?,
    /** A dive's temperatures are the water's, so GPX writes them as `wtemp` rather than `atemp`. */
    val isDive: Boolean,
    /** In time order. */
    val points: List<ExportPoint>,
    /** The lexicon sub-sport, which FIT's session names along with the sport. */
    val subSport: String? = null,
    val elevationGainMeters: Double? = null,
    val avgSpeedMps: Double? = null,
    val maxSpeedMps: Double? = null,
    val avgTemperatureCelsius: Double? = null,
    /** As the streams store them; only FIT writes them. */
    val gearChanges: List<GearChange> = emptyList(),
    /** As the record stores it, in lexicon units; only FIT writes it. */
    val dive: ActivityDive? = null,
    /** As the streams store them, in lexicon units; only FIT writes them. */
    val gasSwitches: List<GasSwitch> = emptyList(),
    val laps: List<Lap> = emptyList(),
    /** The record's power, cadence, calories, descent and shift counts, in lexicon units; only FIT writes them. */
    val totals: ExportTotals = ExportTotals(),
    /** From the zones record, which only the owner (and so only an export) reads in full; only FIT writes it. */
    val trainingLoad: TrainingLoad? = null,
)

/** The record's summary fields only FIT has a place for, in lexicon units. */
data class ExportTotals(
    val elevationLossMeters: Long? = null,
    val avgCadence: Int? = null,
    val maxCadence: Int? = null,
    val avgPower: Int? = null,
    val maxPower: Int? = null,
    val normalizedPower: Int? = null,
    /** Kilojoules. */
    val work: Long? = null,
    /** Kilocalories. */
    val calories: Int? = null,
    val frontShifts: Int? = null,
    val rearShifts: Int? = null,
    val jumps: Int? = null,
) {
    companion object {
        fun of(record: ActivityRecord) =
            ExportTotals(
                elevationLossMeters = record.elevationLoss,
                avgCadence = record.avgCadence,
                maxCadence = record.maxCadence,
                avgPower = record.avgPower,
                maxPower = record.maxPower,
                normalizedPower = record.normalizedPower,
                work = record.work,
                calories = record.calories,
                frontShifts = record.frontShifts,
                rearShifts = record.rearShifts,
                jumps = record.jumps,
            )
    }
}

/** TCX's `Sport` attribute, which knows only these three. */
enum class TcxSport(val xmlName: String) {
    Running("Running"),
    Biking("Biking"),
    Other("Other"),
}

/** One point of an exported file: a track point, a stream sample, or both joined on their second. */
data class ExportPoint(
    val time: Instant,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val altitudeMeters: Double? = null,
    /** From the start. */
    val distanceMeters: Double? = null,
    val speedMps: Double? = null,
    val heartRate: Int? = null,
    val cadence: Int? = null,
    val powerWatts: Int? = null,
    val temperatureCelsius: Double? = null,
    /** Below the surface, for dives. */
    val depthMeters: Double? = null,
    /** Breaths per minute. */
    val respirationRate: Double? = null,
    val verticalOscillationMm: Double? = null,
    val stanceTimeMs: Double? = null,
    val stepLengthMm: Double? = null,
    val verticalRatioPercent: Double? = null,
    val stanceTimeBalancePercent: Double? = null,
    /** Percent of power from the right leg. */
    val rightBalancePercent: Int? = null,
    val ppO2Bar: Double? = null,
    val cnsPercent: Int? = null,
    val n2LoadPercent: Int? = null,
    val ndlSeconds: Long? = null,
    /** The next deco stop; null outside deco. */
    val nextStopDepthMeters: Double? = null,
    val nextStopSeconds: Long? = null,
    val ttsSeconds: Long? = null,
    /** Each tank's pressure here, in the dive's tank order; null where a tank has no reading. Empty without tank pressure. */
    val tankPressuresBar: List<Double?> = emptyList(),
) {
    val hasPosition: Boolean
        get() = latitude != null && longitude != null
}

/** Joins an activity's stored track and streams into the points of an exported file. */
object ExportPoints {
    /**
     * Each track point takes the stream sample at its second (rounded from [startedAt]), so two points in one second both get it; a sample
     * at a second no track point has becomes a point of its own with no position. Units go back from the lexicon's integers to SI (the
     * reverse of [StreamBuilder]). Altitude is the stream's where it has one, else the track's GPS altitude. Gear changes, gas switches and
     * laps aren't points: [ExportActivity] carries them for FIT.
     */
    fun join(startedAt: Instant, track: List<LocationSample>?, streams: ActivityStreamData?): List<ExportPoint> {
        val bySecond = streams?.time?.withIndex()?.reversed()?.associate { (index, second) -> second to index }.orEmpty()
        val trackSeconds = mutableSetOf<Int>()
        val trackPoints =
            track.orEmpty().map { location ->
                val second = ((location.time - startedAt).inWholeMilliseconds / MILLIS_PER_SECOND).roundToInt()
                trackSeconds += second
                val sample = bySecond[second]?.let { streams?.pointAt(it, location.time) }
                (sample ?: ExportPoint(location.time)).copy(
                    latitude = location.latitude,
                    longitude = location.longitude,
                    altitudeMeters = sample?.altitudeMeters ?: location.altitude,
                )
            }
        val streamOnly =
            streams?.time.orEmpty().withIndex().mapNotNull { (index, second) ->
                if (second in trackSeconds) null else streams?.pointAt(index, startedAt + second.seconds)
            }
        return (trackPoints + streamOnly).sortedBy { it.time }
    }

    private fun ActivityStreamData.pointAt(i: Int, time: Instant) =
        ExportPoint(
            time = time,
            altitudeMeters = altitude.at(i)?.div(CENTI),
            distanceMeters = distance.at(i)?.div(CENTI),
            speedMps = speed.at(i)?.div(MILLI),
            heartRate = heartRate?.getOrNull(i),
            cadence = cadence?.getOrNull(i),
            powerWatts = power?.getOrNull(i),
            temperatureCelsius = temperature.at(i)?.div(DECI),
            depthMeters = depth.at(i)?.div(CENTI),
            respirationRate = respirationRate.at(i)?.div(DECI),
            verticalOscillationMm = verticalOscillation.at(i),
            stanceTimeMs = stanceTime.at(i),
            stepLengthMm = stepLength.at(i),
            verticalRatioPercent = verticalRatio.at(i)?.div(CENTI),
            stanceTimeBalancePercent = stanceTimeBalance.at(i)?.div(CENTI),
            rightBalancePercent = leftRightBalance?.getOrNull(i),
            ppO2Bar = ppO2.at(i)?.div(MILLI),
            cnsPercent = cns?.getOrNull(i),
            n2LoadPercent = n2Load?.getOrNull(i),
            ndlSeconds = ndl?.getOrNull(i)?.toLong(),
            nextStopDepthMeters = decoStopDepth.at(i)?.div(CENTI),
            nextStopSeconds = decoStopTime?.getOrNull(i)?.toLong(),
            ttsSeconds = tts?.getOrNull(i)?.toLong(),
            tankPressuresBar = tankPressures.orEmpty().map { it.pressure.getOrNull(i)?.div(MILLI) },
        )

    private fun List<Int?>?.at(i: Int): Double? = this?.getOrNull(i)?.toDouble()

    private const val MILLIS_PER_SECOND = 1000.0
    private const val DECI = 10.0
    private const val CENTI = 100.0
    private const val MILLI = 1000.0
}
