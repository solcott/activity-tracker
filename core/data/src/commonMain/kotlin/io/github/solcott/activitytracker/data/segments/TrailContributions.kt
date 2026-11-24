package io.github.solcott.activitytracker.data.segments

import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.github.solcott.activitytracker.atproto.trailConfirmationRkey
import io.github.solcott.activitytracker.atproto.trailVoteRkey
import io.github.solcott.activitytracker.data.activity.OutboxProcessor
import io.github.solcott.activitytracker.data.activity.OutboxWriter
import io.github.solcott.activitytracker.data.activity.requireInteractionsFor
import io.github.solcott.activitytracker.data.activity.undoingOnFailure
import io.github.solcott.activitytracker.data.db.AppDatabase
import io.github.solcott.activitytracker.data.db.KeyValueEntity
import io.github.solcott.activitytracker.data.di.SessionClients
import io.github.solcott.activitytracker.data.di.SessionScope
import io.github.solcott.activitytracker.lexicon.AtUri
import io.github.solcott.activitytracker.lexicon.Cid
import io.github.solcott.activitytracker.lexicon.Nsid
import io.github.solcott.activitytracker.lexicon.Tid
import io.github.solcott.activitytracker.lexicon.app.AthleteSpace
import io.github.solcott.activitytracker.lexicon.app.ProfileViewBasic
import io.github.solcott.activitytracker.lexicon.app.SegmentView
import io.github.solcott.activitytracker.lexicon.app.TrailAttributes
import io.github.solcott.activitytracker.lexicon.app.TrailDetail
import io.github.solcott.activitytracker.lexicon.app.TrailDetailSuggestionRecord
import io.github.solcott.activitytracker.lexicon.app.TrailDetailSuggestionView
import io.github.solcott.activitytracker.lexicon.app.TrailDetailViewerVote
import io.github.solcott.activitytracker.lexicon.app.TrailDetailVoteDirection
import io.github.solcott.activitytracker.lexicon.app.TrailDetailVoteRecord
import io.github.solcott.activitytracker.lexicon.atproto.StrongRef
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * The athlete's part in keeping trail details true (segments/trail-detail-suggestions): suggesting a value for one detail (a public
 * `feed.trailDetailSuggestion` in their repo), voting on others' suggestions, and confirming a value in effect as still right or changed
 * (each a `feed.trailDetailVote` in their `interactions` space, one rkey per subject, rewritten to change it and deleted to take it back).
 * Writes go through the outbox and show at once through [changes], which [applyTo] lays over the AppView's view until it catches up. Votes
 * and confirmations are refused before anything changes for a session that can't write to its interactions space ([canVote]).
 */
@SingleIn(SessionScope::class)
@Inject
class TrailContributions(
    private val clients: SessionClients,
    private val writer: OutboxWriter,
    private val outbox: OutboxProcessor,
    private val clock: Clock,
    private val db: AppDatabase,
) {
    private val mutex = Mutex()

    /** What the athlete changed this session, by segment URI. */
    val changes: StateFlow<Map<String, TrailDetailChanges>>
        field = MutableStateFlow(emptyMap())

    /** The signed-in athlete, whose own suggestions they can't vote on. */
    val self: io.github.solcott.activitytracker.lexicon.Did
        get() = clients.did

    /** Whether this session can vote and confirm: its PDS holds spaces and its `space:` grant covers trail detail votes. */
    val canVote: Boolean
        get() = clients.canWriteToSpaces(Nsid.FEED_TRAIL_DETAIL_VOTE)

    /** Suggests [value] (only [detail]'s fields, [TrailDetail.isSuggestion]) for [segment]'s [detail], and returns the suggestion's URI. */
    suspend fun suggest(segment: AtUri, detail: String, value: TrailAttributes): AtUri {
        require(TrailDetail.isSuggestion(detail, value)) { "Not a value riders may suggest for $detail" }
        val rkey = Tid.next(clock)
        val uri = AtUri.of(clients.did, Nsid.FEED_TRAIL_DETAIL_SUGGESTION, rkey)
        val now = clock.now()
        val sent =
            TrailDetailSuggestionView(
                uri = uri,
                cid = Cid(PENDING_CID),
                detail = detail,
                value = value,
                author = ProfileViewBasic(clients.did, clients.handle),
                openedAt = now,
                up = 0,
                down = 0,
            )
        write(segment, { it.copy(sent = it.sent + sent) }) {
            writer.put(
                Nsid.FEED_TRAIL_DETAIL_SUGGESTION,
                rkey,
                TrailDetailSuggestionRecord.serializer(),
                TrailDetailSuggestionRecord(segment, detail, value, now),
            )
        }
        return uri
    }

    /**
     * Votes [direction] ([TrailDetailVoteDirection.UP] or [TrailDetailVoteDirection.DOWN]) on [suggestion] of [segment] at the version
     * listed, or takes the athlete's vote back when it's null; [current] is the vote the athlete sees now, whose record is rewritten.
     */
    suspend fun vote(segment: AtUri, suggestion: TrailDetailSuggestionView, direction: String?, current: TrailDetailViewerVote?) {
        clients.requireInteractionsFor(Nsid.FEED_TRAIL_DETAIL_VOTE, "Sign in again to vote on trail details")
        val uri = current?.uri ?: voteUri(trailVoteRkey(suggestion.uri))
        write(segment, { it.copy(votes = it.votes + (suggestion.uri.value to DirectionChange(uri, direction))) }) {
            queueVote(uri, direction) { dir ->
                TrailDetailVoteRecord(dir, clock.now(), subject = StrongRef(suggestion.uri, suggestion.cid))
            }
        }
    }

    /**
     * Says [segment]'s [detail], holding [value] in effect, is still right or has changed ([TrailDetailVoteDirection.STILL] or
     * [TrailDetailVoteDirection.CHANGED]), or takes it back when [direction] is null; [current] is the athlete's confirmation shown now.
     */
    suspend fun confirm(segment: AtUri, detail: String, value: TrailAttributes, direction: String?, current: TrailDetailViewerVote?) {
        clients.requireInteractionsFor(Nsid.FEED_TRAIL_DETAIL_VOTE, "Sign in again to confirm trail details")
        val shown = requireNotNull(TrailDetail.valueOf(value, detail)) { "No $detail to confirm" }
        val uri = current?.uri ?: voteUri(trailConfirmationRkey(segment, detail))
        write(segment, { it.copy(confirmations = it.confirmations + (detail to DirectionChange(uri, direction, shown))) }) {
            queueVote(uri, direction) { dir -> TrailDetailVoteRecord(dir, clock.now(), segment = segment, detail = detail, value = shown) }
        }
    }

    /**
     * Whether the quick check (segments/trail-detail-suggestions) may show for [segment] now: not yet shown on this device this season, a
     * meteorological season of the device's clock in UTC.
     */
    suspend fun quickCheckDue(segment: AtUri): Boolean = db.keyValue().get(quickCheckKey(segment)) != seasonOf(clock.now())

    /** Records that the quick check for [segment] was answered or put off, so it shows again only next season. */
    suspend fun quickCheckDone(segment: AtUri) {
        db.keyValue().put(KeyValueEntity(quickCheckKey(segment), seasonOf(clock.now())))
    }

    private fun quickCheckKey(segment: AtUri) = "trail.quickCheck.${clients.did.value}.${segment.value}"

    /** [view] with this session's [changes] to its trail details laid over it. */
    fun applyTo(view: SegmentView): SegmentView = changes.value[view.uri.value]?.let { applyTrailChanges(view, it, clock.now()) } ?: view

    /**
     * A vote's or confirmation's record at [rkey], derived from its subject: when the AppView reports none (it reports only a counted one),
     * a new one still rewrites whatever record the athlete left on that subject.
     */
    private fun voteUri(rkey: String) = AtUri.of(clients.did, Nsid.FEED_TRAIL_DETAIL_VOTE, rkey)

    private suspend fun queueVote(uri: AtUri, direction: String?, record: (String) -> TrailDetailVoteRecord) {
        val rkey = requireNotNull(uri.rkey)
        val interactions = clients.tiers.uri(AthleteSpace.INTERACTIONS)
        if (direction == null) writer.delete(Nsid.FEED_TRAIL_DETAIL_VOTE, rkey, interactions)
        else writer.put(Nsid.FEED_TRAIL_DETAIL_VOTE, rkey, TrailDetailVoteRecord.serializer(), record(direction), space = interactions)
    }

    private suspend fun write(segment: AtUri, change: (TrailDetailChanges) -> TrailDetailChanges, queue: suspend () -> Unit) {
        mutex.withLock {
            val previous = changes.value
            changes.update { it + (segment.value to change(it[segment.value] ?: TrailDetailChanges())) }
            undoingOnFailure({ changes.value = previous }) { queue() }
        }
        outbox.drain()
    }

    private companion object {
        private const val MONTHS_PER_SEASON = 3

        /** The year and meteorological season of [at] in UTC, December counting toward the next year's winter: "2027-0" for winter. */
        fun seasonOf(at: Instant): String {
            val date = at.toLocalDateTime(TimeZone.UTC).date
            val month = date.month.ordinal + 1
            val year = if (month == DECEMBER) date.year + 1 else date.year
            return "$year-${(month % MONTHS_IN_YEAR) / MONTHS_PER_SEASON}"
        }

        private const val DECEMBER = 12
        private const val MONTHS_IN_YEAR = 12

        /** A sent suggestion's CID until the AppView lists it: nobody can vote on their own suggestion, so it's never used. */
        const val PENDING_CID = "pending"
    }
}

/**
 * The athlete's changes to one segment's trail details this session: suggestions they sent, their votes by suggestion URI, and their
 * confirmations by detail.
 */
data class TrailDetailChanges(
    val sent: List<TrailDetailSuggestionView> = emptyList(),
    val votes: Map<String, DirectionChange> = emptyMap(),
    val confirmations: Map<String, DirectionChange> = emptyMap(),
)

/**
 * The athlete's vote or confirmation record [uri] and its [direction], null once taken back; for a confirmation, the [value] it names
 * ([TrailDetail.valueOf]), which it shows over only while that's the value in effect.
 */
data class DirectionChange(val uri: AtUri, val direction: String?, val value: TrailAttributes? = null)

/**
 * [view] with [changes] laid over it at [now]: a sent suggestion shows until the AppView lists it, or for [SENT_GRACE] at most (the AppView
 * drops for good one that breaks a rule, so it would never be listed), and closes its detail to more of the athlete's meanwhile; a vote
 * moves its suggestion's totals by its difference from the vote the AppView reports, so it never counts twice; a confirmation moves its
 * detail's count the same way, while the value it names is still the one in effect.
 */
internal fun applyTrailChanges(view: SegmentView, changes: TrailDetailChanges, now: Instant): SegmentView {
    val listed =
        view.trailSuggestions.map { suggestion -> changes.votes[suggestion.uri.value]?.let { suggestion.withVote(it) } ?: suggestion }
    val sent = changes.sent.filter { s -> listed.none { it.uri == s.uri } && now - s.openedAt < SENT_GRACE }
    val details =
        view.trailDetails.map { info ->
            val change = changes.confirmations[info.detail] ?: return@map info
            if (change.value != null && view.trail?.let { TrailDetail.valueOf(it, info.detail) } != change.value) return@map info
            val before = info.viewerConfirmation?.direction
            if (before == change.direction) return@map info
            val stills = (info.confirmations ?: 0) - still(before) + still(change.direction)
            info.copy(confirmations = stills, viewerConfirmation = change.direction?.let { TrailDetailViewerVote(change.uri, it) })
        }
    val closed = (view.viewer?.closedTrailDetails.orEmpty() + sent.map { it.detail }).distinct()
    return view.copy(
        trailSuggestions = listed + sent,
        trailDetails = details,
        viewer = view.viewer?.copy(closedTrailDetails = closed),
    )
}

/** How long a sent suggestion shows before the AppView lists it: Jetstream takes seconds, so one still unlisted was dropped. */
internal val SENT_GRACE = 5.minutes

private fun still(direction: String?) = if (direction == TrailDetailVoteDirection.STILL) 1 else 0

private fun TrailDetailSuggestionView.withVote(change: DirectionChange): TrailDetailSuggestionView {
    val before = viewerVote?.direction
    val after = change.direction
    if (before == after) return this
    fun count(direction: String?, of: String) = if (direction == of) 1 else 0
    return copy(
        up = up - count(before, TrailDetailVoteDirection.UP) + count(after, TrailDetailVoteDirection.UP),
        down = down - count(before, TrailDetailVoteDirection.DOWN) + count(after, TrailDetailVoteDirection.DOWN),
        viewerVote = after?.let { TrailDetailViewerVote(change.uri, it) },
    )
}
