package io.github.solcott.activitytracker.data.activity

import io.github.solcott.activitytracker.activityfile.ImportedActivity
import io.github.solcott.activitytracker.activityfile.StreamBuilder
import io.github.solcott.activitytracker.lexicon.AtUri
import io.github.solcott.activitytracker.lexicon.app.ActivityRecord
import io.github.solcott.activitytracker.lexicon.app.ActivitySensor
import io.github.solcott.activitytracker.lexicon.app.ActivityStreamData
import io.github.solcott.activitytracker.lexicon.app.ActivityType
import io.github.solcott.activitytracker.lexicon.app.Visibility
import io.github.solcott.activitytracker.lexicon.app.ZoneSnapshot
import io.github.solcott.activitytracker.location.Gpx
import io.github.solcott.activitytracker.location.LocationSample
import io.github.solcott.activitytracker.location.RecordingResult
import io.github.solcott.activitytracker.location.TrackMath
import kotlin.math.roundToLong
import kotlin.time.Duration
import kotlin.time.Instant

/** An activity ready to publish, from a GPS recording, an imported file or manual entry. */
data class ActivityDraft(
    val type: ActivityType,
    val title: String?,
    val description: String?,
    val startedAt: Instant,
    val elapsed: Duration,
    val movingSeconds: Long? = null,
    val distanceMeters: Double? = null,
    val elevationGainMeters: Double? = null,
    val maxSpeedMps: Double? = null,
    val avgTemperatureCelsius: Double? = null,
    val avgHeartRate: Int? = null,
    val maxHeartRate: Int? = null,
    /** Recording device, e.g. "Garmin fenix7". */
    val device: String? = null,
    /** Paired with [device]; public, so make and model only. */
    val sensors: List<ActivitySensor> = emptyList(),
    val polyline: String? = null,
    val gpx: ByteArray? = null,
    /** Per-sample data for charts, published as the activity's streams record. */
    val streams: ActivityStreamData? = null,
    /** Who may see [streams]: a [Visibility] value. */
    val streamsVisibility: String = Visibility.PUBLIC,
    /** The athlete's zones for this activity, kept with [streams]. */
    val heartRateZones: ZoneSnapshot? = null,
    val powerZones: ZoneSnapshot? = null,
    /** The athlete's own activity this is another recording of ([ActivityRecord.recordingOf]). */
    val recordingOf: AtUri? = null,
) {
    fun toRecord(createdAt: Instant): ActivityRecord =
        ActivityRecord(
            sport = type.sport,
            subSport = type.subSport,
            title = title?.takeIf { it.isNotBlank() },
            description = description?.takeIf { it.isNotBlank() },
            startedAt = startedAt,
            elapsedTime = elapsed.inWholeSeconds,
            movingTime = movingSeconds,
            distance = distanceMeters?.roundToLong(),
            elevationGain = elevationGainMeters?.roundToLong(),
            avgSpeed =
                distanceMeters?.let { d ->
                    val seconds = movingSeconds ?: elapsed.inWholeSeconds
                    if (seconds > 0) (d / seconds * MM_PER_M).roundToLong() else null
                },
            maxSpeed = maxSpeedMps?.let { (it * MM_PER_M).roundToLong() },
            avgTemperature = avgTemperatureCelsius?.let { (it * TENTHS_PER_DEGREE).roundToLong() },
            avgHeartRate = avgHeartRate,
            maxHeartRate = maxHeartRate,
            polyline = polyline,
            device = device,
            sensors = sensors.take(ActivitySensor.MAX).ifEmpty { null },
            recordingOf = recordingOf,
            createdAt = createdAt,
        )

    // Generated equals/hashCode compare arrays by identity; compare gpx by content instead.
    private fun fieldsExceptGpx(): List<Any?> =
        listOf(
            type,
            title,
            description,
            startedAt,
            elapsed,
            movingSeconds,
            distanceMeters,
            elevationGainMeters,
            maxSpeedMps,
            avgTemperatureCelsius,
            avgHeartRate,
            maxHeartRate,
            device,
            sensors,
            polyline,
            streams,
            streamsVisibility,
            heartRateZones,
            powerZones,
            recordingOf,
        )

    override fun equals(other: Any?): Boolean =
        this === other || other is ActivityDraft && fieldsExceptGpx() == other.fieldsExceptGpx() && gpx.contentEquals(other.gpx)

    override fun hashCode(): Int = 31 * fieldsExceptGpx().hashCode() + gpx.contentHashCode()

    companion object {
        /** Lexicon speeds are integer mm/s. */
        private const val MM_PER_M = 1000

        /** Lexicon temperatures are integer tenths of a degree Celsius. */
        private const val TENTHS_PER_DEGREE = 10

        fun fromRecording(
            result: RecordingResult,
            type: ActivityType,
            title: String?,
            description: String?,
        ): ActivityDraft {
            val state = result.state
            val samples = state.samples
            return ActivityDraft(
                type = type,
                title = title,
                description = description,
                startedAt = state.startedAt ?: result.endedAt,
                elapsed = state.activeBeforeSegment,
                movingSeconds = state.movingSeconds,
                distanceMeters = state.distanceMeters,
                elevationGainMeters = state.elevationGainMeters,
                maxSpeedMps = state.maxSpeedMps,
                polyline = polylineOf(samples),
                gpx = gpxOf(samples, title, type.sport),
                streams = StreamBuilder.fromLocations(samples, state.startedAt ?: result.endedAt),
            )
        }

        /** An activity read from a FIT (and later GPX or TCX) file. Its track is published as GPX, like a recorded one. */
        fun fromImport(
            imported: ImportedActivity,
            type: ActivityType,
            title: String?,
        ): ActivityDraft =
            ActivityDraft(
                type = type,
                title = title,
                description = null,
                startedAt = imported.startedAt,
                elapsed = imported.elapsed,
                movingSeconds = imported.movingSeconds,
                distanceMeters = imported.distanceMeters,
                elevationGainMeters = imported.elevationGainMeters,
                maxSpeedMps = imported.maxSpeedMps,
                avgTemperatureCelsius = imported.avgTemperatureCelsius,
                avgHeartRate = imported.avgHeartRate,
                maxHeartRate = imported.maxHeartRate,
                device = imported.device,
                sensors = imported.sensors.map { ActivitySensor(it.kind.token, it.manufacturer, it.model) },
                polyline = polylineOf(imported.samples),
                gpx = gpxOf(imported.samples, title, type.sport),
                streams = imported.streams,
            )

        /** The feed polyline for a track: simplified, then encoded. Null below two points. */
        fun polylineOf(samples: List<LocationSample>): String? =
            samples.takeIf { it.size >= 2 }?.let { TrackMath.encodePolyline(TrackMath.simplify(it)) }

        private fun gpxOf(samples: List<LocationSample>, title: String?, sport: String): ByteArray? =
            samples.takeIf { it.isNotEmpty() }?.let { Gpx.write(title ?: sport, sport, it).encodeToByteArray() }
    }
}
