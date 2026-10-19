package io.github.solcott.activitytracker.app.record

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import io.github.solcott.activitytracker.activityfile.ImportedActivity
import io.github.solcott.activitytracker.atproto.runSuspendCatching
import io.github.solcott.activitytracker.data.activity.ActivityMatch
import io.github.solcott.activitytracker.data.activity.ActivityMatcher
import io.github.solcott.activitytracker.data.activity.DuplicateOf
import io.github.solcott.activitytracker.data.activity.OwnActivity
import io.github.solcott.activitytracker.data.feed.FeedRepository
import kotlin.time.Duration.Companion.hours
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.datetime.TimeZone
import kotlinx.datetime.format
import kotlinx.datetime.toLocalDateTime

/** The athlete's own activities around a file's, to find duplicates among; null while they load. */
internal class OwnActivities(val activities: List<OwnActivity>?, val check: DuplicateCheck)

/**
 * Loads the athlete's activities of any sport from 14 hours either side of [file]'s: wide enough for any zone the athlete might place a
 * zone-less clock in. Nothing loads unless [search]: adding a recording to an activity has no duplicates to find.
 */
@Composable
internal fun rememberOwnActivities(feed: FeedRepository, file: ImmutableList<ImportedActivity>?, search: Boolean = true): OwnActivities {
    var own by
        remember(file, search) {
            mutableStateOf(if (search) OwnActivities(null, DuplicateCheck.Checking) else OwnActivities(emptyList(), DuplicateCheck.Done))
        }
    LaunchedEffect(file, search) {
        val activities = file.orEmpty()
        if (activities.isEmpty() || !search) {
            own = OwnActivities(emptyList(), DuplicateCheck.Done)
            return@LaunchedEffect
        }
        val since = activities.minOf { it.startedAt } - SEARCH_MARGIN
        val until = activities.maxOf { it.startedAt + it.elapsed } + SEARCH_MARGIN
        own =
            runSuspendCatching { feed.ownActivitiesStartedBetween(since, until) }
                .fold({ OwnActivities(it, DuplicateCheck.Done) }, { OwnActivities(emptyList(), DuplicateCheck.Failed) })
    }
    return own
}

/** UTC-12 to UTC+14 apart, rounded: the most a zone-less clock can be off. */
private val SEARCH_MARGIN = 14.hours

/**
 * What each of the file's activities ([activities] for a logbook, else [single]) duplicates, among [own] activities and the file's own. A
 * logged activity that's a member of a combined one stands for that combined activity, when it's among [own]: Combine adds to it.
 */
internal fun importMatches(single: ImportedActivity?, activities: List<ImportedActivity>?, own: OwnActivities): List<ActivityMatch?> {
    val logged = own.activities.orEmpty()
    val byUri = logged.associateBy { it.uri }
    return ActivityMatcher.match(activities ?: listOfNotNull(single), logged).map { match ->
        val member = (match?.of as? DuplicateOf.Existing)?.activity
        val combined = member?.combinedInto?.let(byUri::get)
        if (combined != null) match.copy(of = DuplicateOf.Existing(combined)) else match
    }
}

/** Why [match] makes an activity a duplicate, for the screen to warn about; [action] is what the athlete chose for it. */
internal fun duplicateInfo(match: ActivityMatch?, action: DuplicateAction, zone: TimeZone): DuplicateInfo? {
    val of = match?.of ?: return null
    val existing = (of as? DuplicateOf.Existing)?.activity?.record
    return DuplicateInfo(
        existing = existing?.let { ExistingActivityPreview(it.title, it.startedAt.toLocalDateTime(zone).format(LOCAL_START), it.device) },
        inFileIndex = (of as? DuplicateOf.InFile)?.index,
        hoursApart = match.hoursApart,
        action = action,
        actions = ACTIONS,
        stillUploading = !canCombine(match),
    )
}

private val ACTIONS = persistentListOf(DuplicateAction.Ignore, DuplicateAction.ImportAnyway, DuplicateAction.Combine)

/**
 * Whether [match]'s activity can be combined with what it repeats: an earlier one in the file always can, a logged one once it's sent,
 * since combining reads it as stored.
 */
internal fun canCombine(match: ActivityMatch?): Boolean =
    when (val of = match?.of) {
        is DuplicateOf.Existing -> of.activity.cid != null
        is DuplicateOf.InFile -> true
        null -> false
    }

/** The logged activity to combine [match]'s with, when the athlete chose to. */
internal fun combineTarget(match: ActivityMatch?, action: DuplicateAction): OwnActivity? =
    (match?.of as? DuplicateOf.Existing)?.activity?.takeIf { action == DuplicateAction.Combine && it.cid != null }
