package io.github.solcott.activitytracker.app.record

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import io.github.solcott.activitytracker.activityfile.ImportedActivity
import io.github.solcott.activitytracker.data.activity.ActivityMatch
import io.github.solcott.activitytracker.data.activity.ActivityMatcher
import io.github.solcott.activitytracker.data.activity.DuplicateOf
import io.github.solcott.activitytracker.data.activity.MatchSpan
import io.github.solcott.activitytracker.data.activity.OwnActivity
import io.github.solcott.activitytracker.data.activity.matchSpan
import io.github.solcott.activitytracker.data.feed.FeedRepository
import io.github.solcott.activitytracker.data.feed.ownActivitiesAround
import io.github.solcott.activitytracker.lexicon.app.ActivityType
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.datetime.TimeZone
import kotlinx.datetime.offsetAt

/**
 * The athlete's own activities around a file's, to find duplicates among; null while they load. [retry] searches again after a search that
 * couldn't finish.
 */
internal class OwnActivities(val activities: List<OwnActivity>?, val check: DuplicateCheck, val retry: () -> Unit = {})

/**
 * Loads the athlete's activities of any sport from 14 hours either side of each of [spans] (wide enough for any zone the athlete might
 * place a zone-less clock in), however many years they cover ([ownActivitiesAround]). A search that can't finish keeps what it found and
 * says so ([DuplicateCheck.Failed]). Nothing loads unless [search]: adding a recording to an activity has no duplicates to find.
 */
@Composable
internal fun rememberOwnActivities(feed: FeedRepository, spans: ImmutableList<MatchSpan>?, search: Boolean = true): OwnActivities {
    var attempt by remember(spans, search) { mutableIntStateOf(0) }
    var own by
        remember(spans, search) {
            mutableStateOf(if (search) OwnActivities(null, DuplicateCheck.Checking) else OwnActivities(emptyList(), DuplicateCheck.Done))
        }
    LaunchedEffect(spans, search, attempt) {
        own =
            if (spans.isNullOrEmpty() || !search) {
                OwnActivities(emptyList(), DuplicateCheck.Done)
            } else {
                // A retry keeps what the last search found on screen until this one answers.
                if (attempt > 0) own = OwnActivities(own.activities, DuplicateCheck.Checking)
                val found = feed.ownActivitiesAround(spans)
                OwnActivities(found.activities, if (found.complete) DuplicateCheck.Done else DuplicateCheck.Failed)
            }
    }
    return OwnActivities(own.activities, own.check) { attempt++ }
}

/**
 * What each of an import's activities ([spans], placed in their zones, oldest first) duplicates, among [own] activities and the earlier
 * ones in the import. A logged activity that's a member of a combined one stands for that combined activity, when it's among [own]: Combine
 * adds to it.
 */
internal fun importMatches(spans: List<MatchSpan>, own: OwnActivities): List<ActivityMatch?> {
    val logged = own.activities.orEmpty()
    val byUri = logged.associateBy { it.uri }
    return ActivityMatcher.matchSpans(spans, logged).map { match ->
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
        existing =
            existing?.let {
                ExistingActivityPreview(
                    it.title,
                    importStart(it.startedAt, zone),
                    it.device,
                    ActivityType(it.sport, it.subSport),
                    it.elapsedTime.seconds,
                    it.distance?.toDouble(),
                )
            },
        inBatchIndex = (of as? DuplicateOf.InBatch)?.index,
        hoursApart = match.hoursApart,
        action = action,
        actions = ACTIONS,
        stillUploading = !canCombine(match),
        exactCopy = match.exactCopy,
    )
}

private val ACTIONS = persistentListOf(DuplicateAction.Ignore, DuplicateAction.ImportAnyway, DuplicateAction.Combine)

/**
 * Whether [match]'s activity can be combined with what it repeats: an earlier one in the batch always can, a logged one once it's sent,
 * since combining reads it as stored.
 */
internal fun canCombine(match: ActivityMatch?): Boolean =
    when (val of = match?.of) {
        is DuplicateOf.Existing -> of.activity.cid != null
        is DuplicateOf.InBatch -> true
        null -> false
    }

/** The logged activity to combine [match]'s with, when the athlete chose to. */
internal fun combineTarget(match: ActivityMatch?, action: DuplicateAction): OwnActivity? =
    (match?.of as? DuplicateOf.Existing)?.activity?.takeIf { action == DuplicateAction.Combine && it.cid != null }

/** This earlier activity of the batch as a duplicate of it is compared with. */
internal fun ImportRow.compared(): ExistingActivityPreview = ExistingActivityPreview(title, start, device, type, elapsed, distanceMeters)

/** Each activity [placed] in its zone, as the matcher compares it. */
internal fun List<ImportedActivity>.spans(): List<MatchSpan> = map { it.matchSpan() }

/** The batch's activities [placed] in their zones, as the matcher compares them: each as the sport it saves as unless changed. */
internal fun ScannedBatch.spans(placed: List<ImportedActivity>): List<MatchSpan> = placed.mapIndexed { index, activity ->
    activity.matchSpan(items[index].type(null).sport)
}

/** [at] as an import's start, on [zone]'s clock at that moment. */
internal fun importStart(at: Instant, zone: TimeZone): ImportStart =
    ImportStart(at.epochSeconds, zone.offsetAt(at).totalSeconds / SECONDS_PER_MINUTE)

private const val SECONDS_PER_MINUTE = 60
