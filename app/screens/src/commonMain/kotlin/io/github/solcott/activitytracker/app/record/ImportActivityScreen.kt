package io.github.solcott.activitytracker.app.record

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.Immutable
import com.slack.circuit.runtime.CircuitUiState
import com.slack.circuit.runtime.screen.Screen
import com.slack.circuit.serialization.CircuitSerializable
import io.github.solcott.activitytracker.activityfile.SensorBattery
import io.github.solcott.activitytracker.activityfile.SensorKind
import io.github.solcott.activitytracker.app.UiError
import io.github.solcott.activitytracker.app.edit.ActivityDetailsFields
import io.github.solcott.activitytracker.app.visibility.VisibilityFields
import io.github.solcott.activitytracker.data.di.SessionScope
import io.github.solcott.activitytracker.data.training.ThresholdChange
import io.github.solcott.activitytracker.lexicon.app.ActivityType
import kotlin.time.Duration
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.persistentListOf
import kotlinx.io.bytestring.ByteString

/** Import activities from a FIT, GPX, TCX or Shearwater dive file, or a UDDF or Subsurface logbook (optionally gzipped). */
@CircuitSerializable(SessionScope::class) data object ImportActivityScreen : Screen

@Immutable
data class ImportActivityState(
    /** A picked file is being read. */
    val reading: Boolean,
    /** What the file holds, once read; null for a logbook of several dives, which is [dives] instead. */
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
    /** Who may see the activity (or, for a logbook, every dive) and its charts; starts at the athlete's defaults. */
    val visibility: VisibilityFields = VisibilityFields(),
    /** A logbook (UDDF, Subsurface) of several dives, once read; the athlete picks which to import. */
    val dives: DiveBatch? = null,
    /** How the search for dives already in the athlete's log is going; saving waits for it. */
    val duplicateCheck: DuplicateCheck = DuplicateCheck.Done,
    /** The description, dive site and photos of a single activity; null until a file is read, and for a logbook. */
    val details: ActivityDetailsFields? = null,
    /** The athlete approves followers, so Followers means only the ones they approved. */
    val approvesFollowers: Boolean = false,
    /** The file is over 10 MB even compressed, so it imports without its original file. */
    val originalTooLarge: Boolean = false,
    val eventSink: (ImportActivityEvent) -> Unit,
) : CircuitUiState

/** A logbook's dives in the file's order, each imported with its own type and title. They start selected. */
@Immutable
data class DiveBatch(
    val fileName: String,
    val dives: ImmutableList<DiveRow>,
    /** The first dive's clock: the athlete picks one zone for the whole file. */
    val clock: ImportClock?,
) {
    val selectedCount: Int
        get() = dives.count { it.selected }

    /** Every dive that isn't a duplicate is selected; Select all and Deselect all leave duplicates alone. */
    val allSelected: Boolean
        get() = dives.filter { it.duplicate == null }.all { it.selected }

    /** Whether Select all has anything to select: not when every dive is a duplicate. */
    val hasNonDuplicates: Boolean
        get() = dives.any { it.duplicate == null }

    /** Dives already in the athlete's log. */
    val existingDuplicates: Int
        get() = dives.count { it.duplicate?.existing != null }

    /** Dives that repeat an earlier one in this file. */
    val inFileDuplicates: Int
        get() = dives.count { it.duplicate?.inFileIndex != null }
}

enum class DuplicateCheck {
    Checking,
    Done,
    /** The athlete's log couldn't be read (offline, say); only duplicates within the file are flagged. */
    Failed,
}

/** What the athlete does with a dive that's already in their log, or twice in the file. */
enum class DuplicateAction {
    /** Leave it out; the default. */
    Ignore,
    /** Import it as another activity anyway. */
    ImportAnyway,
    /** Fill what the logged dive lacks from this one (its device, say, or charts), changing nothing it has. Only for a logged dive. */
    Merge,
    /**
     * Import it as another recording (a backup computer's, say) of the dive it repeats: feeds show that dive once, and its page lists both
     * recordings.
     */
    Group,
}

/** What merging a dive into the logged one would add to it. */
enum class MergeGain {
    Device,
    SubSport,
    WaterTemperature,
    /** Depths, gases and the rest of the dive computer's summary. */
    DiveDetails,
    HeartRate,
    Track,
    /** Charts (depth, temperature…) the logged dive lacks; only channels it doesn't have are added. */
    Charts,
    /** The file's charts, for whichever the logged dive lacks: its own charts haven't loaded (or couldn't) to tell. */
    AnyCharts,
}

/** Why a dive looks like a duplicate, and what the athlete chose to do with it. Exactly one of [existing] and [inFileIndex] is set. */
@Immutable
data class DuplicateInfo(
    /** The dive already in the athlete's log. */
    val existing: ExistingActivityPreview?,
    /** The earlier dive in this file it repeats, by [DiveRow.index]. */
    val inFileIndex: Int?,
    /** 0 for the same dive; else hours apart with the same length, which suggests the wrong zone was picked. */
    val hoursApart: Int,
    val action: DuplicateAction,
    /** What the athlete can do with it, [action] among them. */
    val actions: ImmutableList<DuplicateAction>,
    /** What [DuplicateAction.Merge] would add to the logged dive; empty when it would add nothing. */
    val mergeGains: ImmutableList<MergeGain> = persistentListOf(),
)

/** An activity already in the athlete's log, as a duplicate warning names it. */
@Immutable
data class ExistingActivityPreview(
    val title: String?,
    /** When it started, on the clock of the picked zone ("2024-05-12 10:02"). */
    val start: String,
    val device: String?,
)

/** One dive of a [DiveBatch], in SI units. */
@Immutable
data class DiveRow(
    /** Its place in the file; what [ImportActivityEvent.DiveToggled] names. */
    val index: Int,
    /** The dive site, when the logbook names one. */
    val title: String?,
    /** What it saves as without a [title]. */
    val defaultTitle: DefaultTitle,
    /** When it started, on the clock of the picked zone ("2024-05-12 10:02"). */
    val start: String,
    val elapsed: Duration,
    val maxDepthMeters: Double?,
    /** The dive computer's own count. */
    val diveNumber: Int?,
    val device: String?,
    val selected: Boolean,
    /** Null unless it's already in the athlete's log or earlier in this file; such a dive starts unselected. */
    val duplicate: DuplicateInfo? = null,
)

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
    /** The file has per-sample data to chart, published with the activity. */
    val hasStreams: Boolean = false,
    /** Null unless it's a dive already in the athlete's log; saving then follows [DuplicateInfo.action]. */
    val duplicate: DuplicateInfo? = null,
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

    /** Imports a [DiveBatch]'s selected dives. [defaultTitles] has each untitled dive's [DiveRow.defaultTitle] in words, by index. */
    data class SaveDives(val defaultTitles: ImmutableMap<Int, String>) : ImportActivityEvent

    /** What to do with a duplicate dive: a [DiveRow.index], or 0 for a single [ImportPreview]. */
    data class DuplicateActionChanged(val index: Int, val action: DuplicateAction) : ImportActivityEvent

    /** Selects or clears one of a [DiveBatch]'s dives, by [DiveRow.index]; a duplicate's [DuplicateAction] follows. */
    data class DiveToggled(val index: Int) : ImportActivityEvent

    /** Selects or clears every dive in the [DiveBatch] that isn't a duplicate. */
    data class AllDivesSelected(val selected: Boolean) : ImportActivityEvent

    /** Who may see the activity's charts: a `Visibility` value. */
    data class ChartsVisibilityChanged(val visibility: String) : ImportActivityEvent

    /** Who may see the activity (every dive of a logbook): a `Visibility` value. Narrows the charts with it. */
    data class VisibilityChanged(val visibility: String) : ImportActivityEvent

    data object Back : ImportActivityEvent
}
