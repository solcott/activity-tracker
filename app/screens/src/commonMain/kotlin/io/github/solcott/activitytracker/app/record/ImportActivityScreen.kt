package io.github.solcott.activitytracker.app.record

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.Immutable
import com.slack.circuit.runtime.CircuitUiState
import com.slack.circuit.runtime.screen.Screen
import com.slack.circuit.serialization.CircuitSerializable
import io.github.solcott.activitytracker.app.UiError
import io.github.solcott.activitytracker.data.di.SessionScope
import io.github.solcott.activitytracker.lexicon.app.ActivityType
import kotlin.time.Duration
import kotlinx.collections.immutable.ImmutableList
import kotlinx.io.bytestring.ByteString

/** Import an activity from a FIT, GPX, TCX or Shearwater dive file (optionally gzipped) that a device or app recorded. */
@CircuitSerializable(SessionScope::class) data object ImportActivityScreen : Screen

@Immutable
data class ImportActivityState(
    /** A picked file is being read. */
    val reading: Boolean,
    /** What the file holds, once read. */
    val preview: ImportPreview?,
    /** Starts as the file's type; the athlete can correct it. */
    val type: ActivityType,
    /** Recently tracked types, then the common ones, for the picker's quick row. */
    val quickTypes: ImmutableList<ActivityType>,
    val title: TextFieldState,
    val saving: Boolean,
    val error: UiError?,
    val eventSink: (ImportActivityEvent) -> Unit,
) : CircuitUiState

/** The read file's summary, in SI units like the records it becomes. */
@Immutable
data class ImportPreview(
    val fileName: String,
    val elapsed: Duration,
    val movingSeconds: Long?,
    val distanceMeters: Double?,
    val elevationGainMeters: Double?,
    val avgHeartRate: Int?,
    val maxHeartRate: Int?,
    val device: String?,
    /** Null for indoor activities. */
    val polyline: String?,
    /** Null unless a dive computer recorded the file. */
    val dive: DivePreview?,
    /** Null unless the file's times have no zone, so the athlete has to say where it happened. */
    val clock: ImportClock?,
)

/**
 * A file whose clock has no zone (dive computers keep local time). It's read in this device's zone until the athlete picks the one they
 * were in; otherwise a dive logged on a trip saves at the wrong hour.
 */
@Immutable
data class ImportClock(
    /** The device's clock at the start, as written ("2024-10-16 17:30"). The same in every zone; only the moment it means moves. */
    val localStart: String,
    val zone: TimeZoneOption,
    /** Every zone the athlete can pick, by id. */
    val zones: ImmutableList<TimeZoneOption>,
)

/** A region zone ("America/Cancun") and its offset from UTC ("UTC-05:00") when the activity started. */
@Immutable data class TimeZoneOption(val id: String, val offset: String)

/** A dive's own data, in SI units. Shown on the import screen only; records don't carry it yet. */
@Immutable
data class DivePreview(
    val maxDepthMeters: Double?,
    val avgDepthMeters: Double?,
    val minWaterTempCelsius: Double?,
    /** The dive computer's own count. */
    val diveNumber: Int?,
    val surfaceIntervalMinutes: Int?,
    val gradientFactorLow: Int?,
    val gradientFactorHigh: Int?,
    /** Central nervous system oxygen toxicity, in percent of the limit. */
    val startCns: Int?,
    val endCns: Int?,
    /** In order of first use. */
    val gases: ImmutableList<DiveGasPreview>,
    val closedCircuit: Boolean,
    /** Depth over time, thinned for drawing. */
    val profile: ImmutableList<DepthPreviewPoint>,
    /** Time from leaving the surface to the start of the final ascent. */
    val bottomTimeSeconds: Long?,
    /** Whole-body oxygen exposure, in oxygen toxicity units. */
    val oxygenToxicityOtu: Int?,
    /** Air consumption by volume; needs the tank's size. */
    val rmvLitersPerMinute: Double?,
    /** Air consumption by tank pressure, for when the tank's size isn't known. */
    val sacBarPerMinute: Double?,
    val tanks: ImmutableList<DiveTankPreview>,
    /** Where the diver went in, or else came out: the dive site. */
    val site: DiveSitePreview?,
)

/** Whole percents; air is 21/0. */
@Immutable data class DiveGasPreview(val oxygenPercent: Int, val heliumPercent: Int)

/** Pressures in bar. */
@Immutable data class DiveTankPreview(val startPressureBar: Double?, val endPressureBar: Double?)

@Immutable data class DiveSitePreview(val latitude: Double, val longitude: Double)

@Immutable data class DepthPreviewPoint(val seconds: Long, val depthMeters: Double)

sealed interface ImportActivityEvent {
    /** The athlete picked a file. [name] is shown in the preview; the format comes from the content. */
    data class FilePicked(val name: String, val bytes: ByteString) : ImportActivityEvent

    data class TypeChanged(val value: ActivityType) : ImportActivityEvent

    /** Where a zone-less file's clock was ([ImportClock]); a [TimeZoneOption.id]. */
    data class TimeZoneChanged(val id: String) : ImportActivityEvent

    data object Save : ImportActivityEvent

    data object Back : ImportActivityEvent
}
