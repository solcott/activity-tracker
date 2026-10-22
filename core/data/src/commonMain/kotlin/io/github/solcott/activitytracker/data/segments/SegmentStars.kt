package io.github.solcott.activitytracker.data.segments

import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.github.solcott.activitytracker.atproto.runSuspendCatching
import io.github.solcott.activitytracker.atproto.segmentStarRkey
import io.github.solcott.activitytracker.atproto.space.SpaceClient
import io.github.solcott.activitytracker.atproto.space.SpaceRead
import io.github.solcott.activitytracker.atproto.xrpc.XrpcException
import io.github.solcott.activitytracker.data.activity.OutboxProcessor
import io.github.solcott.activitytracker.data.activity.OutboxWriter
import io.github.solcott.activitytracker.data.activity.undoingOnFailure
import io.github.solcott.activitytracker.data.di.SessionClients
import io.github.solcott.activitytracker.data.di.SessionScope
import io.github.solcott.activitytracker.data.outcomeOf
import io.github.solcott.activitytracker.lexicon.AtJson
import io.github.solcott.activitytracker.lexicon.AtUri
import io.github.solcott.activitytracker.lexicon.Nsid
import io.github.solcott.activitytracker.lexicon.app.AthleteSpace
import io.github.solcott.activitytracker.lexicon.app.SegmentStarRecord
import io.github.solcott.activitytracker.lexicon.atproto.Space
import io.github.solcott.activitytracker.lexicon.atproto.SpaceErrors
import io.github.solcott.activitytracker.lexicon.atproto.SpaceUri
import io.github.solcott.activitytracker.lexicon.atproto.StrongRef
import io.github.solcott.dataresult.DataError
import io.github.solcott.dataresult.Origin
import io.github.solcott.dataresult.Outcome
import io.github.solcott.dataresult.mapData
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** A segment the athlete starred, and when. */
data class SegmentStar(val segment: StrongRef, val starredAt: Instant)

/**
 * The athlete's stars on segments (segments/segment-favorites): one `feed.segmentStar` per segment, keyed by [segmentStarRkey], in their
 * `self` space only, so only they read them; the AppView, which syncs `self`, never stores them. Writes go through the outbox and show at
 * once through [overrides] until this session ends, as blocks do: a delayed star harms nothing. A session whose `space:` grant doesn't
 * cover the collection can't star ([canUse]) and is refused before anything changes.
 */
@SingleIn(SessionScope::class)
@Inject
class SegmentStars(
    private val clients: SessionClients,
    private val writer: OutboxWriter,
    private val outbox: OutboxProcessor,
    private val clock: Clock,
) {
    private val mutex = Mutex()

    /** Stars changed this session, by segment URI: the star, or null once removed. They win over what the PDS holds. */
    val overrides: StateFlow<Map<String, SegmentStar?>>
        field = MutableStateFlow(emptyMap())

    /** Whether this session can star segments: not one signed in before its `space:` grant listed `feed.segmentStar`. */
    val canUse: Boolean
        get() = clients.canWriteToSpaces(Nsid.FEED_SEGMENT_STAR)

    /** Whether the athlete starred [segment]; null when that can't be read now, or this session can't use stars. */
    suspend fun isStarred(segment: AtUri): Boolean? {
        val changed = overrides.value
        return when {
            !canUse -> null
            segment.value in changed -> changed[segment.value] != null
            else ->
                when (read { getRecord(it, clients.did, Nsid.FEED_SEGMENT_STAR, segmentStarRkey(segment)) }) {
                    is SpaceRead.Found -> true
                    SpaceRead.NotFound -> false
                    SpaceRead.NoAccess,
                    null -> null
                }
        }
    }

    /**
     * Every star, newest first, with this session's changes laid over them, or why they can't be read now: they are unknown then, not none.
     * The PDS lists them by rkey, a hash, so all are read to order them. Records that don't decode, or whose rkey isn't their subject's,
     * are left out.
     */
    suspend fun starredOutcome(): Outcome<List<SegmentStar>> {
        if (!canUse) return Outcome.Error(DataError.Unknown(message = "This session can't use stars"), Origin.Network)
        // Null when the space refuses the athlete their own stars.
        val stored = outcomeOf {
            clients.spaces.listAllRecords(clients.tiers.uri(AthleteSpace.SELF), clients.did, Nsid.FEED_SEGMENT_STAR, PAGE_SIZE)
        }
            .mapData { answer ->
                when (answer) {
                    is SpaceRead.Found -> answer.value.mapNotNull { it.star() }
                    SpaceRead.NotFound -> emptyList()
                    SpaceRead.NoAccess -> null
                }
            }
        return when (stored) {
            is Outcome.Data ->
                stored.data?.let { Outcome.Data(withOverrides(it), Origin.Network) }
                    ?: Outcome.Error(DataError.Unknown(message = "No access to the self space"), Origin.Network)
            is Outcome.Error -> stored
            Outcome.Loading -> Outcome.Loading
        }
    }

    /** [stored] with this session's changes laid over it, newest first. */
    private fun withOverrides(stored: List<SegmentStar>): List<SegmentStar> {
        val changed = overrides.value
        return (stored.filter { it.segment.uri.value !in changed } + changed.values.filterNotNull()).sortedByDescending { it.starredAt }
    }

    /** Stars ([starred]) or unstars [segment]. Throws for a session that can't use stars, changing nothing. */
    suspend fun setStarred(segment: StrongRef, starred: Boolean) {
        mutex.withLock {
            if (!canUse) throw XrpcException(SCOPE_MISSING_STATUS, SpaceErrors.SCOPE_MISSING, "Sign in again to star segments")
            val key = segment.uri.value
            val previous = overrides.value
            val star = SegmentStar(segment, clock.now()).takeIf { starred }
            overrides.update { it + (key to star) }
            undoingOnFailure({ overrides.value = previous }) {
                val rkey = segmentStarRkey(segment.uri)
                val self = clients.tiers.uri(AthleteSpace.SELF)
                if (star != null) {
                    val record = SegmentStarRecord(segment, star.starredAt)
                    writer.put(Nsid.FEED_SEGMENT_STAR, rkey, SegmentStarRecord.serializer(), record, space = self)
                } else {
                    writer.delete(Nsid.FEED_SEGMENT_STAR, rkey, self)
                }
            }
        }
        outbox.drain()
    }

    /** [block] on the athlete's `self` space; null when it fails. */
    private suspend fun <T> read(block: suspend SpaceClient.(SpaceUri) -> SpaceRead<T>): SpaceRead<T>? = runSuspendCatching {
        clients.spaces.block(clients.tiers.uri(AthleteSpace.SELF))
    }
        .getOrNull()

    /** The star [this] record holds, if it decodes and is stored under its subject's rkey. */
    private suspend fun Space.ListRecords.Record.star(): SegmentStar? {
        val record = value?.let { runCatching { AtJson.decodeFromJsonElement(SegmentStarRecord.serializer(), it) }.getOrNull() }
        return record?.takeIf { segmentStarRkey(it.subject.uri) == rkey }?.let { SegmentStar(it.subject, it.createdAt) }
    }

    private companion object {
        const val PAGE_SIZE = 100
        const val SCOPE_MISSING_STATUS = 403
    }
}
