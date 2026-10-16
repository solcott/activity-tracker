package io.github.solcott.activitytracker.app.record

import io.github.solcott.activitytracker.activityfile.ImportedActivity
import io.github.solcott.activitytracker.data.activity.ActivityDraft
import io.github.solcott.activitytracker.data.activity.ActivityRepository
import io.github.solcott.activitytracker.data.activity.DiveMatch
import io.github.solcott.activitytracker.data.activity.DuplicateOf
import io.github.solcott.activitytracker.data.activity.OwnActivity
import io.github.solcott.activitytracker.data.activity.VisibilityChoice
import io.github.solcott.activitytracker.lexicon.AtUri
import io.github.solcott.activitytracker.lexicon.app.TrainingDetails
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.format
import kotlinx.datetime.toLocalDateTime

/**
 * Which of a file's dives import. A dive that isn't a duplicate imports unless the athlete cleared it ([deselected]); a duplicate only when
 * the athlete picked something other than [DuplicateAction.Ignore] for it ([actions]).
 */
internal data class DiveSelection(val deselected: Set<Int> = emptySet(), val actions: Map<Int, DuplicateAction> = emptyMap()) {
    fun action(index: Int): DuplicateAction = actions[index] ?: DuplicateAction.Ignore

    fun isSelected(index: Int, match: DiveMatch?): Boolean =
        if (match == null) index !in deselected else action(index) != DuplicateAction.Ignore

    /** Ticking a duplicate imports it anyway; unticking ignores it. */
    fun toggled(index: Int, match: DiveMatch?): DiveSelection =
        when {
            match != null -> withAction(index, if (isSelected(index, match)) DuplicateAction.Ignore else DuplicateAction.ImportAnyway)
            index in deselected -> copy(deselected = deselected - index)
            else -> copy(deselected = deselected + index)
        }

    /** Every dive that isn't a duplicate, of [count]; duplicates keep their actions. */
    fun all(selected: Boolean, count: Int): DiveSelection = copy(deselected = if (selected) emptySet() else (0 until count).toSet())

    fun withAction(index: Int, action: DuplicateAction): DiveSelection = copy(actions = actions + (index to action))
}

/** The file's only activity ([placed] in [zone]), and whether it's a dive already in the athlete's log. */
@Suppress("LongParameterList") // The preview is everything the screen knows about the file.
internal fun singlePreview(
    placed: Pair<String, ImportedActivity>?,
    zone: TimeZone,
    zones: ImmutableList<TimeZoneOption>?,
    matches: List<DiveMatch?>,
    selection: DiveSelection,
    streams: LoggedStreams,
): ImportPreview? {
    val (name, activity) = placed ?: return null
    val duplicate = duplicateInfo(activity, matches.firstOrNull(), selection.action(0), zone, streams)
    return activity.toPreview(name, zone, zones).copy(duplicate = duplicate)
}

/** A logbook's [dives], placed in [zone], as the athlete picks from them; null until a logbook ([fileName]) is read. */
@Suppress("LongParameterList") // The batch is everything the screen knows about the file.
internal fun diveBatch(
    fileName: String?,
    dives: List<ImportedActivity>?,
    selection: DiveSelection,
    matches: List<DiveMatch?>,
    zone: TimeZone,
    zones: ImmutableList<TimeZoneOption>?,
    streams: LoggedStreams,
): DiveBatch? {
    if (fileName == null || dives == null) return null
    return DiveBatch(
        fileName = fileName,
        dives =
            dives
                .mapIndexed { index, dive ->
                    val start = dive.startedAt.toLocalDateTime(zone)
                    val match = matches.getOrNull(index)
                    DiveRow(
                        index = index,
                        title = dive.title,
                        defaultTitle = DefaultTitle(partOfDay(start.hour), dive.type),
                        start = start.format(LOCAL_START),
                        elapsed = dive.elapsed,
                        maxDepthMeters = dive.dive?.maxDepthMeters,
                        diveNumber = dive.dive?.diveNumber,
                        device = dive.device,
                        selected = selection.isSelected(index, match),
                        duplicate = duplicateInfo(dive, match, selection.action(index), zone, streams),
                    )
                }
                .toImmutableList(),
        clock = dives.firstNotNullOfOrNull { it.localStart }?.let { importClock(it, zone, zones) },
    )
}

internal fun importClock(start: LocalDateTime, zone: TimeZone, zones: ImmutableList<TimeZoneOption>?) =
    ImportClock(start.format(LOCAL_START), zones?.find { it.id == zone.id } ?: zoneOption(zone, start), zones ?: persistentListOf())

/** A logbook's selected dives: new activities (by their index in the file), merges into logged dives, and other recordings of either. */
internal class DiveImport(
    val drafts: List<Pair<Int, ActivityDraft>>,
    val merges: List<Pair<OwnActivity, ActivityDraft>>,
    val recordings: List<RecordingDraft>,
    /** For each dive in the file that duplicates a logged one, the activity a recording of it should point at instead. */
    val logged: Map<Int, AtUri>,
)

/**
 * A dive to publish as another recording of a logged activity ([ofLogged], whose visibility is [loggedVisibility]) or of a dive earlier in
 * the file ([ofIndex]), which shares this one's.
 */
internal class RecordingDraft(val draft: ActivityDraft, val ofLogged: AtUri?, val ofIndex: Int?, val loggedVisibility: String? = null)

/**
 * The selected [dives] ready to save, oldest first, each with its own type and its site as the title, else its [defaultTitles] entry. Dives
 * carry no thresholds to update.
 */
@Suppress("LongParameterList") // Each is a part of the draft.
internal fun diveImport(
    dives: List<ImportedActivity>?,
    selection: DiveSelection,
    matches: List<DiveMatch?>,
    defaultTitles: Map<Int, String>,
    training: TrainingDetails?,
    visibility: VisibilityChoice,
): DiveImport {
    val drafts = mutableListOf<Pair<Int, ActivityDraft>>()
    val merges = mutableListOf<Pair<OwnActivity, ActivityDraft>>()
    val recordings = mutableListOf<RecordingDraft>()
    for ((index, dive) in dives.orEmpty().withIndex().sortedBy { it.value.startedAt }) {
        val match = matches.getOrNull(index)
        if (!selection.isSelected(index, match)) continue
        val title = dive.title ?: defaultTitles[index]?.ifBlank { null }
        // A logbook's notes are personal and the batch never shows them, so none are published unseen.
        val draft = importDraft(dive, dive.type, title, training, emptyList()).copy(description = null).withVisibility(visibility)
        val action = selection.action(index)
        val into = mergeTarget(match, action)
        when {
            into != null -> merges += into to draft
            match != null && action == DuplicateAction.Group ->
                recordings +=
                    RecordingDraft(draft, groupTarget(match, action), recordedIndex(index, matches, selection), loggedVisibility(match))
            else -> drafts += index to draft
        }
    }
    val logged = matches.withIndex().mapNotNull { (index, match) -> loggedTarget(match)?.let { index to it } }.toMap()
    return DiveImport(drafts, merges, recordings, logged)
}

/** The earliest dive in the file a chain of in-file recordings leads back to from [index], or null when it records a logged dive. */
private fun recordedIndex(index: Int, matches: List<DiveMatch?>, selection: DiveSelection): Int? {
    fun inFile(i: Int) = (matches.getOrNull(i)?.of as? DuplicateOf.InFile)?.index
    var current = inFile(index)
    while (current != null && selection.action(current) == DuplicateAction.Group) current = inFile(current) ?: break
    return current
}

/** The file's only activity, by its name; null for a logbook of several. */
internal fun singleActivity(file: Pair<String, List<ImportedActivity>>?): Pair<String, ImportedActivity>? = file?.let { (name, read) ->
    read.singleOrNull()?.let { name to it }
}

/**
 * Saves a logbook's selected dives (merges first, then one drain with the new ones), then [onPublished]; nothing when none are selected.
 */
internal suspend fun ActivityRepository.saveDives(import: DiveImport, onPublished: () -> Unit) {
    if (import.drafts.isEmpty() && import.merges.isEmpty() && import.recordings.isEmpty()) return
    import.merges.forEach { (into, draft) -> mergeInto(into, draft) }
    // New activities first: a recording of one points at its URI.
    val published = import.drafts.map { it.first }.zip(publishAll(import.drafts.map { it.second })).toMap()
    val recordings =
        import.recordings.map { recording ->
            val of = recording.ofLogged ?: recording.ofIndex?.let { published[it] ?: import.logged[it] }
            // A recording takes its primary's visibility: a logged dive's own, or the batch's for one in the file.
            val visibility = recording.loggedVisibility.takeIf { recording.ofLogged != null } ?: recording.draft.visibility
            of?.let { recording.draft.recordingOf(it, visibility) } ?: recording.draft
        }
    if (recordings.isNotEmpty()) publishAll(recordings)
    onPublished()
}

/** Publishes a single file's activity, maybe as another recording of a logged one, or merges it into the logged dive it duplicates. */
internal suspend fun ActivityRepository.saveSingle(
    draft: ActivityDraft,
    into: OwnActivity?,
    recordingOf: AtUri?,
    recordingOfVisibility: String?,
) {
    if (into == null) {
        publish(recordingOf?.let { draft.recordingOf(it, recordingOfVisibility ?: draft.visibility) } ?: draft)
    } else {
        mergeInto(into, draft)
        sync()
    }
}
