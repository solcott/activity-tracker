package io.github.solcott.activitytracker.data.activity

import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.github.solcott.activitytracker.atproto.runSuspendCatching
import io.github.solcott.activitytracker.data.db.AppDatabase
import io.github.solcott.activitytracker.data.db.CombineDismissalEntity
import io.github.solcott.activitytracker.data.db.dismissed
import io.github.solcott.activitytracker.data.di.SessionClients
import io.github.solcott.activitytracker.data.di.SessionScope
import io.github.solcott.activitytracker.data.feed.FeedRepository
import io.github.solcott.activitytracker.lexicon.AtUri
import io.github.solcott.activitytracker.lexicon.app.ActivityRecord
import io.github.solcott.activitytracker.lexicon.app.ActivityView
import io.github.solcott.activitytracker.lexicon.app.effectiveRecord
import io.github.solcott.activitytracker.location.TrackMath
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.seconds

/** One of the athlete's activities, as [CombineSuggestions] looks at it: saved moments ago, or as the AppView serves it. */
data class CombineSubject(val uri: AtUri, val record: ActivityRecord, val combinedInto: AtUri? = null) {
    constructor(view: ActivityView) : this(view.uri, view.effectiveRecord(), view.combinedInto)
}

/**
 * Another of the athlete's activities that [CombineSuggestions.forActivity]'s subject likely belongs with.
 *
 * @property candidate the activity found, which the [cue] compares with the subject.
 * @property shown what combining joins the subject to: [candidate], or the combined activity it's a member of.
 */
data class CombineSuggestion(val cue: CombineCue, val candidate: ActivityView, val shown: ActivityView)

/**
 * Finds another of the signed-in athlete's activities that one of theirs likely belongs with in one combined activity
 * (`feed/combine-suggestions`): recorded at the same time on another device, or one after the other ([ActivityMatcher.pairing]). One query
 * per call, and only for activities started in the last [RECENT]. Pairs the athlete said not to combine are remembered on the device.
 */
@SingleIn(SessionScope::class)
@Inject
class CombineSuggestions(
    private val db: AppDatabase,
    private val clients: SessionClients,
    private val feed: FeedRepository,
    private val tracks: ActivityTrackStore,
    private val clock: Clock,
) {
    /**
     * The activity nearest in time to [subject] that it likely belongs with, or null. Never one in the same combined activity, two combined
     * activities, a member with another combined activity or its members, a dismissed pair, or anything started more than [RECENT] ago.
     */
    suspend fun forActivity(subject: CombineSubject): CombineSuggestion? {
        val recentSince = clock.now() - RECENT
        if (subject.record.startedAt < recentSince) return null
        val views =
            feed.ownViewsStartedBetween(
                subject.record.startedAt - ActivityMatcher.MAX_BREAK - LONGEST,
                subject.record.end + ActivityMatcher.MAX_BREAK,
            )
        val nearest =
            views
                .filter { it.record.startedAt >= recentSince }
                .mapNotNull { candidate(subject, it) }
                .minByOrNull { apart(subject.record, it.record) }
        val shown = nearest?.let { shownFor(it.view, views) }
        return if (nearest == null || shown == null) null else CombineSuggestion(cueFor(subject, nearest), nearest.view, shown)
    }

    /** [view] as a candidate for [subject], or null when the two can't be suggested together or don't belong together. */
    private suspend fun candidate(subject: CombineSubject, view: ActivityView): Candidate? {
        val record = view.effectiveRecord()
        val subjectTarget = subject.combinedInto ?: subject.uri
        val target = view.combinedInto ?: view.uri
        val bothCombined =
            (subject.combinedInto != null || subject.record.combined != null) && (view.combinedInto != null || record.combined != null)
        val excluded =
            view.uri == subject.uri ||
                target == subjectTarget ||
                bothCombined ||
                dismissed(subject.uri, view.uri) ||
                dismissed(subjectTarget, target)
        return if (excluded) null else ActivityMatcher.pairing(subject.record, record)?.let { Candidate(view, record, it) }
    }

    /** What combining with [candidate] joins to: it, or the combined activity it's a member of (null when that can't be read). */
    private suspend fun shownFor(candidate: ActivityView, views: List<ActivityView>): ActivityView? =
        when (val into = candidate.combinedInto) {
            null -> candidate
            else -> views.firstOrNull { it.uri == into } ?: runSuspendCatching { feed.activity(into) }.getOrNull()
        }

    private suspend fun cueFor(subject: CombineSubject, nearest: Candidate): CombineCue =
        if (nearest.pairing == Pairing.Overlap) CombineCue.SameActivity
        else {
            val subjectEnds = ends(subject.uri, subject.record)
            CombineCues.of(nearest.pairing, subject.record, subjectEnds, nearest.record, ends(nearest.view.uri, nearest.record))
        }

    suspend fun forActivity(view: ActivityView): CombineSuggestion? = forActivity(CombineSubject(view))

    /** For [draft], just published as [uri]: the AppView may not serve it yet. */
    suspend fun forSaved(uri: AtUri, draft: ActivityDraft): CombineSuggestion? =
        forActivity(CombineSubject(uri, draft.toRecord(clock.now())))

    /**
     * What to pass `ActivityManager.combine` to accept [suggestion] for [subject]: the combined activity either one is part of, else the
     * earlier one, as `into`, and the other.
     */
    suspend fun targets(subject: ActivityView, suggestion: CombineSuggestion): Pair<ActivityView, ActivityView> {
        val mine = subject.combinedInto?.let { feed.activity(it) } ?: subject
        val theirs = suggestion.shown
        return when {
            mine.record.combined != null -> mine to theirs
            theirs.record.combined != null -> theirs to mine
            mine.record.startedAt <= theirs.record.startedAt -> mine to theirs
            else -> theirs to mine
        }
    }

    /** Stops suggesting [subject] and [suggestion]'s activity, in either order, from either one's page. */
    suspend fun dismiss(subject: CombineSubject, suggestion: CombineSuggestion) {
        val dao = db.combineDismissals()
        dao.put(CombineDismissalEntity.of(clients.did.value, subject.uri.value, suggestion.candidate.uri.value))
        dao.put(CombineDismissalEntity.of(clients.did.value, subject.combinedInto?.value ?: subject.uri.value, suggestion.shown.uri.value))
    }

    private suspend fun dismissed(a: AtUri, b: AtUri) = db.combineDismissals().dismissed(clients.did.value, a.value, b.value)

    /**
     * Where [uri] started and ended: from the athlete's own track when it has one, else the record's polyline (trimmed by privacy zones, so
     * maybe not where they really were) with its streams' first and last altitude.
     */
    private suspend fun ends(uri: AtUri, record: ActivityRecord): ActivityEnds =
        tracks.read(uri)?.takeIf { it.isNotEmpty() }?.let(::trackEnds) ?: routeEnds(uri, record)

    private suspend fun routeEnds(uri: AtUri, record: ActivityRecord): ActivityEnds {
        val line = record.polyline?.let { runCatching { TrackMath.decodePolyline(it) }.getOrNull() }.orEmpty()
        return if (line.isEmpty()) ActivityEnds()
        else polylineEnds(line, runSuspendCatching { feed.streams(uri)?.data?.altitude }.getOrNull().orEmpty())
    }

    private class Candidate(val view: ActivityView, val record: ActivityRecord, val pairing: Pairing)

    private companion object {
        /** Suggestions only look at activities started this recently, so an old log doesn't fill with banners. */
        val RECENT = 7.days

        /** The longest a candidate is taken to last: the query filters by start, so it reaches back this much further. */
        val LONGEST = 12.hours

        val ActivityRecord.end
            get() = startedAt + elapsedTime.seconds

        /** How far apart [a] and [b] are in time: zero when they overlap. */
        fun apart(a: ActivityRecord, b: ActivityRecord): Duration =
            maxOf(Duration.ZERO, maxOf(a.startedAt, b.startedAt) - minOf(a.end, b.end))
    }
}
