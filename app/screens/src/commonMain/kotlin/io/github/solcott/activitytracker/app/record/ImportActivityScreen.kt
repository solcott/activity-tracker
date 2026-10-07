package io.github.solcott.activitytracker.app.record

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.Immutable
import com.slack.circuit.runtime.CircuitUiState
import com.slack.circuit.runtime.screen.Screen
import com.slack.circuit.serialization.CircuitSerializable
import io.github.solcott.activitytracker.activityfile.SensorBattery
import io.github.solcott.activitytracker.activityfile.SensorKind
import io.github.solcott.activitytracker.app.UiError
import io.github.solcott.activitytracker.data.di.SessionScope
import io.github.solcott.activitytracker.data.training.ThresholdChange
import io.github.solcott.activitytracker.lexicon.app.ActivityType
import kotlin.time.Duration
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
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
    /** Starts as the file's own title, if it has one. */
    val title: TextFieldState,
    /** What a blank [title] saves as, when the file has no title of its own; follows [type] and the clock's zone. */
    val defaultTitle: DefaultTitle?,
    val saving: Boolean,
    val error: UiError?,
    /** Thresholds that saving updates from the recording device's settings, where the athlete lets imports update them. */
    val thresholdChanges: ImmutableList<ThresholdChange> = persistentListOf(),
    val eventSink: (ImportActivityEvent) -> Unit,
) : CircuitUiState

/** A title in Strava's style, "Morning Mountain Bike Ride": when the activity started, then its type. The UI words it. */
@Immutable data class DefaultTitle(val partOfDay: PartOfDay, val type: ActivityType)

/** Morning is 5:00 to noon, afternoon to 17:00, evening to 21:00, and night the rest, on the clock where the activity happened. */
enum class PartOfDay {
    Morning,
    Afternoon,
    Evening,
    Night,
}

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
    val elevationLossMeters: Double? = null,
    /** Null unless a ride recorded cadence. */
    val cadence: CadencePreview? = null,
    /** Null without a power meter. */
    val power: PowerPreview? = null,
    /** Null without electronic shifting. */
    val shifting: ShiftingPreview? = null,
    /** Null unless the device recorded mountain bike dynamics. */
    val mtb: MtbPreview? = null,
    /** The sensors paired with [device]. */
    val sensors: ImmutableList<SensorPreview> = persistentListOf(),
)

/** Revolutions per minute; the average leaves out coasting. */
@Immutable data class CadencePreview(val avg: Int?, val max: Int?)

/** Watts. Shown on the import screen only; records don't carry it yet, and some of it may need to stay private. */
@Immutable
data class PowerPreview(
    val avgWatts: Int?,
    val maxWatts: Int?,
    val normalizedWatts: Int?,
    val workKilojoules: Long?,
    /** The athlete's FTP as the device had it; [intensityFactor] and [trainingStressScore] are relative to it. */
    val ftpWatts: Int?,
    val intensityFactor: Double?,
    val trainingStressScore: Double?,
)

@Immutable
data class ShiftingPreview(
    val frontShifts: Int?,
    val rearShifts: Int?,
    /** By rear gear number, 1 being the largest cog. */
    val gears: ImmutableList<GearTimePreview>,
)

/** By gear number, not teeth: devices report teeth from a gearing setup that is often a default rather than the bike's. */
@Immutable data class GearTimePreview(val gear: Int, val seconds: Long)

@Immutable
data class MtbPreview(
    val jumps: Int?,
    val longestHangTimeSeconds: Double?,
    val longestJumpMeters: Double?,
    /** Garmin's trail difficulty score. */
    val grit: Double?,
    /** Garmin's trail smoothness score; lower is smoother. */
    val flow: Double?,
)

/** [model] is null when only its maker can name it; the UI then names it by [kind]. */
@Immutable
data class SensorPreview(
    val kind: SensorKind,
    val manufacturer: String?,
    val model: String?,
    val battery: SensorBattery?,
    val batteryPercent: Int?,
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

    /** [defaultTitle] is [ImportActivityState.defaultTitle] in words, saved when the title is blank. */
    data class Save(val defaultTitle: String?) : ImportActivityEvent

    data object Back : ImportActivityEvent
}
