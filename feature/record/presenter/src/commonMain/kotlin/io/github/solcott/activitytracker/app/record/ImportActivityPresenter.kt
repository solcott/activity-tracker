package io.github.solcott.activitytracker.app.record

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.slack.circuit.codegen.annotations.CircuitInject
import com.slack.circuit.runtime.Navigator
import io.github.solcott.activitytracker.activityfile.ActivityFileException
import io.github.solcott.activitytracker.activityfile.ActivityFileReader
import io.github.solcott.activitytracker.activityfile.BatchReader
import io.github.solcott.activitytracker.activityfile.ByteArrayImportSource
import io.github.solcott.activitytracker.activityfile.EntryRef
import io.github.solcott.activitytracker.activityfile.ImportSource
import io.github.solcott.activitytracker.activityfile.ImportedActivity
import io.github.solcott.activitytracker.activityfile.ImportedDive
import io.github.solcott.activitytracker.activityfile.ImportedPower
import io.github.solcott.activitytracker.activityfile.ImportedShifting
import io.github.solcott.activitytracker.activityfile.SkipReason
import io.github.solcott.activitytracker.activityfile.SourceFile
import io.github.solcott.activitytracker.activityfile.TimeZones
import io.github.solcott.activitytracker.activityfile.UnsupportedActivityFileException
import io.github.solcott.activitytracker.activityfile.localZone
import io.github.solcott.activitytracker.app.UiError
import io.github.solcott.activitytracker.app.detail.ActivityPage
import io.github.solcott.activitytracker.app.detail.ActivityPageContent
import io.github.solcott.activitytracker.app.edit.rememberDetailsForm
import io.github.solcott.activitytracker.app.rememberSingleFlight
import io.github.solcott.activitytracker.app.visibility.rememberNewActivityVisibility
import io.github.solcott.activitytracker.app.visibility.uiErrorOf
import io.github.solcott.activitytracker.atproto.runSuspendCatching
import io.github.solcott.activitytracker.data.activity.ActivityDefaults
import io.github.solcott.activitytracker.data.activity.ActivityDraft
import io.github.solcott.activitytracker.data.activity.ActivityManager
import io.github.solcott.activitytracker.data.activity.ActivityRepository
import io.github.solcott.activitytracker.data.activity.CombineSuggestions
import io.github.solcott.activitytracker.data.activity.matchSpan
import io.github.solcott.activitytracker.data.db.ImportSaveEntity
import io.github.solcott.activitytracker.data.di.SessionScope
import io.github.solcott.activitytracker.data.feed.FeedRepository
import io.github.solcott.activitytracker.data.importsave.ImportCounts
import io.github.solcott.activitytracker.data.importsave.ImportSaveRunner
import io.github.solcott.activitytracker.data.importsave.ImportSaveStore
import io.github.solcott.activitytracker.data.importsave.ImportSaves
import io.github.solcott.activitytracker.data.profile.OwnProfile
import io.github.solcott.activitytracker.data.profile.ProfileRepository
import io.github.solcott.activitytracker.data.profile.applyDeviceThresholds
import io.github.solcott.activitytracker.data.streams.StreamAnalytics
import io.github.solcott.activitytracker.data.training.ThresholdChange
import io.github.solcott.activitytracker.data.training.ThresholdUpdates
import io.github.solcott.activitytracker.data.units.UnitsRepository
import io.github.solcott.activitytracker.lexicon.Tid
import io.github.solcott.activitytracker.lexicon.app.ActivityType
import io.github.solcott.activitytracker.lexicon.app.FollowPolicy
import io.github.solcott.activitytracker.lexicon.app.Sport
import io.github.solcott.activitytracker.lexicon.app.TrainingDetails
import io.github.solcott.activitytracker.textfield.clearText
import io.github.solcott.activitytracker.textfield.rememberTextFieldState
import io.github.solcott.activitytracker.textfield.setTextAndPlaceCursorAtEnd
import kotlin.math.roundToLong
import kotlin.time.Instant
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableSet
import kotlinx.coroutines.Job
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.format.char
import kotlinx.datetime.offsetAt
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime

@CircuitInject(ImportActivityScreen::class, SessionScope::class)
@Composable
@Suppress("CyclomaticComplexMethod", "LongMethod") // One branch per event; a single file and a batch share the screen.
fun ImportActivityPresenter(
    screen: ImportActivityScreen,
    navigator: Navigator,
    activities: ActivityRepository,
    manager: ActivityManager,
    reader: ActivityFileReader,
    typeChoices: ActivityTypeChoices,
    gearChoices: GearChoices,
    newDeviceChoices: NewDeviceChoices,
    profiles: ProfileRepository,
    feed: FeedRepository,
    defaults: ActivityDefaults,
    units: UnitsRepository,
    suggestions: CombineSuggestions,
    saveStore: ImportSaveStore,
    saves: ImportSaves,
    saveRunner: ImportSaveRunner,
): ImportActivityState {
    // The parsed file isn't saveable; after process death the athlete picks it again. A single activity, or for adding a recording, a
    // logbook's several.
    var file by remember { mutableStateOf<ImportFile?>(null) }
    // The file as it's kept with every activity saved from it; null when it's too large to keep (originalTooLarge).
    var source by remember { mutableStateOf<SourceFile?>(null) }
    var originalTooLarge by remember { mutableStateOf(false) }
    // Several activities: the picked files, read again as needed, and what's kept of each activity.
    var batchFiles by remember { mutableStateOf<BatchReader?>(null) }
    var scanned by remember { mutableStateOf<ScannedBatch?>(null) }
    var readProgress by remember { mutableStateOf<ReadProgress?>(null) }
    var readJob by remember { mutableStateOf<Job?>(null) }
    // Where each of a batch's zone-less files was, by file; this device's zone until the athlete says.
    var fileZones by remember { mutableStateOf(mapOf<Int, String>()) }
    var filter by remember { mutableStateOf(BatchFilter.All) }
    var opened by remember { mutableStateOf<Int?>(null) }
    var openedActivity by remember { mutableStateOf<ImportedActivity?>(null) }
    var openedError by remember { mutableStateOf<UiError?>(null) }
    // The picked files a batch was read from, which its save keeps.
    var pickedSources by remember { mutableStateOf<List<ImportSource>>(emptyList()) }
    // The batch save this screen started, followed by id: it's persisted and runs app-wide, outliving the screen (feed/batch-import).
    var savingBatch by rememberSaveable { mutableStateOf<String?>(null) }
    var savingDevices by remember { mutableStateOf<NewDevicesSaved?>(null) }
    var batchSaved by remember { mutableStateOf<BatchSaved?>(null) }
    // Which of the activities import, what to do with duplicates, and the athlete's changes to a batch's activities.
    var selection by remember { mutableStateOf(BatchSelection()) }
    val typeChoice = typeChoices.produce(ActivityType(Sport.OTHER))
    val type = typeChoice.selected
    val openedType = typeChoices.produce(ActivityType(Sport.OTHER))
    val openedTitle = rememberTextFieldState()
    // The import is saved: what's left to say before leaving, and how it leaves.
    var afterImport by remember { mutableStateOf<AfterImport?>(null) }
    var leave by remember { mutableStateOf<(suspend () -> Unit)?>(null) }
    val title = rememberTextFieldState()
    val details = rememberDetailsForm()
    // The title the last file filled in, so a file without one clears it but not what the athlete typed.
    var fileTitle by remember { mutableStateOf<String?>(null) }
    // Where a zone-less file's clock was; null for this device's zone.
    var zoneId by rememberSaveable { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<UiError?>(null) }
    var savedSuggestions by remember { mutableStateOf<ImmutableList<SavedSuggestion>?>(null) }
    val flights = rememberSingleFlight()
    val saveCounts by saveStore.counts.collectAsState(null)
    val savesRunning by saves.running.collectAsState()
    val saveFailures by saves.failures.collectAsState()
    val ownSave = savingBatch?.let { id -> saveCounts?.firstOrNull { it.batchId == id } }
    val saveFailed = savingBatch?.takeIf { it !in savesRunning }?.let(saveFailures::get)
    val saveProgress =
        ownSave
            ?.takeIf { saveFailed == null && it.state == ImportSaveEntity.STATE_RUNNING }
            ?.let { SaveProgress(it.done + it.skipped, it.total) }
    val saving = flights.isRunning(ImportActivityEvent.Save::class) || saveProgress != null

    val zone = remember(zoneId) { zoneId?.let(TimeZone::of) ?: TimeZones.device() }
    val zoneOf = remember(fileZones) { { file: Int -> fileZones[file]?.let(TimeZone::of) ?: TimeZones.device() } }
    // Adding a recording to an activity: it, once loaded, and of a logbook only the entry nearest it.
    val adding = rememberAddingRecording(feed, screen, file, zone) { error = it }
    val picked = adding.offered
    val imported = singleActivity(picked)
    val placed = remember(imported, zone) { imported?.let { (name, activity) -> NamedActivity(name, activity.inZone(zone)) } }
    val placedBatch = remember(scanned, zoneOf) { scanned?.placed(zoneOf) }
    val zones = remember(file) { file?.activities?.firstNotNullOfOrNull { it.localStart }?.let(::zoneOptions) }
    // Searched around the activities as read: the search's margin covers any zone the athlete places them in.
    val spans =
        remember(picked, scanned) { (scanned?.items?.map { it.summary } ?: picked?.activities)?.map { it.matchSpan() }?.toImmutableList() }
    val own = rememberOwnActivities(feed, spans, search = !adding.requested)
    // Matched where the athlete placed the clock: a zone change can make or break a duplicate.
    val matches =
        remember(placed, placedBatch, own) {
            importMatches(placedBatch?.let { scanned?.spans(it) } ?: listOfNotNull(placed?.activity).spans(), own)
        }
    val tooFar = adding.tooFar(placed?.activity)
    // Keyed on the zone too: a zone with the same offset leaves the activity as it was.
    val preview = remember(placed, zones, zone, matches, selection) { singlePreview(placed, zone, zones, matches, selection) }
    val athlete = rememberOwnProfile(profiles)
    val batchSelected =
        remember(scanned, selection, matches) {
            scanned?.items?.indices?.filter { selection.isSelected(it, matches.getOrNull(it)) }.orEmpty()
        }
    // What each activity's file says about its gear: a batch's selected activities share the gear, which starts from the first one's.
    val gearSources =
        remember(placed, placedBatch, batchSelected) {
            placed?.activity?.let { persistentListOf(GearSource.of(it)) }
                ?: batchSelected.mapNotNull { placedBatch?.getOrNull(it)?.let(GearSource::of) }.toImmutableList()
        }
    // A batch's gear starts from the defaults for its first selected activity's type.
    val gearType = scanned?.let { batch -> batchSelected.firstOrNull()?.let { batch.items[it].type(selection.overrides[it]) } } ?: type
    val gear = gearChoices.produce(gearType, gearSources)
    val newDevices = newDeviceChoices.produce(gear, gearSources)
    val held =
        remember(placed, scanned, batchSelected, type) {
            scanned?.let { batch ->
                val items = batchSelected.map { batch.items[it] }
                ImportCategories(items.flatMap { it.categories }.toImmutableSet(), items.any { it.runningForm })
            } ?: importCategories(placed?.activity, type, null) { false }
        }
    val offered = gear.offering(held.categories)
    val visibility =
        rememberNewActivityVisibility(defaults, offered, runningForm = held.runningForm) { athlete?.followPolicy == FollowPolicy.APPROVE }
    val training = athlete?.details?.training
    val thresholdChanges = remember(placed, training, type.sport) { pendingThresholds(training, placed?.activity, type.sport) }
    val defaultTitle =
        placed
            ?.activity
            ?.takeIf { it.title == null }
            ?.let { DefaultTitle(partOfDay(it.startedAt.toLocalDateTime(it.localZone(zone)).hour), type) }
    val unitSystem by units.units.collectAsState()
    // What the activity's page will show its owner, rebuilt for the type and visibility chosen: the hero follows the sport.
    val page by
        produceState<ActivityPageContent?>(null, placed, type, training, thresholdChanges, visibility.current, adding, unitSystem) {
            value = placed?.let { (_, activity) ->
                val draft = importDraft(activity, type, title = null, training, thresholdChanges, source = null)
                ActivityPage.fromDraft(adding.placed(draft.withVisibility(visibility.current)), unitSystem)
            }
        }
    val rows =
        remember(scanned, placedBatch, matches, selection, visibility.current, zoneOf) {
            scanned?.let { batch -> batchRows(batch, placedBatch.orEmpty(), matches, selection, visibility.current.activity, zoneOf) }
        }
    // One of a batch's activities, opened: read again from its file, shown as it would publish with the type the athlete gives it.
    val openedFile = opened?.let { scanned?.items?.getOrNull(it) }?.file
    val openedZones = remember(openedActivity) { openedActivity?.localStart?.let(::zoneOptions) }
    val openedPage by
        produceState<ActivityPageContent?>(null, openedActivity, openedType.selected, training, visibility.current, unitSystem) {
            value = openedActivity?.let { activity ->
                val draft = importDraft(activity, openedType.selected, title = null, training, emptyList(), source = null)
                ActivityPage.fromDraft(draft.withVisibility(visibility.current), unitSystem)
            }
        }
    val batch = rows?.let { rowList ->
        val scan = checkNotNull(scanned)
        val openedRow = opened?.let(rowList::getOrNull)
        ImportBatch(
            rows = rowList,
            filter = filter,
            clocks = batchClocks(scan, placedBatch.orEmpty(), zoneOf),
            skipped = scan.skipped,
            ignoredFiles = scan.ignored,
            exports = scan.exports.toImmutableList(),
            originalsTooLarge = scan.files.filter { it.originalTooLarge }.map { it.name }.toImmutableList(),
            originalsBytes = batchSelected.map { scan.items[it].file }.distinct().sumOf { scan.files[it].originalBytes },
            preview =
                openedRow?.let { row ->
                    ImportItemPreview(
                        index = row.index,
                        title = openedTitle,
                        type = openedType.field,
                        defaultTitle = row.defaultTitle.copy(type = openedType.selected),
                        preview =
                            openedActivity?.let { activity ->
                                val fileZone = zoneOf(checkNotNull(openedFile))
                                activity.toPreview(row.fileName, fileZone, openedZones).copy(duplicate = row.duplicate)
                            },
                        page = openedPage.takeIf { openedActivity != null },
                        error = openedError,
                        matched =
                            row.duplicate?.let { duplicate ->
                                duplicate.existing ?: duplicate.inBatchIndex?.let(rowList::getOrNull)?.compared()
                            },
                    )
                },
            progress = saveProgress,
            saveFailure =
                saveFailed?.let { e ->
                    SaveFailure(uiErrorOf(UiError.Kind.SaveActivity, e), saved = ownSave?.done ?: 0, total = ownSave?.total ?: 0)
                },
        )
    }

    // The save this screen started ended: show what it saved, then what's left to offer. A failed one stays to save again.
    LaunchedEffect(savingBatch, savesRunning, saveCounts) {
        val id = savingBatch ?: return@LaunchedEffect
        if (id in savesRunning || saveCounts == null || saveStore.save(id) != null) return@LaunchedEffect
        val devices = savingDevices
        savingBatch = null
        savingDevices = null
        // Its own flight: clearing the save just restarts this effect.
        flights.launch(ImportActivityEvent.Saved::class) {
            val (saved, skipped) = saveStore.importCounts(id) ?: ImportCounts(0, 0)
            val small = saved <= MAX_SUGGESTED
            val next: suspend () -> Unit = {
                // A small batch is sent before it's summed up, as a single save is; a large one is the session's pacer's to send, waiting
                // out the PDS's limits whatever screen is open (data/outbox).
                if (small) runSuspendCatching { activities.sync() }
                batchSaved = BatchSaved(saved, skipped)
                // Suggestions ask the AppView about each saved activity: only for a batch small enough to look through.
                if (small) savedSuggestions = savedSuggestionsFor(saveStore.savedRecords(id), suggestions).takeIf { it.isNotEmpty() }
            }
            val after = devices?.let { newDevices.afterSave(it, saveStore.savedUris(id).toSet()) }
            if (after == null) next()
            else {
                leave = next
                afterImport = after
            }
        }
    }
    // A save that failed partway: what it saved or skipped leaves the selection, so saving again continues with the rest
    // (feed/batch-import).
    LaunchedEffect(saveFailed) {
        val id = savingBatch ?: return@LaunchedEffect
        if (saveFailed == null) return@LaunchedEffect
        selection = selection.without(saveStore.settledIndices(id), matches::getOrNull)
    }

    fun clear() {
        file = null
        source = null
        originalTooLarge = false
        batchFiles = null
        pickedSources = emptyList()
        scanned = null
        fileZones = emptyMap()
        filter = BatchFilter.All
        opened = null
        openedActivity = null
        selection = BatchSelection()
        zoneId = null
        error = null
    }

    /** Takes in a single file's activities, read whole, as the single-file form shows them. */
    fun takeSingle(name: String, read: List<ImportedActivity>, prepared: SourceFile?) {
        file = ImportFile(name, read.toImmutableList())
        source = prepared
        originalTooLarge = prepared == null
        val first = read.first()
        typeChoice.select(first.type)
        val named = first.title.takeIf { read.size == 1 }
        when {
            named != null -> title.setTextAndPlaceCursorAtEnd(named)
            fileTitle != null && title.text.toString() == fileTitle -> title.clearText()
        }
        fileTitle = named
        // The file's notes (a logbook's, GPX `desc`, TCX `Notes`) start the description, for the athlete to keep, change or clear.
        details.seed(
            description = first.description.takeIf { read.size == 1 },
            diveSite = first.dive?.header?.siteName.takeIf { read.size == 1 },
        )
    }

    fun pick(sources: List<ImportSource>) {
        // Drop the previous files first, so a failed pick can't leave their preview (and Save) behind.
        clear()
        // A newer pick replaces one still being read.
        readJob =
            flights.launchLatest(ImportActivityEvent.FilePicked::class) {
                runSuspendCatching {
                    val one = sources.singleOrNull()
                    if (adding.requested && one != null) {
                        // A recording to add is one file, a logbook's dives all offered to pick the nearest from.
                        val whole = BatchReader(sources, reader).read(EntryRef(0))
                        val (read, prepared) = reader.readKeeping(whole.name, whole.bytes)
                        takeSingle(whole.name, read.activities, prepared)
                        return@runSuspendCatching
                    }
                    val files = BatchReader(sources, reader)
                    val found = files.collect { readProgress = it }
                    val only = found.items.singleOrNull()
                    when {
                        found.items.isEmpty() ->
                            error =
                                if (found.skipped.all { it.reason == SkipReason.Unsupported }) UiError(UiError.Kind.UnsupportedActivityFile)
                                else UiError.of(UiError.Kind.ReadActivityFile, ActivityFileException("No activity could be read"))
                        only != null -> {
                            val one = files.read(found.files[only.file].ref)
                            val (read, prepared) = reader.readKeeping(one.name, one.bytes)
                            takeSingle(one.name, read.activities, prepared)
                        }
                        else -> {
                            batchFiles = files
                            pickedSources = sources
                            scanned = found
                            selection = BatchSelection(batch = true)
                        }
                    }
                }
                    .onFailure { e ->
                        error =
                            if (e is UnsupportedActivityFileException) UiError(UiError.Kind.UnsupportedActivityFile)
                            else UiError.of(UiError.Kind.ReadActivityFile, e)
                    }
                readProgress = null
            }
    }

    /** Keeps the opened activity's title and type as the athlete left them, unless they match what it had. */
    fun closeOpened() {
        val index = opened ?: return
        val item = scanned?.items?.getOrNull(index)
        if (item != null && openedActivity != null) {
            val shown = item.title(selection.overrides[index])
            val typed = openedTitle.text.toString()
            val override =
                ItemOverride(
                    title = typed.takeIf { it != shown.orEmpty() },
                    type = openedType.selected.takeIf { it != item.type(selection.overrides[index]) },
                )
            val previous = selection.overrides[index] ?: ItemOverride()
            selection = selection.withOverride(index, ItemOverride(override.title ?: previous.title, override.type ?: previous.type))
        }
        opened = null
        openedActivity = null
        openedError = null
    }

    return ImportActivityState(
        reading = flights.isRunning(ImportActivityEvent.FilePicked::class),
        preview = preview,
        type = typeChoice.field,
        title = title,
        defaultTitle = defaultTitle,
        saving = saving,
        error = error,
        thresholdChanges = thresholdChanges,
        visibility = visibility.fields,
        batch = batch,
        readProgress = readProgress,
        duplicateCheck = own.check,
        details = placed?.let { details.fields(isDive = type.sport == Sport.DIVE) },
        approvesFollowers = athlete?.followPolicy == FollowPolicy.APPROVE,
        originalTooLarge = originalTooLarge,
        page = page.takeIf { placed != null },
        addsRecording = adding.requested,
        addingTo = adding.preview(zone),
        tooFarFromTarget = tooFar,
        savedSuggestions = savedSuggestions,
        batchSaved = batchSaved,
        gear = gear.field.takeIf { picked != null || scanned != null },
        newDevices = if (picked != null || scanned != null) newDevices.fields else NewDevicesFields(),
        afterImport = afterImport,
        savingImport =
            saveCounts
                ?.takeIf { savingBatch == null && scanned == null && file == null }
                ?.firstOrNull { it.state == ImportSaveEntity.STATE_RUNNING }
                ?.let { SavingImport(SaveProgress(it.done + it.skipped, it.total)) },
        stoppedImports =
            saveCounts
                .orEmpty()
                .filter { it.state == ImportSaveEntity.STATE_STOPPED }
                .map { StoppedImport(it.batchId, it.done, it.total) }
                .toImmutableList(),
    ) { event ->
        when (event) {
            is ImportActivityEvent.FilePicked -> pick(listOf(ByteArrayImportSource(event.name, event.bytes.toByteArray())))

            is ImportActivityEvent.FilesPicked -> pick(event.sources)

            ImportActivityEvent.CancelReading -> {
                readJob?.cancel()
                readProgress = null
                clear()
            }

            is ImportActivityEvent.TimeZoneChanged -> {
                zoneId = event.id
            }

            is ImportActivityEvent.FileZoneChanged -> fileZones = fileZones + (event.key to event.id)

            is ImportActivityEvent.FilterChanged -> filter = event.filter

            is ImportActivityEvent.ItemOpened -> {
                closeOpened()
                val item = scanned?.items?.getOrNull(event.index) ?: return@ImportActivityState
                val row = rows?.getOrNull(event.index) ?: return@ImportActivityState
                opened = event.index
                openedTitle.setTextAndPlaceCursorAtEnd(row.title.orEmpty())
                openedType.select(row.type)
                val files = batchFiles ?: return@ImportActivityState
                val batchFile = checkNotNull(scanned).files[item.file]
                flights.launchLatest(ImportActivityEvent.ItemOpened::class) {
                    runSuspendCatching {
                        val read = reader.readFile(files.read(batchFile.ref).bytes)
                        val full = read.activities[item.entry]
                        openedActivity = if (batchFile.zoneless) full.inZone(zoneOf(item.file)) else full
                    }
                        .onFailure { e -> openedError = UiError.of(UiError.Kind.ReadActivityFile, e) }
                }
            }

            ImportActivityEvent.ItemClosed -> closeOpened()

            ImportActivityEvent.Back,
            is ImportActivityEvent.Saved -> navigator.leave(event)

            ImportActivityEvent.After.Done -> flights.launch(ImportActivityEvent.After.Done) { leave?.invoke() ?: navigator.pop() }

            ImportActivityEvent.After.ApplyToPast ->
                flights.launch(ImportActivityEvent.After.ApplyToPast) {
                    val offer = afterImport?.pastImports ?: return@launch
                    afterImport = afterImport?.copy(pastImports = offer.copy(applying = true, done = 0, failed = false))
                    runSuspendCatching {
                        newDevices.applyToPast { done ->
                            afterImport = afterImport?.copy(pastImports = offer.copy(applying = true, done = done))
                        }
                    }
                        .onSuccess { leave?.invoke() ?: navigator.pop() }
                        .onFailure { e ->
                            afterImport = afterImport?.copy(pastImports = offer.copy(failed = true))
                            error = uiErrorOf(UiError.Kind.ApplyGearToPast, e)
                        }
                }

            is ImportActivityEvent.ItemToggled -> selection = selection.toggled(event.index, matches.getOrNull(event.index))

            is ImportActivityEvent.AllShownSelected ->
                selection =
                    selection.all(
                        event.selected,
                        batch?.shown?.map { it.index }.orEmpty(),
                        matches::getOrNull,
                        duplicates = filter == BatchFilter.Duplicates,
                    )

            is ImportActivityEvent.DuplicateActionChanged -> selection = selection.withAction(event.index, event.action)

            is ImportActivityEvent.ShownDuplicatesAction ->
                selection = selection.withShownAction(event.action, batch?.shown?.map { it.index }.orEmpty(), matches::getOrNull)

            is ImportActivityEvent.SaveBatch ->
                flights.launch(ImportActivityEvent.Save::class) {
                    closeOpened()
                    val scan = scanned ?: return@launch
                    val start = gearSources.minOfOrNull { it.startedAt } ?: return@launch
                    val again = savingBatch
                    val linked =
                        savingDevices.takeIf { again != null }
                            ?: runSuspendCatching { newDevices.save(start) }
                                .getOrElse { e ->
                                    error = uiErrorOf(UiError.Kind.SaveNewGear, e)
                                    return@launch
                                }
                    runSuspendCatching {
                        val choices = BatchChoices(training, visibility.current, linked.gear, zoneOf, importBatch = again ?: Tid.next())
                        val plan = batchPlan(scan.items.size, matches, selection)
                        val first = again?.let { saveStore.nextPosition(it) } ?: 0
                        // An activity a failed run may have queued already keeps its record key, so it isn't queued twice.
                        val kept = again?.let { saveStore.pending(it) }.orEmpty()
                        val save =
                            newBatchSave(scan, pickedSources, plan, selection, event.defaultTitles, choices, scan.uploadSource, first, kept)
                        if (again == null) saveStore.start(save) else saveStore.replacePending(again, save.items)
                        // Followed only once it's written: until then, no plan would read as a save already finished.
                        savingDevices = linked
                        savingBatch = save.batchId
                        saves.launch(saveStore.account) { saveRunner.runAll() }
                    }
                        .onFailure { e -> error = uiErrorOf(UiError.Kind.SaveActivity, e) }
                }

            ImportActivityEvent.RetryDuplicateCheck -> own.retry()

            is ImportActivityEvent.StoppedImportDismissed ->
                flights.launch(event) { runSuspendCatching { saveStore.dismiss(event.batchId) } }

            is ImportActivityEvent.Save -> {
                val match = matches.firstOrNull()
                val activity = adding.saved(placed?.activity, selection.isSelected(0, match)) ?: return@ImportActivityState
                flights.launch(ImportActivityEvent.Save::class) {
                    // New gear, its mounts and collections go first: a failure keeps the preview, with nothing published.
                    val linked = runSuspendCatching {
                        newDevices.save(activity.startedAt)
                    }
                        .getOrElse { e ->
                            error = uiErrorOf(UiError.Kind.SaveNewGear, e)
                            return@launch
                        }
                    runSuspendCatching {
                        val text = title.text.toString().ifBlank { event.defaultTitle?.ifBlank { null } }
                        val draft =
                            importDraft(activity, type, text, training, thresholdChanges, adding.sourceOf(source))
                                .withDetails(details, isDive = type.sport == Sport.DIVE)
                                .copy(gear = linked.gear)
                                .withVisibility(visibility.current)
                        val saved = saveSingle(activities, manager, draft, adding.into(combineTarget(match, selection.action(0, match))))
                        updateThresholds(profiles, activity, type.sport, previewed = thresholdChanges)
                        val after = newDevices.afterSave(linked, setOf(saved.uri))
                        if (after == null) navigator.openSaved(saved, adding)
                        else {
                            leave = { navigator.openSaved(saved, adding) }
                            afterImport = after
                        }
                    }
                        .onFailure { e -> error = uiErrorOf(UiError.Kind.SaveActivity, e) }
                }
            }
        }
    }
}

/** Saved batches this size or smaller are looked through for combine suggestions. */
private const val MAX_SUGGESTED = 50

/** The thresholds saving [activity] as [sport] would update from its device's settings; none until [training] loads. */
private fun pendingThresholds(training: TrainingDetails?, activity: ImportedActivity?, sport: String): ImmutableList<ThresholdChange> =
    ThresholdUpdates.pending(training, activity?.deviceThresholds, activity?.startedAt ?: Instant.DISTANT_PAST, sport).toImmutableList()

/**
 * The athlete's profile (their thresholds and follow policy), read once; null until it loads, and when it can't (offline, say), so the
 * import still works.
 */
@Composable
private fun rememberOwnProfile(profiles: ProfileRepository): OwnProfile? {
    var own by remember { mutableStateOf<OwnProfile?>(null) }
    LaunchedEffect(Unit) { runSuspendCatching { profiles.ownProfile() }.onSuccess { own = it } }
    return own
}

/**
 * Updates thresholds from a saved [activity], when the athlete was shown changes ([previewed]). The activity is saved either way; a failed
 * update leaves the thresholds for the zones screen.
 */
private suspend fun updateThresholds(
    profiles: ProfileRepository,
    activity: ImportedActivity,
    sport: String,
    previewed: List<ThresholdChange>,
) {
    if (previewed.isEmpty()) return
    runSuspendCatching { profiles.applyDeviceThresholds(activity.deviceThresholds, activity.startedAt, sport, activity.device) }
}

@Suppress("MagicNumber") // Hours of the day.
internal fun partOfDay(hour: Int) =
    when (hour) {
        in 5..11 -> PartOfDay.Morning
        in 12..16 -> PartOfDay.Afternoon
        in 17..20 -> PartOfDay.Evening
        else -> PartOfDay.Night
    }

internal fun ImportedActivity.toPreview(fileName: String, zone: TimeZone, zones: ImmutableList<TimeZoneOption>?): ImportPreview {
    val line = ActivityDraft.polylineOf(samples)
    return ImportPreview(
        fileName = fileName,
        elapsed = elapsed,
        device = device,
        polyline = line?.polyline,
        polylineGaps = line?.gaps?.toImmutableList(),
        dive = dive?.toPreview(),
        elevationLossMeters = elevationLossMeters,
        // A run's cadence is strides, not revolutions.
        cadence =
            if (type.sport in PEDALLED && (avgCadence != null || maxCadence != null)) CadencePreview(avgCadence, maxCadence) else null,
        power = power?.toPreview(),
        shifting = shifting?.toPreview(),
        mtb = mtb?.let { MtbPreview(it.jumps, it.longestHangTimeSeconds, it.longestJumpMeters, it.grit, it.flow) },
        sensors = sensors.map { SensorPreview(it.kind, it.manufacturer, it.model, it.battery, it.batteryPercent) }.toImmutableList(),
        hasStreams = streams != null,
        clock = localStart?.let { importClock(it, zone, zones) },
    )
}

/** Every zone, with the offset in force when the activity started: a zone with daylight saving shows the one that applied. */
internal fun zoneOptions(localStart: LocalDateTime): ImmutableList<TimeZoneOption> {
    val device = TimeZones.device().id
    // The device's zone can be one the list leaves out, like "GMT".
    val ids = if (device in TimeZones.ids) TimeZones.ids else (TimeZones.ids + device).sorted()
    return ids.map { zoneOption(TimeZone.of(it), localStart) }.toImmutableList()
}

internal fun zoneOption(zone: TimeZone, localStart: LocalDateTime): TimeZoneOption {
    val offset = zone.offsetAt(localStart.toInstant(zone))
    return TimeZoneOption(zone.id, if (offset.totalSeconds == 0) "UTC" else "UTC$offset")
}

/** The import clock's zoneless start, as written ("2024-10-16 17:30"). */
internal val LOCAL_START = LocalDateTime.Format {
    date(LocalDate.Formats.ISO)
    char(' ')
    hour()
    char(':')
    minute()
}

private val PEDALLED = setOf(Sport.RIDE, Sport.HANDCYCLE)

private const val JOULES_PER_KILOJOULE = 1000

private fun ImportedPower.toPreview() =
    PowerPreview(
        avgWatts = avgWatts,
        maxWatts = maxWatts,
        normalizedWatts = normalizedWatts,
        workKilojoules = workJoules?.let { (it / JOULES_PER_KILOJOULE).roundToLong() },
        ftpWatts = ftpWatts,
        intensityFactor = intensityFactor,
        trainingStressScore = trainingStressScore,
    )

private fun ImportedShifting.toPreview() =
    ShiftingPreview(
        frontShifts = frontShifts,
        rearShifts = rearShifts,
        gears = timeInGears.map { GearTimePreview(it.gear, it.seconds) }.toImmutableList(),
    )

private fun ImportedDive.toPreview() =
    with(header) {
        DivePreview(
            maxDepthMeters = maxDepthMeters,
            avgDepthMeters = avgDepthMeters,
            minWaterTempCelsius = minWaterTempCelsius,
            diveNumber = diveNumber,
            surfaceIntervalMinutes = surfaceIntervalMinutes,
            gradientFactorLow = gradientFactorLow,
            gradientFactorHigh = gradientFactorHigh,
            startCns = startCns,
            endCns = endCns,
            gases = gases.map { DiveGasPreview(it.oxygenPercent, it.heliumPercent) }.toImmutableList(),
            closedCircuit = closedCircuit,
            profile = StreamAnalytics.everyNth(profile).map { DepthPreviewPoint(it.seconds, it.depthMeters) }.toImmutableList(),
            bottomTimeSeconds = bottomTimeSeconds?.roundToLong(),
            oxygenToxicityOtu = oxygenToxicityOtu,
            rmvLitersPerMinute = rmvLitersPerMinute,
            sacBarPerMinute = sacBarPerMinute,
            tanks = tanks.map { DiveTankPreview(it.startPressureBar, it.endPressureBar) }.toImmutableList(),
            site = (entry ?: exit)?.let { DiveSitePreview(it.latitude, it.longitude) },
        )
    }
