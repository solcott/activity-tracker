package io.github.solcott.activitytracker.data.activity

import io.github.solcott.activitytracker.lexicon.app.ActivityRecord
import io.github.solcott.activitytracker.location.Gpx
import io.github.solcott.activitytracker.location.RecordingResult
import io.github.solcott.activitytracker.location.TrackMath
import kotlin.math.roundToLong
import kotlin.time.Duration
import kotlin.time.Instant

/** An activity ready to publish, from a GPS recording or manual entry. */
data class ActivityDraft(
    val sport: String,
    val title: String?,
    val description: String?,
    val startedAt: Instant,
    val elapsed: Duration,
    val movingSeconds: Long? = null,
    val distanceMeters: Double? = null,
    val elevationGainMeters: Double? = null,
    val maxSpeedMps: Double? = null,
    val polyline: String? = null,
    val gpx: ByteArray? = null,
) {
    fun toRecord(createdAt: Instant): ActivityRecord =
        ActivityRecord(
            sport = sport,
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
                    if (seconds > 0) (d / seconds * 1000).roundToLong() else null
                },
            maxSpeed = maxSpeedMps?.let { (it * 1000).roundToLong() },
            polyline = polyline,
            createdAt = createdAt,
        )

    companion object {
        fun fromRecording(
            result: RecordingResult,
            sport: String,
            title: String?,
            description: String?,
        ): ActivityDraft {
            val state = result.state
            val samples = state.samples
            return ActivityDraft(
                sport = sport,
                title = title,
                description = description,
                startedAt = state.startedAt ?: result.endedAt,
                elapsed = state.activeBeforeSegment,
                movingSeconds = state.movingSeconds,
                distanceMeters = state.distanceMeters,
                elevationGainMeters = state.elevationGainMeters,
                maxSpeedMps = state.maxSpeedMps,
                polyline = samples.takeIf { it.size >= 2 }?.let { TrackMath.encodePolyline(TrackMath.simplify(it)) },
                gpx =
                    samples
                        .takeIf { it.isNotEmpty() }
                        ?.let {
                            Gpx.write(
                                    title ?: sport,
                                    sport,
                                    it,
                                )
                                .encodeToByteArray()
                        },
            )
        }
    }
}
