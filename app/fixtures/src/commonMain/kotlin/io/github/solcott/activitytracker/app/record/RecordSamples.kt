package io.github.solcott.activitytracker.app.record

import androidx.compose.foundation.text.input.TextFieldState
import io.github.solcott.activitytracker.app.fixtures.Fixed
import io.github.solcott.activitytracker.app.fixtures.SampleKind
import io.github.solcott.activitytracker.data.units.UnitSystem
import io.github.solcott.activitytracker.lexicon.app.ActivityType
import io.github.solcott.activitytracker.lexicon.app.Sport
import io.github.solcott.activitytracker.location.RecordingState
import kotlin.time.Duration.Companion.minutes
import kotlinx.collections.immutable.persistentListOf

private val typeField: ActivityTypeField
    get() =
        ActivityTypeField(
            ActivityType(Sport.RIDE),
            persistentListOf(ActivityType(Sport.RIDE), ActivityType(Sport.RUN), ActivityType(Sport.WALK)),
        )

object RecordSamples {
    private fun state(status: RecordingState.Status) =
        RecordState(
            status = status,
            elapsed = if (status == RecordingState.Status.Idle) 0.minutes else 42.minutes,
            distanceMeters = if (status == RecordingState.Status.Idle) 0.0 else 15_300.0,
            movingSeconds = if (status == RecordingState.Status.Idle) 0 else 2_400,
            elevationGainMeters = if (status == RecordingState.Status.Idle) 0.0 else 180.0,
            type = typeField,
            title = TextFieldState(),
            hasPermission = true,
            saving = false,
            error = null,
            eventSink = {},
        )

    val all: Map<String, RecordState>
        get() =
            mapOf(
                SampleKind.LOADED to state(RecordingState.Status.Idle),
                "recording" to state(RecordingState.Status.Recording),
                "paused" to state(RecordingState.Status.Paused),
            )
}

object ManualActivitySamples {
    private fun state(duration: String) =
        ManualActivityState(
            type = typeField,
            title = TextFieldState("Evening Ride"),
            distance = TextFieldState("24.5"),
            durationMinutes = TextFieldState(duration),
            temperature = TextFieldState(),
            units = UnitSystem.Metric,
            saving = false,
            error = null,
            eventSink = {},
        )

    val all: Map<String, ManualActivityState>
        get() = mapOf(SampleKind.LOADED to state("55"), SampleKind.INVALID to state("0"))
}

object ImportActivitySamples {
    private fun state(reading: Boolean, preview: ImportPreview?) =
        ImportActivityState(
            reading = reading,
            preview = preview,
            type = typeField,
            title = TextFieldState(),
            defaultTitle = preview?.let { DefaultTitle(PartOfDay.Morning, ActivityType(Sport.RIDE)) },
            saving = false,
            error = null,
            eventSink = {},
        )

    private val preview =
        ImportPreview(
            fileName = "morning-ride.fit",
            elapsed = 65.minutes,
            device = "Garmin Edge 840",
            polyline = Fixed.POLYLINE,
            dive = null,
            clock = null,
        )

    val all: Map<String, ImportActivityState>
        get() = mapOf(SampleKind.LOADED to state(false, null), "reading" to state(true, null), "preview" to state(false, preview))
}
