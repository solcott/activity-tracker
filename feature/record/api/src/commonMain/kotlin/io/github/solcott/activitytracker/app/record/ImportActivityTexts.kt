package io.github.solcott.activitytracker.app.record

import androidx.compose.runtime.Immutable
import io.github.solcott.activitytracker.activityfile.ExportKind
import io.github.solcott.activitytracker.activityfile.SkipReason
import io.github.solcott.activitytracker.app.detail.FigureTexts
import io.github.solcott.activitytracker.app.detail.FigureValue
import io.github.solcott.activitytracker.app.detail.RecordingTexts
import io.github.solcott.activitytracker.app.gear.GearTexts
import io.github.solcott.activitytracker.app.gear.deviceName
import io.github.solcott.activitytracker.app.text.AppText
import io.github.solcott.activitytracker.app.text.ErrorTexts
import io.github.solcott.activitytracker.app.text.LabelText
import io.github.solcott.activitytracker.app.text.StatText
import io.github.solcott.activitytracker.data.training.ThresholdChange
import io.github.solcott.activitytracker.data.units.UnitSystem
import io.github.solcott.activitytracker.data.units.formatWhole
import io.github.solcott.activitytracker.lexicon.app.ActivityType
import io.github.solcott.activitytracker.lexicon.app.GearType
import io.github.solcott.activitytracker.lexicon.app.Threshold
import io.github.solcott.activitytracker.lexicon.app.Visibility
import kotlin.math.abs
import kotlin.time.Duration
import kotlin.time.Instant
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone

/**
 * What the import screen says that follows a rule (feed/batch-import, feed/import-duplicates, dev/shared-display-text): the picking
 * section, default titles, threshold changes, duplicate warnings and banners, a batch's summary, filters, rows and Save, the comparison,
 * the saved and after-import panels, time zones and their search, and the new devices. The Compose screen and the native one ask these.
 */
@Suppress("TooManyFunctions") // One screen's words, each a small rule.
object ImportActivityTexts {
    private const val BYTES_PER_MB = 1024L * 1024

    val hint: AppText = AppText.named("import_hint")
    val cancel: AppText = AppText.named("action_cancel")
    val retry: AppText = AppText.named("action_retry")
    val originalTooLarge: AppText = AppText.named("import_original_too_large")
    val savingTitle: AppText = AppText.named("import_saving_title")
    val viewUploads: AppText = AppText.named("import_view_uploads")
    val stoppedDismiss: AppText = AppText.named("import_stopped_dismiss")
    val thresholdsHeading: AppText = AppText.named("import_thresholds_heading")
    val tooFar: AppText = AppText.named("import_too_far")
    val detailsHint: AppText = AppText.named("import_dives_details_hint")
    val batchVisibilityHeading: AppText = AppText.named("import_batch_visibility_heading")
    val bulkHeading: AppText = AppText.named("import_bulk_duplicates")
    val itemTitle: AppText = AppText.named("import_item_title")
    val itemDone: AppText = AppText.named("import_item_done")
    val itemTitleLabel: AppText = AppText.named("import_item_title_label")
    val compareExact: AppText = AppText.named("import_compare_exact")
    val compareThis: AppText = AppText.named("import_compare_this")
    val batchSavedTitle: AppText = AppText.named("import_batch_saved_title")
    val suggestionsTitle: AppText = AppText.named("import_saved_suggestions_title")
    val suggestionsExplained: AppText = AppText.named("import_saved_suggestions_explained")
    val suggestionCombine: AppText = AppText.named("combine_suggestion_combine")
    val savedDone: AppText = AppText.named("import_saved_suggestions_done")
    val timeZone: AppText = AppText.named("import_time_zone")

    /** Import activity, or Add a recording when it adds one to an activity. */
    fun title(addsRecording: Boolean): AppText = AppText.named(if (addsRecording) "import_add_recording_title" else "import_title")

    /** Choose files, or Choose other files once something is read. */
    fun pick(picked: Boolean): AppText = AppText.named(if (picked) "import_pick_another" else "import_pick")

    /** Reading files…, with how many files and activities so far once it knows. */
    fun reading(progress: ReadProgress?): AppText =
        if (progress == null) AppText.named("import_reading")
        else AppText.named("import_reading_progress", whole(progress.files), whole(progress.activities))

    /** "Morning Mountain Bike Ride": the part of the day, then the type. */
    fun defaultTitle(title: DefaultTitle): AppText =
        AppText.named(
            when (title.partOfDay) {
                PartOfDay.Morning -> "default_title_morning"
                PartOfDay.Afternoon -> "default_title_afternoon"
                PartOfDay.Evening -> "default_title_evening"
                PartOfDay.Night -> "default_title_night"
            },
            LabelText.activityType(title.type),
        )

    /** A single activity's Save, or Saving… while it saves. */
    fun save(saving: Boolean): AppText = AppText.named(if (saving) "action_saving" else "action_save")

    fun savingProgress(saving: SavingImport): AppText =
        AppText.named("import_saving_progress", whole(saving.progress.queued), whole(saving.progress.total))

    fun stopped(stopped: StoppedImport): AppText = AppText.named("import_stopped", whole(stopped.saved), whole(stopped.total))

    /** "Threshold power: 231 W → 240 W", or "Run · Threshold power: …" for a sport's own threshold. */
    fun thresholdChange(change: ThresholdChange): AppText {
        val name = thresholdName(change.threshold)
        val from = thresholdValue(change.threshold, change.from)
        val to = thresholdValue(change.threshold, change.to)
        return change.sport?.let { AppText.named("import_threshold_change_sport", LabelText.sport(it), name, from, to) }
            ?: AppText.named("import_threshold_change", name, from, to)
    }

    private fun thresholdName(threshold: String): AppText =
        when (threshold) {
            Threshold.FUNCTIONAL_THRESHOLD_POWER -> AppText.named("threshold_ftp")
            Threshold.MAX_HEART_RATE -> AppText.named("threshold_max_heart_rate")
            Threshold.THRESHOLD_HEART_RATE -> AppText.named("threshold_threshold_heart_rate")
            Threshold.RESTING_HEART_RATE -> AppText.named("threshold_resting_heart_rate")
            else -> AppText.written(threshold)
        }

    private fun thresholdValue(threshold: String, value: Long?): AppText =
        when {
            value == null -> AppText.named("value_missing")
            threshold == Threshold.FUNCTIONAL_THRESHOLD_POWER -> AppText.named("unit_watts", whole(value))
            else -> AppText.named("unit_bpm", whole(value))
        }

    /** "Adds this file as another recording of: Lunch loop · 2024-05-14 12:01 · Garmin Fenix 7". */
    fun addingTo(target: ExistingActivityPreview): AppText = AppText.named("import_adding_to", described(target))

    /** "Lunch loop · 2024-05-14 12:01 · Garmin Fenix 7", Untitled activity for one with no title. */
    private fun described(activity: ExistingActivityPreview): AppText =
        listOfNotNull(
                activity.title?.let(AppText::written) ?: AppText.named("import_duplicate_untitled"),
                AppText.written(activity.start),
                activity.device?.let(AppText::written),
            )
            .let(::dotted)

    /**
     * Why an activity looks like a duplicate ("Already in your log: …", "Same activity as #3 in this import"). [inFileNumber] numbers the
     * earlier activity a duplicate within the batch repeats; [inFileNumber] of the batch gives it for a row.
     */
    fun duplicateWarning(info: DuplicateInfo, inFileNumber: Int? = info.inBatchIndex?.plus(1)): AppText {
        val existing =
            info.existing
                ?: return AppText.named(
                    if (info.exactCopy) "import_duplicate_copy_in_file" else "import_duplicate_in_file",
                    AppText.written((inFileNumber ?: 0).toString()),
                )
        val details = described(existing)
        return when {
            info.hoursApart != 0 -> AppText.named("import_duplicate_shifted", details, AppText.written(abs(info.hoursApart).toString()))
            info.exactCopy -> AppText.named("import_duplicate_copy_existing", details)
            else -> AppText.named("import_duplicate_existing", details)
        }
    }

    /** What a duplicate offers, in order: Combine left out while the logged activity is still uploading. */
    fun duplicateChoices(info: DuplicateInfo): List<DuplicateChoice> =
        info.actions.filter { it != DuplicateAction.Combine || !info.stillUploading }.map(::DuplicateChoice)

    /** Every action, for setting every shown duplicate at once. */
    val bulkChoices: List<DuplicateChoice>
        get() = DuplicateAction.entries.map(::DuplicateChoice)

    fun action(action: DuplicateAction): AppText =
        AppText.named(
            when (action) {
                DuplicateAction.Ignore -> "duplicate_action_ignore"
                DuplicateAction.ImportAnyway -> "duplicate_action_import_anyway"
                DuplicateAction.Combine -> "duplicate_action_combine"
            },
        )

    /** What the chosen action does, where it needs saying. */
    fun duplicateExplained(info: DuplicateInfo): AppText? =
        when {
            info.exactCopy && info.action == DuplicateAction.Ignore -> AppText.named("import_copy_explained")
            info.stillUploading -> AppText.named("import_combine_uploading")
            info.action == DuplicateAction.Combine -> AppText.named("import_combine_explained")
            else -> null
        }

    /**
     * The duplicate banner: the search running or failed, how many duplicates of each kind, and what happens to them, as sentences. Null
     * when there's nothing to say.
     */
    fun duplicateBanner(check: DuplicateCheck, existing: Int, inFile: Int, copies: Int = 0): AppText? =
        listOfNotNull(
                AppText.named("import_duplicates_checking").takeIf { check == DuplicateCheck.Checking },
                AppText.named("import_duplicates_failed").takeIf { check == DuplicateCheck.Failed },
                plural("import_duplicates_existing", existing).takeIf { existing > 0 },
                plural("import_duplicates_in_file", inFile).takeIf { inFile > 0 },
                plural("import_duplicates_copies", copies).takeIf { copies > 0 },
                AppText.named("import_duplicates_unchecked").takeIf { existing + inFile > 0 },
            )
            .takeIf { it.isNotEmpty() }
            ?.let(::spaced)

    /** A single activity's banner: only the search's progress or failure. */
    fun duplicateBanner(state: ImportActivityState): AppText? =
        if (state.duplicateCheck == DuplicateCheck.Done) null else duplicateBanner(state.duplicateCheck, 0, 0)

    /** A batch's banner. */
    fun batchDuplicateBanner(state: ImportActivityState, batch: ImportBatch): AppText? =
        duplicateBanner(state.duplicateCheck, batch.existingDuplicates, batch.inBatchDuplicates, batch.exactCopies)

    /** The banner offers Retry after the search failed. */
    fun offersRetry(check: DuplicateCheck): Boolean = check == DuplicateCheck.Failed

    /** "5 activities found". */
    fun found(batch: ImportBatch): AppText = plural("import_batch_found", batch.rows.size)

    /** What an account export brings, and whether it says who could see each activity. */
    fun exportNote(export: BatchExport): AppText =
        AppText.named(
            when (export.kind) {
                ExportKind.Strava if export.privacy -> "import_batch_strava"
                ExportKind.Strava -> "import_batch_strava_no_privacy"
                ExportKind.Garmin if export.privacy -> "import_batch_garmin"
                ExportKind.Garmin -> "import_batch_garmin_no_privacy"
            },
        )

    /** "2 files couldn't be read: 4410.tcx.gz (damaged), notes.xml (not a file this app reads)"; null when none were skipped. */
    fun skipped(batch: ImportBatch): AppText? {
        if (batch.skipped.isEmpty()) return null
        val names =
            batch.skipped.map { file ->
                AppText.named(
                    when (file.reason) {
                        SkipReason.Unsupported -> "import_skip_unsupported"
                        SkipReason.Damaged -> "import_skip_damaged"
                        SkipReason.TooLarge -> "import_skip_too_large"
                    },
                    AppText.written(file.name),
                )
            }
        return AppText.named("text_space_pair", plural("import_batch_skipped", batch.skipped.size), commaList(names))
    }

    fun ignored(batch: ImportBatch): AppText? = plural("import_batch_ignored", batch.ignoredFiles).takeIf { batch.ignoredFiles > 0 }

    fun originalsTooLarge(batch: ImportBatch): AppText? =
        batch.originalsTooLarge
            .takeIf { it.isNotEmpty() }
            ?.let { AppText.named("import_batch_originals_too_large", commaList(it.map(AppText::written))) }

    /** All, New and Duplicates. */
    val filters: List<FilterChoice>
        get() = BatchFilter.entries.map(::FilterChoice)

    fun filter(filter: BatchFilter): AppText =
        AppText.named(
            when (filter) {
                BatchFilter.All -> "import_filter_all"
                BatchFilter.New -> "import_filter_new"
                BatchFilter.Duplicates -> "import_filter_duplicates"
            },
        )

    /** "3 of 5 selected". */
    fun selected(batch: ImportBatch): AppText = AppText.named("import_dives_selected", whole(batch.selectedCount), whole(batch.rows.size))

    /** Select all or Deselect all for the rows shown; null when none of them can be. */
    fun selectAll(batch: ImportBatch): AppText? =
        if (batch.selectable.isEmpty()) null else AppText.named(if (batch.allShownSelected) "import_deselect_all" else "import_select_all")

    /** The action for every shown duplicate shows on the Duplicates filter while any show. */
    fun offersBulkActions(batch: ImportBatch): Boolean = batch.filter == BatchFilter.Duplicates && batch.shown.isNotEmpty()

    /** The rows shown, by the month they started in, in order, each headed "May 2024". */
    fun months(batch: ImportBatch): List<ImportMonth> =
        batch.shown.groupBy { it.month }.map { (month, rows) -> ImportMonth(month, monthHeading(month), rows.toImmutableList()) }

    private fun monthHeading(month: LocalDate): AppText =
        AppText.named("import_batch_month", AppText.named("month_short_${month.month.ordinal + 1}"), AppText.written(month.year.toString()))

    /** A row's title, else its default title. */
    fun rowTitle(row: ImportRow): AppText = row.title?.let(AppText::written) ?: defaultTitle(row.defaultTitle)

    /** "2024-05-12 07:02 · Ride · 42.1 km · 1:32:10 · Edge 840": a dive's depth and number in place of distance. */
    fun rowDetails(row: ImportRow, units: UnitSystem): AppText =
        dotted(
            listOfNotNull(
                AppText.written(row.start),
                LabelText.activityType(row.type),
                row.dive?.maxDepthMeters?.let { StatText.depth(it, units) } ?: row.distanceMeters?.let { StatText.distance(it, units) },
                AppText.written(StatText.duration(row.elapsed.inWholeSeconds)),
                row.dive?.diveNumber?.let { AppText.named("dive_number", AppText.written(it.toString())) },
                row.device?.let(AppText::written),
            ),
        )

    /** Why a row saves narrower than the batch: its export marked it private or followers only. */
    fun narrowed(row: ImportRow): AppText? =
        row.narrowedTo?.let {
            AppText.named(if (it == Visibility.SELF) "import_batch_narrowed_self" else "import_batch_narrowed_followers")
        }

    /** The number a row's duplicate warning gives what it repeats: its dive computer's number where it has one, else its place. */
    fun inFileNumber(batch: ImportBatch, row: ImportRow): Int? =
        row.duplicate?.inBatchIndex?.let { batch.rows.getOrNull(it)?.dive?.diveNumber ?: (it + 1) }

    /** How a saved batch uploads: phones keep going in the background (data/outbox), desktop and web only while the app is open. */
    fun uploadingNote(mobile: Boolean): AppText = AppText.named(if (mobile) "import_batch_uploading_mobile" else "import_batch_uploading")

    /** "Up to 4 MB of original files stay on this device until they're uploaded.", then how they upload; null when none are kept. */
    fun originalsSize(batch: ImportBatch, mobile: Boolean): AppText? {
        if (batch.originalsBytes <= 0) return null
        val megabytes = AppText.named("import_megabytes", whole((batch.originalsBytes + BYTES_PER_MB - 1) / BYTES_PER_MB))
        return AppText.named("text_space_pair", AppText.named("import_batch_originals_size", megabytes), uploadingNote(mobile))
    }

    /** A save that stopped short, else anything else saving refused. */
    fun saveFailure(state: ImportActivityState, batch: ImportBatch): AppText? {
        val failure = batch.saveFailure ?: return state.error?.let(ErrorTexts::of)
        return AppText.named("import_batch_save_failed", whole(failure.saved), whole(failure.total), ErrorTexts.of(failure.error))
    }

    /** "Save 3 activities", its progress while saving, or Import with nothing selected. */
    fun saveBatch(state: ImportActivityState, batch: ImportBatch): AppText {
        val progress = batch.progress
        return when {
            progress != null -> AppText.named("import_batch_saving", whole(progress.queued), whole(progress.total))
            state.saving -> AppText.named("action_saving")
            batch.selectedCount == 0 -> AppText.named("import_dives_none")
            else -> plural("import_batch_save", batch.selectedCount)
        }
    }

    /** Each untitled row's default title in words, by index, as [SaveBatch][ImportActivityEvent.SaveBatch] takes them. */
    fun defaultTitles(batch: ImportBatch, words: (DefaultTitle) -> String): Map<Int, String> {
        val untitled = batch.rows.filter { it.title == null }
        val labels = untitled.map { it.defaultTitle }.distinct().associateWith(words)
        return untitled.associate { it.index to labels.getValue(it.defaultTitle) }
    }

    /** Earlier in this import, or Already in your log. */
    fun compareOther(duplicate: DuplicateInfo): AppText =
        AppText.named(if (duplicate.existing != null) "import_compare_logged" else "import_compare_in_import")

    /** A batch row's sport, start, length, distance and device, a dash for any unknown, beside [compareMatched]'s. */
    fun compareRow(row: ImportRow, units: UnitSystem): List<AppText> =
        compareValues(row.type, row.start, row.elapsed, row.distanceMeters, row.device, units)

    /** What a duplicate repeats, in [compareRow]'s order. */
    fun compareMatched(matched: ExistingActivityPreview, units: UnitSystem): List<AppText> =
        compareValues(matched.type, matched.start, matched.elapsed, matched.distanceMeters, matched.device, units)

    private fun compareValues(
        type: ActivityType?,
        start: String,
        elapsed: Duration?,
        meters: Double?,
        device: String?,
        units: UnitSystem,
    ): List<AppText> {
        val missing = AppText.named("value_missing")
        return listOf(
            type?.let(LabelText::activityType) ?: missing,
            AppText.written(start),
            elapsed?.let { AppText.written(StatText.duration(it.inWholeSeconds)) } ?: missing,
            meters?.let { StatText.distance(it, units) } ?: missing,
            device?.let(AppText::written) ?: missing,
        )
    }

    /** "40 activities saved.", how many couldn't be read again, and how they upload. */
    fun batchSaved(result: BatchSaved, mobile: Boolean): AppText =
        spaced(
            listOfNotNull(
                plural("import_batch_saved", result.saved),
                plural("import_batch_saved_skipped", result.skipped).takeIf { result.skipped > 0 },
                uploadingNote(mobile),
            ),
        )

    fun suggestionCue(suggestion: SavedSuggestion): AppText = RecordingTexts.combineCue(suggestion.cue)

    /** "Blue Hole → Morning Dive (Today at 9:30 AM · 52:00)": the saved activity, then what it likely belongs with. */
    fun suggestionRow(suggestion: SavedSuggestion, now: Instant, zone: TimeZone): AppText {
        val saved =
            suggestion.title?.let(AppText::written)
                ?: FigureTexts.value(
                    FigureValue.StartTime(suggestion.startEpochSeconds, null, dateOnly = false),
                    UnitSystem.Metric,
                    now,
                    zone,
                )
        return AppText.named("import_saved_suggestion_row", saved, RecordingTexts.suggestedActivity(suggestion.other, now, zone))
    }

    val afterTitle: AppText = AppText.named("after_import_title")
    val afterLinksNotSaved: AppText = AppText.named("after_import_links_not_saved")
    val afterDone: AppText = AppText.named("after_import_done")
    val pastApply: AppText = AppText.named("past_imports_apply")
    val pastSkip: AppText = AppText.named("past_imports_skip")

    /** "Add Tarmac to 10 earlier imports?". */
    fun pastOffer(past: PastImportsOffer): AppText =
        AppText.Plural(
            "past_imports_offer",
            past.count,
            persistentListOf(AppText.written(past.count.toString()), commaList(past.gear.map(AppText::written))),
        )

    fun pastProgress(past: PastImportsOffer): AppText =
        AppText.named("past_imports_progress", AppText.written(past.done.toString()), AppText.written(past.count.toString()))

    /** That the file's clock has no zone, so it's read in the one below; for a batch's file, which file. */
    fun zoneNotice(clock: ImportClock, fileName: String? = null): AppText =
        if (fileName == null) AppText.named("import_time_zone_notice", AppText.written(clock.localStart))
        else AppText.named("import_time_zone_notice_file", AppText.written(fileName), AppText.written(clock.localStart))

    /** "America/New York (UTC-04:00)". */
    fun zone(zone: TimeZoneOption): AppText =
        AppText.named("import_time_zone_option", AppText.written(cityName(zone)), AppText.written(zone.offset))

    /** The zones [query] finds by part of their city ("cancun", "new york") or offset ("utc+8"); every zone for a blank query. */
    fun zones(clock: ImportClock, query: String): List<TimeZoneOption> {
        val key = searchKey(query.trim())
        return if (key.isEmpty()) clock.zones else clock.zones.filter { key in searchKey("${cityName(it)} ${it.offset}") }
    }

    /** Zone ids spell spaces as underscores. */
    private fun cityName(zone: TimeZoneOption) = zone.id.replace('_', ' ')

    /** Folds case, and lets "utc+8" find "UTC+08:00". */
    private fun searchKey(text: String): String = text.lowercase().replace("+0", "+").replace("-0", "-")

    val newDevicesTitle: AppText = AppText.named("new_devices_title")
    val newDevicesHelp: AppText = AppText.named("new_devices_help")
    val deviceNameLabel: AppText = AppText.named("new_device_name")
    val deviceTypeLabel: AppText = AppText.named("new_device_type")
    val deviceKindLabel: AppText = AppText.named("new_device_kind")
    val deviceMountLabel: AppText = AppText.named("new_device_mount_on")
    val deviceCollectionsLabel: AppText = AppText.named("new_device_collections")
    val deviceKindNone: AppText = AppText.named("new_device_kind_none")
    val deviceMountNone: AppText = AppText.named("new_device_mount_none")
    val profileLinkHelp: AppText = AppText.named("profile_link_help")
    val profileLinkNone: AppText = AppText.named("profile_link_none")

    /** "Garmin Edge 840". */
    fun deviceName(card: NewDeviceCard): AppText = AppText.written(deviceName(card.manufacturer, card.model))

    /** "Recording device · Serial …2071 · Known only by its model…"; null when there's nothing to say. */
    fun deviceAbout(card: NewDeviceCard): AppText? =
        listOfNotNull(
                AppText.named("new_device_recorder").takeIf { card.recorder },
                card.serialTail?.let { AppText.named("new_device_serial", AppText.written(it)) },
                AppText.named("new_device_weak").takeIf { card.weak },
            )
            .takeIf { it.isNotEmpty() }
            ?.let(::dotted)

    /** Later, Add as gear and Not gear. */
    val deviceDecisions: List<DeviceDecisionChoice>
        get() = NewDeviceDecision.entries.map(::DeviceDecisionChoice)

    fun decision(decision: NewDeviceDecision): AppText =
        AppText.named(
            when (decision) {
                NewDeviceDecision.Later -> "new_device_later"
                NewDeviceDecision.Add -> "new_device_add"
                NewDeviceDecision.NotGear -> "new_device_not_gear"
            },
        )

    /** Every gear type a new item can be. */
    val gearTypes: List<String>
        get() = GearType.all

    /** [type]'s kinds; none for a type without kinds. */
    fun gearKinds(type: String): List<String> = GearType.kinds(type)

    /** The card's kind, None when it has none. */
    fun deviceKind(card: NewDeviceCard): AppText = card.kind?.let { GearTexts.kind(card.type, it) } ?: deviceKindNone

    /** What the new item is mounted on, Nothing when it isn't. */
    fun deviceMount(card: NewDeviceCard, fields: NewDevicesFields): AppText =
        fields.mountOptions.firstOrNull { it.uri == card.mountOn }?.name?.let(AppText::written) ?: deviceMountNone

    fun profileLinkTitle(offer: ProfileLinkOffer): AppText = AppText.named("profile_link_title", AppText.written(offer.name))

    /** The item the profile links to, Don't link while none. */
    fun profileLinked(offer: ProfileLinkOffer): AppText =
        offer.options.firstOrNull { it.uri == offer.chosen }?.name?.let(AppText::written) ?: profileLinkNone

    /** "Sensors matched more than one Bike: Tarmac, Stumpjumper. Remove any you didn't use." */
    fun gearConflict(conflict: GearConflict): AppText =
        AppText.named("gear_conflict", GearTexts.type(conflict.type), commaList(conflict.names.map(AppText::written)))

    private fun whole(value: Int): AppText = AppText.written(formatWhole(value))

    private fun whole(value: Long): AppText = AppText.written(formatWhole(value))

    private fun plural(name: String, count: Int): AppText = AppText.Plural(name, count, persistentListOf(whole(count)))

    private fun spaced(texts: List<AppText>): AppText = texts.reduce { a, b -> AppText.named("text_space_pair", a, b) }

    private fun dotted(texts: List<AppText>): AppText = texts.reduce { a, b -> AppText.named("text_dot_pair", a, b) }

    /** Names joined with commas ("a, b, c"). */
    private fun commaList(texts: List<AppText>): AppText = AppText.joined(texts) ?: AppText.written("")
}

/** A duplicate's action as a choice, since a list of enums can't reach Swift. */
@Immutable data class DuplicateChoice(val action: DuplicateAction)

/** A batch filter as a choice. */
@Immutable data class FilterChoice(val filter: BatchFilter)

/** A new device's decision as a choice. */
@Immutable data class DeviceDecisionChoice(val decision: NewDeviceDecision)

/** The rows shown from one month, the first day of which is [month], headed [heading]. */
@Immutable data class ImportMonth(val month: LocalDate, val heading: AppText, val rows: ImmutableList<ImportRow>)
