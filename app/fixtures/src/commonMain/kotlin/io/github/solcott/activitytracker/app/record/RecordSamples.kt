package io.github.solcott.activitytracker.app.record

import androidx.compose.foundation.text.input.TextFieldState
import io.github.solcott.activitytracker.app.UiError
import io.github.solcott.activitytracker.app.fixtures.Fixed
import io.github.solcott.activitytracker.app.fixtures.SampleKind
import io.github.solcott.activitytracker.app.visibility.VisibilityFields
import io.github.solcott.activitytracker.data.units.UnitSystem
import io.github.solcott.activitytracker.lexicon.AtUri
import io.github.solcott.activitytracker.lexicon.Nsid
import io.github.solcott.activitytracker.lexicon.app.ActivityType
import io.github.solcott.activitytracker.lexicon.app.GearType
import io.github.solcott.activitytracker.lexicon.app.SensorType
import io.github.solcott.activitytracker.lexicon.app.Sport
import io.github.solcott.activitytracker.lexicon.app.Visibility
import io.github.solcott.activitytracker.location.RecordingState
import kotlin.time.Duration.Companion.minutes
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentSetOf

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
    private fun state(duration: String, error: UiError? = null, visibility: VisibilityFields = VisibilityFields()) =
        ManualActivityState(
            type = typeField,
            title = TextFieldState("Evening Ride"),
            distance = TextFieldState("24.5"),
            durationMinutes = TextFieldState(duration),
            temperature = TextFieldState(),
            units = UnitSystem.Metric,
            saving = false,
            error = error,
            visibility = visibility,
            eventSink = {},
        )

    val all: Map<String, ManualActivityState>
        get() =
            mapOf(
                SampleKind.LOADED to state("55"),
                SampleKind.INVALID to state("0"),
                // On a PDS without Spaces: the picker is Public and says why, and a save the session thought it could make was refused.
                "spacesUnsupported" to
                    state(
                        "55",
                        UiError(UiError.Kind.SpacesUnsupported),
                        VisibilityFields(canChoose = false, spacesUnsupported = true),
                    ),
            )
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

    private val tarmac = AtUri("at://${Fixed.ALICE.value}/${Nsid.GEAR_ITEM}/3ktarmac")
    private val stumpjumper = AtUri("at://${Fixed.ALICE.value}/${Nsid.GEAR_ITEM}/3kstumpy")

    private val gear =
        GearField(
            options =
                persistentListOf(
                    GearOption(tarmac, "Tarmac", GearType.BIKE, "road", Visibility.PUBLIC),
                    GearOption(stumpjumper, "Stumpjumper", GearType.BIKE, "mountain", Visibility.PUBLIC),
                ),
            selected = persistentListOf(tarmac, stumpjumper),
            conflicts = persistentListOf(GearConflict(GearType.BIKE, persistentListOf("Tarmac", "Stumpjumper"))),
        )

    /** An Edge 840 ride naming three devices not linked yet: the power meter being added, mounted on the Tarmac. */
    private val newDevices =
        NewDevicesFields(
            cards =
                persistentListOf(
                    NewDeviceCard("edge", "Garmin", "Edge 840", recorder = true, serialTail = "2071", type = GearType.DEVICE),
                    NewDeviceCard(
                        "hrm",
                        "Garmin",
                        "HRM 200",
                        recorder = false,
                        sensorKind = SensorType.HEART_RATE,
                        weak = true,
                        decision = NewDeviceDecision.NotGear,
                        type = GearType.SENSOR,
                        kind = "heartRate",
                    ),
                    NewDeviceCard(
                        "meter",
                        "SRAM",
                        "1052",
                        recorder = false,
                        sensorKind = SensorType.POWER,
                        serialTail = "3426",
                        decision = NewDeviceDecision.Add,
                        name = TextFieldState("SRAM Power meter"),
                        type = GearType.COMPONENT,
                        kind = "powerMeter",
                        mountOn = tarmac,
                        collections = persistentSetOf("3kcol1"),
                        expanded = true,
                    ),
                ),
            mountOptions = gear.options,
            collections = persistentListOf(GearCollectionOption("3kcol1", "Race kit", 4), GearCollectionOption("3kcol2", "Winter", 2)),
            profileOffer = ProfileLinkOffer("MOUNTAIN", gear.options),
        )

    val all: Map<String, ImportActivityState>
        get() =
            mapOf(
                SampleKind.LOADED to state(false, null),
                "reading" to state(true, null),
                "preview" to state(false, preview),
                "newDevices" to state(false, preview).copy(gear = gear, newDevices = newDevices),
                "afterImport" to
                    state(false, preview)
                        .copy(afterImport = AfterImport(pastImports = PastImportsOffer(persistentListOf("Tarmac"), count = 10))),
            )
}
