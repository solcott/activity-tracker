package io.github.solcott.activitytracker.app.record

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import io.github.solcott.activitytracker.activityfile.ImportedActivity
import io.github.solcott.activitytracker.atproto.runSuspendCatching
import io.github.solcott.activitytracker.data.activity.ActivityMerge
import io.github.solcott.activitytracker.data.activity.DiveMatch
import io.github.solcott.activitytracker.data.activity.DiveMatcher
import io.github.solcott.activitytracker.data.activity.DuplicateOf
import io.github.solcott.activitytracker.data.activity.OwnActivity
import io.github.solcott.activitytracker.data.feed.FeedRepository
import io.github.solcott.activitytracker.lexicon.AtUri
import io.github.solcott.activitytracker.lexicon.app.ActivityStreamData
import io.github.solcott.activitytracker.lexicon.app.Sport
import kotlin.time.Duration.Companion.days
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableSet
import kotlinx.datetime.TimeZone
import kotlinx.datetime.format
import kotlinx.datetime.toLocalDateTime

/** The athlete's own dives around a file's, to find duplicates among; null while they load. */
internal class OwnDives(val activities: List<OwnActivity>?, val check: DuplicateCheck)

/**
 * Loads the athlete's dives from a day either side of [file]'s: wide enough for any zone the athlete might place a zone-less clock in. A
 * file without dives needs none.
 */
@Composable
internal fun rememberOwnDives(feed: FeedRepository, file: ImmutableList<ImportedActivity>?): OwnDives {
    var own by remember(file) { mutableStateOf(OwnDives(null, DuplicateCheck.Checking)) }
    LaunchedEffect(file) {
        val dives = file.orEmpty().filter { it.type.sport == Sport.DIVE }
        if (dives.isEmpty()) {
            own = OwnDives(emptyList(), DuplicateCheck.Done)
            return@LaunchedEffect
        }
        val since = dives.minOf { it.startedAt } - 1.days
        val until = dives.maxOf { it.startedAt + it.elapsed } + 1.days
        own =
            runSuspendCatching { feed.ownActivitiesStartedBetween(since, until, Sport.DIVE) }
                .fold({ OwnDives(it, DuplicateCheck.Done) }, { OwnDives(emptyList(), DuplicateCheck.Failed) })
    }
    return own
}

/** Logged dives' streams by URI, as far as they've loaded: null for a dive without any. A dive not in the map isn't known yet. */
internal typealias LoggedStreams = Map<AtUri, ActivityStreamData?>

/** Loads the streams of each logged dive in [targets] once, so a merge into it can say whether it adds charts. */
@Composable
internal fun rememberLoggedStreams(feed: FeedRepository, targets: ImmutableSet<AtUri>): LoggedStreams {
    var loaded by remember { mutableStateOf<LoggedStreams>(emptyMap()) }
    LaunchedEffect(targets) {
        // A failure leaves the dive unknown, and the note hedged.
        for (uri in targets - loaded.keys) runSuspendCatching { feed.streams(uri)?.data }.onSuccess { loaded = loaded + (uri to it) }
    }
    return loaded
}

/** The indexed logged dives the athlete chose to merge into: only those have streams to read (see `ActivityRepository.mergeInto`). */
internal fun mergeTargets(matches: List<DiveMatch?>, selection: DiveSelection): ImmutableSet<AtUri> =
    matches.indices
        .mapNotNull { index -> mergeTarget(matches[index], selection.action(index))?.takeIf { it.cid != null }?.uri }
        .toImmutableSet()

/** What each of the file's dives ([dives] for a logbook, else [single]) duplicates, among [own] dives and the file's own. */
internal fun importMatches(single: ImportedActivity?, dives: List<ImportedActivity>?, own: OwnDives): List<DiveMatch?> =
    DiveMatcher.match(dives ?: listOfNotNull(single), own.activities.orEmpty())

/** Why [match] makes [dive] a duplicate, for the screen to warn about. */
internal fun duplicateInfo(
    dive: ImportedActivity,
    match: DiveMatch?,
    action: DuplicateAction,
    zone: TimeZone,
    streams: LoggedStreams,
): DuplicateInfo? {
    val of = match?.of ?: return null
    val logged = (of as? DuplicateOf.Existing)?.activity
    val existing = logged?.record
    return DuplicateInfo(
        existing = existing?.let { ExistingActivityPreview(it.title, it.startedAt.toLocalDateTime(zone).format(LOCAL_START), it.device) },
        inFileIndex = (of as? DuplicateOf.InFile)?.index,
        hoursApart = match.hoursApart,
        action = action,
        actions = if (existing != null) LOGGED_ACTIONS else IN_FILE_ACTIONS,
        mergeGains = logged?.let { mergeGains(it, dive, streams) } ?: persistentListOf(),
    )
}

/** What merging [dive] into [logged] would fill in. */
private fun mergeGains(logged: OwnActivity, dive: ImportedActivity, streams: LoggedStreams): ImmutableList<MergeGain> {
    val existing = logged.record
    return listOfNotNull(
            MergeGain.Device.takeIf { existing.device == null && dive.device != null },
            MergeGain.SubSport.takeIf { existing.subSport == null && dive.type.subSport != null },
            MergeGain.WaterTemperature.takeIf { existing.avgTemperature == null && dive.avgTemperatureCelsius != null },
            MergeGain.DiveDetails.takeIf { existing.dive == null && dive.dive != null },
            MergeGain.HeartRate.takeIf { existing.avgHeartRate == null && dive.avgHeartRate != null },
            MergeGain.Track.takeIf { existing.track == null && dive.samples.isNotEmpty() },
            chartsGain(logged, dive, streams),
        )
        .toImmutableList()
}

/** Whether merging [dive] adds charts to [logged]: none to one still in the outbox, whose streams a merge leaves alone. */
private fun chartsGain(logged: OwnActivity, dive: ImportedActivity, streams: LoggedStreams): MergeGain? {
    val imported = dive.streams ?: return null
    return when {
        logged.cid == null -> null
        logged.uri !in streams -> MergeGain.AnyCharts
        else -> MergeGain.Charts.takeIf { ActivityMerge.addsChannels(streams[logged.uri], imported) }
    }
}

/** Merging is into a logged dive; a dive twice in the file can be imported once, twice, or as two recordings of it. */
private val LOGGED_ACTIONS =
    persistentListOf(DuplicateAction.Ignore, DuplicateAction.ImportAnyway, DuplicateAction.Merge, DuplicateAction.Group)
private val IN_FILE_ACTIONS = persistentListOf(DuplicateAction.Ignore, DuplicateAction.ImportAnyway, DuplicateAction.Group)

/** The logged activity a recording of [match]'s dive points at, when the athlete chose to group them: never itself a recording. */
internal fun groupTarget(match: DiveMatch?, action: DuplicateAction): AtUri? =
    loggedTarget(match)?.takeIf { action == DuplicateAction.Group }

/** The activity a recording of the logged dive [match] names should point at: that dive, or the one it's a recording of. */
internal fun loggedTarget(match: DiveMatch?): AtUri? =
    (match?.of as? DuplicateOf.Existing)?.activity?.let { it.record.recordingOf ?: it.uri }

/** The visibility of the logged dive [match] names, which a recording grouped into it takes. */
internal fun loggedVisibility(match: DiveMatch?): String? = (match?.of as? DuplicateOf.Existing)?.activity?.visibility

/** The logged dive to merge into, when the athlete chose to. */
internal fun mergeTarget(match: DiveMatch?, action: DuplicateAction): OwnActivity? =
    (match?.of as? DuplicateOf.Existing)?.activity?.takeIf { action == DuplicateAction.Merge }
