package io.github.solcott.activitytracker.activityfile

import io.github.solcott.activitytracker.lexicon.app.ActivityStreamData
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
)

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
) {
    val hasPosition: Boolean
        get() = latitude != null && longitude != null
}

/** Joins an activity's stored track and streams into the points of an exported file. */
object ExportPoints {
    /**
     * Each track point takes the stream sample at its second (rounded from [startedAt]), so two points in one second both get it; a sample
     * at a second no track point has becomes a point of its own with no position. Units go back from the lexicon's integers to SI (the
     * reverse of [StreamBuilder]). Altitude is the stream's where it has one, else the track's GPS altitude. Gear changes are dropped:
     * neither GPX nor TCX has them.
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
        )

    private fun List<Int?>?.at(i: Int): Double? = this?.getOrNull(i)?.toDouble()

    private const val MILLIS_PER_SECOND = 1000.0
    private const val DECI = 10.0
    private const val CENTI = 100.0
    private const val MILLI = 1000.0
}
