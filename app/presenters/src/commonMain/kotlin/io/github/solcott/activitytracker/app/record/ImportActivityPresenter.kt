package io.github.solcott.activitytracker.app.record

import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.slack.circuit.codegen.annotations.CircuitInject
import com.slack.circuit.runtime.Navigator
import io.github.solcott.activitytracker.activityfile.ActivityFileReader
import io.github.solcott.activitytracker.activityfile.ImportedActivity
import io.github.solcott.activitytracker.activityfile.ImportedDive
import io.github.solcott.activitytracker.activityfile.ImportedPower
import io.github.solcott.activitytracker.activityfile.ImportedShifting
import io.github.solcott.activitytracker.activityfile.SourceFile
import io.github.solcott.activitytracker.activityfile.TimeZones
import io.github.solcott.activitytracker.activityfile.UnsupportedActivityFileException
import io.github.solcott.activitytracker.activityfile.localZone
import io.github.solcott.activitytracker.app.UiError
import io.github.solcott.activitytracker.app.edit.rememberDetailsForm
import io.github.solcott.activitytracker.app.rememberSingleFlight
import io.github.solcott.activitytracker.app.visibility.rememberNewActivityVisibility
import io.github.solcott.activitytracker.app.visibility.uiErrorOf
import io.github.solcott.activitytracker.atproto.runSuspendCatching
import io.github.solcott.activitytracker.data.activity.ActivityDefaults
import io.github.solcott.activitytracker.data.activity.ActivityDraft
import io.github.solcott.activitytracker.data.activity.ActivityRepository
import io.github.solcott.activitytracker.data.di.SessionScope
import io.github.solcott.activitytracker.data.feed.FeedRepository
import io.github.solcott.activitytracker.data.profile.OwnProfile
import io.github.solcott.activitytracker.data.profile.ProfileRepository
import io.github.solcott.activitytracker.data.profile.applyDeviceThresholds
import io.github.solcott.activitytracker.data.streams.StreamAnalytics
import io.github.solcott.activitytracker.data.training.ThresholdChange
import io.github.solcott.activitytracker.data.training.ThresholdUpdates
import io.github.solcott.activitytracker.lexicon.app.ActivityType
import io.github.solcott.activitytracker.lexicon.app.FollowPolicy
import io.github.solcott.activitytracker.lexicon.app.Sport
import io.github.solcott.activitytracker.lexicon.app.TrainingDetails
import kotlin.math.roundToLong
import kotlin.time.Instant
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.format.char
import kotlinx.datetime.offsetAt
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime

@CircuitInject(ImportActivityScreen::class, SessionScope::class)
@Composable
fun ImportActivityPresenter(
    navigator: Navigator,
    activities: ActivityRepository,
    reader: ActivityFileReader,
    typeChoices: ActivityTypeChoices,
    profiles: ProfileRepository,
    feed: FeedRepository,
    defaults: ActivityDefaults,
): ImportActivityState {
    // The parsed file isn't saveable; after process death the athlete picks it again. A logbook holds many dives.
    var file by remember { mutableStateOf<Pair<String, ImmutableList<ImportedActivity>>?>(null) }
    // The file as it's kept with every activity saved from it; null when it's too large to keep (originalTooLarge).
    var source by remember { mutableStateOf<SourceFile?>(null) }
    var originalTooLarge by remember { mutableStateOf(false) }
    // Which of the file's dives import, and what to do with duplicates.
    var selection by remember { mutableStateOf(DiveSelection()) }
    val typeChoice = typeChoices.produce(ActivityType(Sport.OTHER))
    val type = typeChoice.selected
    val title = rememberTextFieldState()
    val details = rememberDetailsForm()
    // The title the last file filled in, so a file without one clears it but not what the athlete typed.
    var fileTitle by remember { mutableStateOf<String?>(null) }
    // Where a zone-less file's clock was; null for this device's zone.
    var zoneId by rememberSaveable { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<UiError?>(null) }
    val flights = rememberSingleFlight()
    val saving = flights.isRunning(ImportActivityEvent.Save::class)
    val reading = flights.isRunning(ImportActivityEvent.FilePicked::class)

    val zone = remember(zoneId) { zoneId?.let(TimeZone::of) ?: TimeZones.device() }
    val imported = singleActivity(file)
    val logbook = file?.takeIf { it.second.size > 1 }
    val placed = remember(imported, zone) { imported?.let { (name, activity) -> name to activity.inZone(zone) } }
    val placedDives = remember(logbook, zone) { logbook?.second?.map { it.inZone(zone) } }
    val zones = remember(file) { file?.second?.firstNotNullOfOrNull { it.localStart }?.let(::zoneOptions) }
    val own = rememberOwnDives(feed, file?.second)
    // Matched where the athlete placed the clock: a zone change can make or break a duplicate.
    val matches = remember(placed, placedDives, own) { importMatches(placed?.second, placedDives, own) }
    // The logged dives' charts, once the athlete picks Merge, so the merge note can say whether it adds any.
    val loggedStreams = rememberLoggedStreams(feed, remember(matches, selection) { mergeTargets(matches, selection) })
    // Keyed on the zone too: a zone with the same offset leaves the activity as it was.
    val preview =
        remember(placed, zones, zone, matches, selection, loggedStreams) {
            singlePreview(placed, zone, zones, matches, selection, loggedStreams)
        }
    val athlete = rememberOwnProfile(profiles)
    val visibility = rememberNewActivityVisibility(defaults) { athlete?.followPolicy == FollowPolicy.APPROVE }
    val training = athlete?.details?.training
    val thresholdChanges = remember(placed, training, type.sport) { pendingThresholds(training, placed?.second, type.sport) }
    val defaultTitle =
        placed
            ?.second
            ?.takeIf { it.title == null }
            ?.let { DefaultTitle(partOfDay(it.startedAt.toLocalDateTime(it.localZone(zone)).hour), type) }
    val dives =
        remember(logbook, placedDives, selection, matches, zones, zone, loggedStreams) {
            diveBatch(logbook?.first, placedDives, selection, matches, zone, zones, loggedStreams)
        }

    return ImportActivityState(
        reading,
        preview,
        typeChoice.field,
        title,
        defaultTitle,
        saving,
        error,
        thresholdChanges,
        visibility.fields,
        dives,
        own.check,
        placed?.let { details.fields(isDive = type.sport == Sport.DIVE) },
        approvesFollowers = athlete?.followPolicy == FollowPolicy.APPROVE,
        originalTooLarge = originalTooLarge,
    ) { event ->
        when (event) {
            is ImportActivityEvent.FilePicked -> {
                // Drop the previous file first, so a failed pick can't leave its preview (and Save) behind.
                file = null
                source = null
                originalTooLarge = false
                selection = DiveSelection()
                zoneId = null
                error = null
                // A newer pick replaces one still being read.
                flights.launchLatest(ImportActivityEvent.FilePicked::class) {
                    // The format comes from the file's content, not its name.
                    runSuspendCatching { reader.readKeeping(event.name, event.bytes.toByteArray()) }
                        .onSuccess { (readFile, prepared) ->
                            val read = readFile.activities
                            file = event.name to read.toImmutableList()
                            source = prepared
                            originalTooLarge = prepared == null
                            // A logbook's dives keep their own types and titles; these fields are for a single activity.
                            val first = read.first()
                            typeChoice.select(first.type)
                            val named = first.title.takeIf { read.size == 1 }
                            when {
                                named != null -> title.setTextAndPlaceCursorAtEnd(named)
                                fileTitle != null && title.text.toString() == fileTitle -> title.clearText()
                            }
                            fileTitle = named
                            // The file's notes (a logbook's, GPX `desc`, TCX `Notes`) start the description, for the athlete to keep,
                            // change or clear.
                            details.seed(
                                description = first.description.takeIf { read.size == 1 },
                                diveSite = first.dive?.siteName.takeIf { read.size == 1 },
                            )
                        }
                        .onFailure { e ->
                            error =
                                if (e is UnsupportedActivityFileException) UiError(UiError.Kind.UnsupportedActivityFile, null)
                                else UiError.of(UiError.Kind.ReadActivityFile, e)
                        }
                }
            }

            is ImportActivityEvent.TimeZoneChanged -> {
                zoneId = event.id
            }

            ImportActivityEvent.Back -> {
                navigator.pop()
            }

            is ImportActivityEvent.DiveToggled -> selection = selection.toggled(event.index, matches.getOrNull(event.index))

            is ImportActivityEvent.AllDivesSelected -> selection = selection.all(event.selected, placedDives.orEmpty().size)

            is ImportActivityEvent.DuplicateActionChanged -> selection = selection.withAction(event.index, event.action)

            is ImportActivityEvent.SaveDives ->
                flights.launch(ImportActivityEvent.Save::class) {
                    val import = diveImport(placedDives, selection, matches, event.defaultTitles, training, visibility.current, source)
                    runSuspendCatching { activities.saveDives(import, onPublished = { navigator.pop() }) }
                        .onFailure { e -> error = uiErrorOf(UiError.Kind.SaveActivity, e) }
                }

            is ImportActivityEvent.Save -> {
                // An ignored duplicate stays out.
                val activity = placed?.second?.takeIf { selection.isSelected(0, matches.firstOrNull()) } ?: return@ImportActivityState
                flights.launch(ImportActivityEvent.Save::class) {
                    runSuspendCatching {
                        val text = title.text.toString().ifBlank { event.defaultTitle?.ifBlank { null } }
                        activities.saveSingle(
                            importDraft(activity, type, text, training, thresholdChanges, source?.single())
                                .withDetails(
                                    description = details.description.text.toString(),
                                    diveSite = details.diveSite.text.toString().takeIf { type.sport == Sport.DIVE },
                                    photos = details.newPhotos(),
                                )
                                .withVisibility(visibility.current),
                            into = mergeTarget(matches.firstOrNull(), selection.action(0)),
                            recordingOf = groupTarget(matches.firstOrNull(), selection.action(0)),
                            recordingOfVisibility = loggedVisibility(matches.firstOrNull()),
                        )
                        updateThresholds(profiles, activity, type.sport, previewed = thresholdChanges)
                        navigator.pop()
                    }
                        .onFailure { e -> error = uiErrorOf(UiError.Kind.SaveActivity, e) }
                }
            }
        }
    }
}

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

internal fun ImportedActivity.toPreview(fileName: String, zone: TimeZone, zones: ImmutableList<TimeZoneOption>?) =
    ImportPreview(
        fileName = fileName,
        elapsed = elapsed,
        movingSeconds = movingSeconds,
        distanceMeters = distanceMeters,
        elevationGainMeters = elevationGainMeters,
        avgHeartRate = avgHeartRate,
        maxHeartRate = maxHeartRate,
        device = device,
        polyline = ActivityDraft.polylineOf(samples),
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

/** Every zone, with the offset in force when the activity started: a zone with daylight saving shows the one that applied. */
private fun zoneOptions(localStart: LocalDateTime): ImmutableList<TimeZoneOption> {
    val device = TimeZones.device().id
    // The device's zone can be one the list leaves out, like "GMT".
    val ids = if (device in TimeZones.ids) TimeZones.ids else (TimeZones.ids + device).sorted()
    return ids.map { zoneOption(TimeZone.of(it), localStart) }.toImmutableList()
}

internal fun zoneOption(zone: TimeZone, localStart: LocalDateTime): TimeZoneOption {
    val offset = zone.offsetAt(localStart.toInstant(zone))
    return TimeZoneOption(zone.id, if (offset.totalSeconds == 0) "UTC" else "UTC$offset")
}

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
