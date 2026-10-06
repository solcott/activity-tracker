package io.github.solcott.activitytracker.app.record

import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.slack.circuit.codegen.annotations.CircuitInject
import com.slack.circuit.runtime.Navigator
import io.github.solcott.activitytracker.activityfile.ActivityFileReader
import io.github.solcott.activitytracker.activityfile.DepthPoint
import io.github.solcott.activitytracker.activityfile.ImportedActivity
import io.github.solcott.activitytracker.activityfile.ImportedDive
import io.github.solcott.activitytracker.activityfile.ImportedPower
import io.github.solcott.activitytracker.activityfile.ImportedShifting
import io.github.solcott.activitytracker.activityfile.TimeZones
import io.github.solcott.activitytracker.activityfile.UnsupportedActivityFileException
import io.github.solcott.activitytracker.app.UiError
import io.github.solcott.activitytracker.app.rememberSingleFlight
import io.github.solcott.activitytracker.atproto.runSuspendCatching
import io.github.solcott.activitytracker.data.activity.ActivityDraft
import io.github.solcott.activitytracker.data.activity.ActivityRepository
import io.github.solcott.activitytracker.data.di.SessionScope
import io.github.solcott.activitytracker.data.sport.RecentActivityTypes
import io.github.solcott.activitytracker.lexicon.app.ActivityType
import io.github.solcott.activitytracker.lexicon.app.Sport
import kotlin.math.roundToLong
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.format
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
    recentTypes: RecentActivityTypes,
): ImportActivityState {
    // The parsed file isn't saveable; after process death the athlete picks it again.
    var imported by remember { mutableStateOf<Pair<String, ImportedActivity>?>(null) }
    var type by rememberSaveable(stateSaver = ActivityTypeSaver) { mutableStateOf(ActivityType(Sport.OTHER)) }
    val title = rememberTextFieldState()
    // The title the last file filled in, so a file without one clears it but not what the athlete typed.
    var fileTitle by remember { mutableStateOf<String?>(null) }
    // Where a zone-less file's clock was; null for this device's zone.
    var zoneId by rememberSaveable { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<UiError?>(null) }
    val flights = rememberSingleFlight()
    val saving = flights.isRunning(ImportActivityEvent.Save::class)
    val reading = flights.isRunning(ImportActivityEvent.FilePicked::class)

    val zone = remember(zoneId) { zoneId?.let(TimeZone::of) ?: TimeZones.device() }
    val placed = remember(imported, zone) { imported?.let { (name, activity) -> name to activity.inZone(zone) } }
    val zones = remember(imported) { imported?.second?.localStart?.let(::zoneOptions) }
    // Keyed on the zone too: a zone with the same offset leaves the activity as it was.
    val preview = remember(placed, zones, zone) { placed?.let { (name, activity) -> activity.toPreview(name, zone, zones) } }
    val quickTypes = quickActivityTypes(recentTypes)
    val defaultTitle =
        placed?.second?.takeIf { it.title == null }?.let { DefaultTitle(partOfDay(it.startedAt.toLocalDateTime(zone).hour), type) }

    return ImportActivityState(reading, preview, type, quickTypes, title, defaultTitle, saving, error) { event ->
        when (event) {
            is ImportActivityEvent.FilePicked -> {
                // Drop the previous file first, so a failed pick can't leave its preview (and Save) behind.
                imported = null
                zoneId = null
                error = null
                // A newer pick replaces one still being read.
                flights.launchLatest(ImportActivityEvent.FilePicked::class) {
                    // The format comes from the file's content, not its name.
                    runSuspendCatching { reader.read(event.bytes.toByteArray()) }
                        .onSuccess {
                            imported = event.name to it
                            type = it.type
                            val named = it.title
                            when {
                                named != null -> title.setTextAndPlaceCursorAtEnd(named)
                                fileTitle != null && title.text.toString() == fileTitle -> title.clearText()
                            }
                            fileTitle = named
                        }
                        .onFailure { e ->
                            error =
                                if (e is UnsupportedActivityFileException) UiError(UiError.Kind.UnsupportedActivityFile, null)
                                else UiError.of(UiError.Kind.ReadActivityFile, e)
                        }
                }
            }

            is ImportActivityEvent.TypeChanged -> {
                type = event.value
            }

            is ImportActivityEvent.TimeZoneChanged -> {
                zoneId = event.id
            }

            ImportActivityEvent.Back -> {
                navigator.pop()
            }

            is ImportActivityEvent.Save -> {
                val activity = placed?.second ?: return@ImportActivityState
                flights.launch(ImportActivityEvent.Save::class) {
                    runSuspendCatching {
                        val text = title.text.toString().ifBlank { event.defaultTitle?.ifBlank { null } }
                        activities.publish(ActivityDraft.fromImport(activity, type, text))
                        navigator.pop()
                    }
                        .onFailure { e -> error = UiError.of(UiError.Kind.SaveActivity, e) }
                }
            }
        }
    }
}

@Suppress("MagicNumber") // Hours of the day.
private fun partOfDay(hour: Int) =
    when (hour) {
        in 5..11 -> PartOfDay.Morning
        in 12..16 -> PartOfDay.Afternoon
        in 17..20 -> PartOfDay.Evening
        else -> PartOfDay.Night
    }

private fun ImportedActivity.toPreview(fileName: String, zone: TimeZone, zones: ImmutableList<TimeZoneOption>?) =
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
        clock =
            localStart?.let { start ->
                ImportClock(
                    start.format(LOCAL_START),
                    zones?.find { it.id == zone.id } ?: zoneOption(zone, start),
                    zones ?: persistentListOf(),
                )
            },
    )

/** Every zone, with the offset in force when the activity started: a zone with daylight saving shows the one that applied. */
private fun zoneOptions(localStart: LocalDateTime): ImmutableList<TimeZoneOption> {
    val device = TimeZones.device().id
    // The device's zone can be one the list leaves out, like "GMT".
    val ids = if (device in TimeZones.ids) TimeZones.ids else (TimeZones.ids + device).sorted()
    return ids.map { zoneOption(TimeZone.of(it), localStart) }.toImmutableList()
}

private fun zoneOption(zone: TimeZone, localStart: LocalDateTime): TimeZoneOption {
    val offset = zone.offsetAt(localStart.toInstant(zone))
    return TimeZoneOption(zone.id, if (offset.totalSeconds == 0) "UTC" else "UTC$offset")
}

private val LOCAL_START = LocalDateTime.Format {
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
        profile = profile.thinned().map { DepthPreviewPoint(it.seconds, it.depthMeters) }.toImmutableList(),
        bottomTimeSeconds = bottomTimeSeconds?.roundToLong(),
        oxygenToxicityOtu = oxygenToxicityOtu,
        rmvLitersPerMinute = rmvLitersPerMinute,
        sacBarPerMinute = sacBarPerMinute,
        tanks = tanks.map { DiveTankPreview(it.startPressureBar, it.endPressureBar) }.toImmutableList(),
        site = (entry ?: exit)?.let { DiveSitePreview(it.latitude, it.longitude) },
    )

/** About this many points draw a smooth profile; a two-hour dive logs 720. */
private const val MAX_PROFILE_POINTS = 300

/** Every nth point, keeping the last so the profile ends at the surface. */
private fun List<DepthPoint>.thinned(): List<DepthPoint> {
    if (size <= MAX_PROFILE_POINTS) return this
    val step = (size + MAX_PROFILE_POINTS - 1) / MAX_PROFILE_POINTS
    val kept = filterIndexed { i, _ -> i % step == 0 }
    return if (kept.last() == last()) kept else kept + last()
}
