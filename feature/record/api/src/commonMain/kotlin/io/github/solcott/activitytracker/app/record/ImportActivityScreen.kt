package io.github.solcott.activitytracker.app.record

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.Immutable
import com.slack.circuit.runtime.CircuitUiState
import com.slack.circuit.runtime.screen.Screen
import com.slack.circuit.serialization.CircuitSerializable
import io.github.solcott.activitytracker.activityfile.ExportKind
import io.github.solcott.activitytracker.activityfile.ImportSource
import io.github.solcott.activitytracker.activityfile.SensorBattery
import io.github.solcott.activitytracker.activityfile.SensorKind
import io.github.solcott.activitytracker.activityfile.SkipReason
import io.github.solcott.activitytracker.app.UiError
import io.github.solcott.activitytracker.app.detail.ActivityPageContent
import io.github.solcott.activitytracker.app.edit.ActivityDetailsFields
import io.github.solcott.activitytracker.app.visibility.VisibilityFields
import io.github.solcott.activitytracker.data.activity.CombineCue
import io.github.solcott.activitytracker.data.di.SessionScope
import io.github.solcott.activitytracker.data.training.ThresholdChange
import io.github.solcott.activitytracker.lexicon.AtUri
import io.github.solcott.activitytracker.lexicon.app.ActivityType
import io.github.solcott.activitytracker.lexicon.app.ActivityView
import kotlin.time.Duration
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.datetime.LocalDate
import kotlinx.io.bytestring.ByteString

/**
 * Import activities from FIT, GPX, TCX or Shearwater dive files, UDDF or Subsurface logbooks, gzipped or not, or zips of them (a Strava or
 * Garmin Connect account export among them), several at once. With [combineInto], the AT-URI of the athlete's own activity, the one file
 * picked is another recording of it ("Add a recording…"): it's saved and combined with that activity.
 */
@CircuitSerializable(SessionScope::class) data class ImportActivityScreen(val combineInto: String? = null) : Screen

@Immutable
data class ImportActivityState(
    /** Picked files are being read. */
    val reading: Boolean,
    /** What the file holds, once read; null for an import of several activities, which is [batch] instead. */
    val preview: ImportPreview?,
    /** Starts as the file's type; the athlete can correct it. */
    val type: ActivityTypeField,
    /** Starts as the file's own title, if it has one. */
    val title: TextFieldState,
    /** What a blank [title] saves as, when the file has no title of its own; follows [type] and the clock's zone. */
    val defaultTitle: DefaultTitle?,
    val saving: Boolean,
    val error: UiError?,
    /** Thresholds that saving updates from the recording device's settings, where the athlete lets imports update them. */
    val thresholdChanges: ImmutableList<ThresholdChange> = persistentListOf(),
    /**
     * Who may see the activity (or, for a batch, every activity, an account export's private ones excepted) and each detail category;
     * starts at the athlete's defaults.
     */
    val visibility: VisibilityFields = VisibilityFields(),
    /** Several activities read from the picked files (a logbook's dives, several files, an archive); the athlete picks which to import. */
    val batch: ImportBatch? = null,
    /** How reading the picked files is going; null when nothing is being read. */
    val readProgress: ReadProgress? = null,
    /** How the search for activities already in the athlete's log is going; saving waits for it. */
    val duplicateCheck: DuplicateCheck = DuplicateCheck.Done,
    /** The description, dive site and photos of a single activity; null until a file is read, and for a logbook. */
    val details: ActivityDetailsFields? = null,
    /** The athlete approves followers, so Followers means only the ones they approved. */
    val approvesFollowers: Boolean = false,
    /** The file is over 10 MB even compressed, so it imports without its original file. */
    val originalTooLarge: Boolean = false,
    /**
     * What the activity's page will show the athlete once saved, built from the file as it would publish: every category, each narrower
     * than the activity marked. Rebuilt when the type or visibility changes; null until built, and for a logbook.
     */
    val page: ActivityPageContent? = null,
    /**
     * Adding a recording to an activity ([ImportActivityScreen.combineInto]): it takes that activity's visibility, so there are no
     * visibility fields, and there's no duplicate search.
     */
    val addsRecording: Boolean = false,
    /** The activity the recording is added to; null until it loads, and when not [addsRecording]. */
    val addingTo: ExistingActivityPreview? = null,
    /** The file's activity starts more than 12 hours from [addingTo]'s, so it isn't another recording of it and can't be saved. */
    val tooFarFromTarget: Boolean = false,
    /**
     * A batch's activities, just saved, that likely belong with another of the athlete's activities: each opens its page to combine it
     * ([ImportActivityEvent.Saved.Open]). Null until a save finds any; a batch save shows [batchSaved] either way.
     */
    val savedSuggestions: ImmutableList<SavedSuggestion>? = null,
    /** A batch just saved: how many of its activities were saved and skipped. Null until then. */
    val batchSaved: BatchSaved? = null,
    /**
     * The gear used (for a batch, by every activity); null until a file is read. Starts with what the file's linked sensors and activity
     * profile stand for, then the athlete's defaults for the type.
     */
    val gear: GearField? = null,
    /** The devices the file names that aren't linked to gear yet, and the profile link offer. */
    val newDevices: NewDevicesFields = NewDevicesFields(),
    /** The import is saved; what's left to say or offer before leaving. */
    val afterImport: AfterImport? = null,
    val eventSink: (ImportActivityEvent) -> Unit,
) : CircuitUiState

/** A dive just saved from a logbook, and the activity it likely belongs with. */
@Immutable
data class SavedSuggestion(val uri: AtUri, val title: String?, val startEpochSeconds: Long, val cue: CombineCue, val other: ActivityView)

/** A batch just saved: [saved] activities queued for the athlete's log, [skipped] that couldn't be read again to save. */
@Immutable data class BatchSaved(val saved: Int, val skipped: Int)

/** Reading picked files: [files] activity files read so far, holding [activities]. */
@Immutable data class ReadProgress(val files: Int, val activities: Int)

/**
 * Several activities to import at once, oldest first: a logbook's dives, several picked files, or the activity files in an archive. Each
 * keeps its own type and title; every one that isn't a duplicate starts selected. Visibility, categories and gear are the batch's.
 */
@Immutable
data class ImportBatch(
    /** Every activity read, oldest first; [ImportRow.index] names each. */
    val rows: ImmutableList<ImportRow>,
    val filter: BatchFilter = BatchFilter.All,
    /** A zone choice for each picked file (or archive member) whose times have no zone, which places all its activities. */
    val clocks: ImmutableList<FileClock> = persistentListOf(),
    /** Files that looked like activity files but couldn't be read. */
    val skipped: ImmutableList<SkippedFile> = persistentListOf(),
    /** Archive members that aren't activity files (photos, notes), left out. */
    val ignoredFiles: Int = 0,
    /** The account export the activities came in, whose titles, types and privacy they take; null for any other files. */
    val export: ExportKind? = null,
    /** Files over 10 MB even compressed, whose activities import without their original. */
    val originalsTooLarge: ImmutableList<String> = persistentListOf(),
    /** At most how many bytes of original files the selected activities keep on this device until they're uploaded. */
    val originalsBytes: Long = 0,
    /** The activity the athlete opened to look at; null when none is. */
    val preview: ImportItemPreview? = null,
    /** Saving: how many are queued so far. Null when not saving. */
    val progress: SaveProgress? = null,
) {
    /** The rows [filter] shows. */
    val shown: ImmutableList<ImportRow>
        get() =
            when (filter) {
                BatchFilter.All -> rows
                BatchFilter.New -> rows.filter { it.duplicate == null }.toImmutableList()
                BatchFilter.Duplicates -> rows.filter { it.duplicate != null }.toImmutableList()
            }

    val selectedCount: Int
        get() = rows.count { it.selected }

    /** Activities that aren't duplicates. */
    val newCount: Int
        get() = rows.count { it.duplicate == null }

    /**
     * The rows Select all and Deselect all change: those [shown], leaving duplicates as the athlete set them unless only duplicates show.
     */
    val selectable: ImmutableList<ImportRow>
        get() = if (filter == BatchFilter.Duplicates) shown else shown.filter { it.duplicate == null }.toImmutableList()

    /** Every [selectable] row is selected. */
    val allShownSelected: Boolean
        get() = selectable.all { it.selected }

    /** Activities already in the athlete's log. */
    val existingDuplicates: Int
        get() = rows.count { it.duplicate?.existing != null }

    /** Activities that repeat an earlier one in this batch. */
    val inBatchDuplicates: Int
        get() = rows.count { it.duplicate?.inBatchIndex != null }
}

/** Which of a batch's rows show. */
enum class BatchFilter {
    All,
    /** Those not already in the athlete's log or earlier in the batch. */
    New,
    Duplicates,
}

/** A file in a batch whose times have no zone, by [key], placed as [clock] says. */
@Immutable data class FileClock(val key: Int, val fileName: String, val clock: ImportClock)

/** A file in a batch that wasn't read, and why. */
@Immutable data class SkippedFile(val name: String, val reason: SkipReason)

/** Saving a batch: [queued] of [total] activities so far. */
@Immutable data class SaveProgress(val queued: Int, val total: Int)

/**
 * One of a batch's activities opened to look at before saving: its [preview] and [page] as the single-file import shows them, read from its
 * file again, and the only things the athlete can change of it here, its [title] and [type].
 */
@Immutable
data class ImportItemPreview(
    /** The [ImportRow.index] it shows. */
    val index: Int,
    val title: TextFieldState,
    val type: ActivityTypeField,
    /** What a blank [title] saves as. */
    val defaultTitle: DefaultTitle,
    /** Null while it's read again, and when that failed ([error]). */
    val preview: ImportPreview? = null,
    val page: ActivityPageContent? = null,
    val error: UiError? = null,
)

enum class DuplicateCheck {
    Checking,
    Done,
    /** The athlete's log couldn't all be read (offline, say); duplicates are flagged only within the batch and where it was read. */
    Failed,
}

/** What the athlete does with an activity that's already in their log, or earlier in the batch. */
enum class DuplicateAction {
    /** Leave it out. */
    Ignore,
    /** Import it as an activity of its own anyway. */
    ImportAnyway,
    /**
     * Import it and combine it with the activity it repeats (or that one's combined activity), keeping both recordings as one activity; the
     * default.
     */
    Combine,
}

/**
 * Why an activity looks like a duplicate, and what the athlete chose to do with it. Exactly one of [existing] and [inBatchIndex] is set.
 */
@Immutable
data class DuplicateInfo(
    /** The activity already in the athlete's log. */
    val existing: ExistingActivityPreview?,
    /** The earlier activity in this batch it repeats, by [ImportRow.index]. */
    val inBatchIndex: Int?,
    /** 0 for the same dive; else hours apart with the same length, which suggests the wrong zone was picked. */
    val hoursApart: Int,
    val action: DuplicateAction,
    /** What the athlete can do with it, [action] among them. */
    val actions: ImmutableList<DuplicateAction>,
    /** The logged activity is still uploading, so [DuplicateAction.Combine] can't be chosen yet; Import anyway can. */
    val stillUploading: Boolean = false,
)

/** An activity already in the athlete's log, as a duplicate warning (or the recording it's added to) names it. */
@Immutable
data class ExistingActivityPreview(
    val title: String?,
    /** When it started, on the clock of the picked zone ("2024-05-12 10:02"). */
    val start: String,
    val device: String?,
)

/** One activity of an [ImportBatch], in SI units. */
@Immutable
data class ImportRow(
    /** Its place in the batch, oldest first; what [ImportActivityEvent.ItemToggled] names. */
    val index: Int,
    /** The title it saves with: the athlete's, its account export's, or its file's (a logbook's dive site). */
    val title: String?,
    /** What it saves as without a [title]. */
    val defaultTitle: DefaultTitle,
    /** The type it saves as: the athlete's, its account export's, or its file's. */
    val type: ActivityType,
    /** When it started, on the clock of the zone it's placed in ("2024-05-12 10:02"). */
    val start: String,
    /** The first day of the month it started in, which the list groups by. */
    val month: LocalDate,
    val elapsed: Duration,
    val distanceMeters: Double?,
    /** Null unless it's a dive. */
    val dive: DiveRowExtras? = null,
    val device: String?,
    /** The file it was read from. */
    val fileName: String,
    val selected: Boolean,
    /** Null unless it's already in the athlete's log or earlier in this batch; such an activity starts set to combine with it. */
    val duplicate: DuplicateInfo? = null,
    /** The visibility its account export narrows it to, when narrower than the batch's; null otherwise. */
    val narrowedTo: String? = null,
)

/** A dive's depth and number for its row. */
@Immutable
data class DiveRowExtras(
    val maxDepthMeters: Double?,
    /** The dive computer's own count. */
    val diveNumber: Int?,
)

/** A default title, "Morning Mountain Bike Ride": when the activity started, then its type. The UI words it. */
@Immutable data class DefaultTitle(val partOfDay: PartOfDay, val type: ActivityType)

/** Morning is 5:00 to noon, afternoon to 17:00, evening to 21:00, and night the rest, on the clock where the activity happened. */
enum class PartOfDay {
    Morning,
    Afternoon,
    Evening,
    Night,
}

/** The read file's own details beyond what [ImportActivityState.page] shows, in SI units like the records it becomes. */
@Immutable
data class ImportPreview(
    val fileName: String,
    val elapsed: Duration,
    val device: String?,
    /** Null for indoor activities. */
    val polyline: String?,
    /** Where [polyline] breaks (`feed.activity#polylineGaps`); null when it's one line. */
    val polylineGaps: ImmutableList<Int>? = null,
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
    /** Null unless it's already in the athlete's log; saving then follows [DuplicateInfo.action]. */
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

sealed interface ImportActivityEvent {
    /** The athlete picked one file, read whole ([bytes]); [name] is shown in the preview. The format comes from the content. */
    data class FilePicked(val name: String, val bytes: ByteString) : ImportActivityEvent

    /** The athlete picked [sources]: activity files, gzipped or not, or zips of them, read as needed rather than held. */
    data class FilesPicked(val sources: ImmutableList<ImportSource>) : ImportActivityEvent

    /** Stops reading the picked files, keeping nothing of them. */
    data object CancelReading : ImportActivityEvent

    /** Where a zone-less file's clock was ([ImportClock]); a [TimeZoneOption.id]. */
    data class TimeZoneChanged(val id: String) : ImportActivityEvent

    /** Where a batch's zone-less file ([FileClock.key]) was; a [TimeZoneOption.id]. */
    data class FileZoneChanged(val key: Int, val id: String) : ImportActivityEvent

    /** [defaultTitle] is [ImportActivityState.defaultTitle] in words, saved when the title is blank. */
    data class Save(val defaultTitle: String?) : ImportActivityEvent

    /**
     * Imports an [ImportBatch]'s selected activities. [defaultTitles] has each untitled one's [ImportRow.defaultTitle] in words, by index.
     */
    data class SaveBatch(val defaultTitles: ImmutableMap<Int, String>) : ImportActivityEvent

    /** What to do with a duplicate: an [ImportRow.index], or 0 for a single [ImportPreview]. */
    data class DuplicateActionChanged(val index: Int, val action: DuplicateAction) : ImportActivityEvent

    /** Selects or clears one of an [ImportBatch]'s activities, by [ImportRow.index]; a duplicate's [DuplicateAction] follows. */
    data class ItemToggled(val index: Int) : ImportActivityEvent

    /** Selects or clears every [ImportBatch.selectable] activity; a duplicate's [DuplicateAction] follows. */
    data class AllShownSelected(val selected: Boolean) : ImportActivityEvent

    /** Which of the batch's rows show. */
    data class FilterChanged(val filter: BatchFilter) : ImportActivityEvent

    /** Opens one of the batch's activities, by [ImportRow.index], to look at it and change its title and type. */
    data class ItemOpened(val index: Int) : ImportActivityEvent

    /** Closes [ImportBatch.preview], keeping the title and type it shows for that activity. */
    data object ItemClosed : ImportActivityEvent

    data object Back : ImportActivityEvent

    /** The athlete acts on [ImportActivityState.afterImport]. */
    sealed interface After : ImportActivityEvent {
        /** Adds the newly linked gear to the earlier imports offered. */
        data object ApplyToPast : After

        /** Leaves, as saving would have. */
        data object Done : After
    }

    /** The athlete acts on [ImportActivityState.savedSuggestions] or [ImportActivityState.batchSaved]. */
    sealed interface Saved : ImportActivityEvent {
        /** Opens a just-saved activity's page, asking there whether to combine it. */
        data class Open(val uri: AtUri) : Saved

        /** Leaves the screen, combining nothing more. */
        data object Done : Saved
    }
}
