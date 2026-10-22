package io.github.solcott.activitytracker.data.segments

import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.github.solcott.activitytracker.atproto.xrpc.XrpcException
import io.github.solcott.activitytracker.data.di.SessionClients
import io.github.solcott.activitytracker.data.di.SessionScope
import io.github.solcott.activitytracker.data.outcomeFlow
import io.github.solcott.activitytracker.data.outcomeOf
import io.github.solcott.activitytracker.lexicon.AtUri
import io.github.solcott.activitytracker.lexicon.app.CheckSegment
import io.github.solcott.activitytracker.lexicon.app.EffortView
import io.github.solcott.activitytracker.lexicon.app.GetActivitySegments
import io.github.solcott.activitytracker.lexicon.app.GetSegment
import io.github.solcott.activitytracker.lexicon.app.GetSegmentEfforts
import io.github.solcott.activitytracker.lexicon.app.GetSegmentLeaderboard
import io.github.solcott.activitytracker.lexicon.app.LeaderboardEntry
import io.github.solcott.activitytracker.lexicon.app.SearchSegments
import io.github.solcott.activitytracker.lexicon.app.SegmentView
import io.github.solcott.dataresult.Outcome
import io.github.solcott.uistate.Page
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

/** Segments and the efforts on them, read from the AppView (segments/segment-records, segments/segment-efforts). */
@SingleIn(SessionScope::class)
@Inject
class SegmentRepository(private val clients: SessionClients) {
    /** The activities the signed-in athlete published a segment from this session ([SegmentPublisher]), so their pages can say so. */
    val createdFrom: StateFlow<Set<AtUri>>
        field = MutableStateFlow(emptySet())

    /** Notes that a segment was published from [activity]. */
    fun markCreated(activity: AtUri) = createdFrom.update { it + activity }

    /** Forgets [activity]'s note, once its page has said so. */
    fun dismissCreated(activity: AtUri) = createdFrom.update { it - activity }

    /** The segment at [uri], with the signed-in athlete's best effort on it. */
    suspend fun segment(uri: AtUri): SegmentView = clients.appView.query(GetSegment.method, GetSegment.Params(uri)).segment

    /**
     * The segment at [uri], or null when the AppView no longer serves it (`NotFound`: deleted, never indexed or taken down), as a starred
     * segment's lookup reads it.
     */
    suspend fun segmentLookup(uri: AtUri): Outcome<SegmentView?> = outcomeOf {
        try {
            segment(uri)
        } catch (e: XrpcException) {
            if (e.error == NOT_FOUND) null else throw e
        }
    }

    /** [segment] as a stream: loading, then the segment or why it couldn't be read. Each collection reads it again. */
    fun segmentOutcome(uri: AtUri): Flow<Outcome<SegmentView>> = outcomeFlow { segment(uri) }

    /** The first page of the signed-in athlete's own efforts on [segment], fastest first, as a stream like [segmentOutcome]. */
    fun ownEffortsOutcome(segment: AtUri): Flow<Outcome<List<EffortView>>> = outcomeFlow { efforts(segment).efforts }

    /** The efforts on [activity] the signed-in athlete may see, in the order they began. */
    suspend fun activitySegments(activity: AtUri): List<EffortView> =
        clients.appView.query(GetActivitySegments.method, GetActivitySegments.Params(activity)).efforts

    /** One page of [actor]'s efforts on [segment], fastest first; the signed-in athlete's own when [actor] is null. */
    suspend fun efforts(segment: AtUri, actor: String? = null, cursor: String? = null): GetSegmentEfforts.Output =
        clients.appView.query(GetSegmentEfforts.method, GetSegmentEfforts.Params(segment, actor ?: clients.did.value, cursor = cursor))

    /**
     * The live segments of [sport] that [line] (precision 6) would duplicate, earliest first, and how many more segments the signed-in
     * athlete may create today; asked before publishing one ([SegmentPublisher]).
     */
    suspend fun check(sport: String, line: String): CheckSegment.Output =
        clients.appView.procedure(CheckSegment.method, CheckSegment.Input(sport, line))

    /**
     * One page of [segment]'s leaderboard (segments/segment-leaderboards), fastest first, with the signed-in athlete's own entry as its
     * meta: null when they aren't on the board.
     */
    suspend fun leaderboardPage(
        segment: AtUri,
        period: String,
        scope: String,
        eBike: Boolean,
        cursor: String? = null,
    ): Outcome<Page<LeaderboardEntry, LeaderboardEntry?>> = outcomeOf {
        val board =
            clients.appView.query(
                GetSegmentLeaderboard.method,
                GetSegmentLeaderboard.Params(segment, period, scope, eBike = eBike.takeIf { it }, cursor = cursor),
            )
        Page(board.entries, board.viewer, board.cursor)
    }

    /** One page of segments whose line passes through [params]' box. */
    suspend fun search(params: SearchSegments.Params): SearchSegments.Output = clients.appView.query(SearchSegments.method, params)
}

private const val NOT_FOUND = "NotFound"
