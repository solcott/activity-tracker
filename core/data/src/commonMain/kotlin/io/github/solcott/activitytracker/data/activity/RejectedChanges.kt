package io.github.solcott.activitytracker.data.activity

import androidx.compose.runtime.Immutable
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.github.solcott.activitytracker.data.db.AppDatabase
import io.github.solcott.activitytracker.data.db.OutboxEntity
import io.github.solcott.activitytracker.data.db.RejectedOutboxWrite
import io.github.solcott.activitytracker.data.db.UploadItemEntity
import io.github.solcott.activitytracker.data.di.SessionClients
import io.github.solcott.activitytracker.data.di.SessionScope
import io.github.solcott.activitytracker.data.feed.FeedRepository
import io.github.solcott.activitytracker.data.feed.LocalLikes
import io.github.solcott.activitytracker.lexicon.AtJson
import io.github.solcott.activitytracker.lexicon.AtUri
import io.github.solcott.activitytracker.lexicon.Nsid
import io.github.solcott.activitytracker.lexicon.app.ActivityRecord
import io.github.solcott.activitytracker.lexicon.app.CommentRecord
import io.github.solcott.activitytracker.lexicon.app.LikeRecord
import kotlin.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.KSerializer

/**
 * A change the athlete's PDS refused (data/outbox): its rejected write and the writes held behind it, which share its [rkey]. [kind] says
 * what it is; an activity's [title], [sport] and [startedAt] come from its queued record, when there is one.
 */
@Immutable
data class RejectedChange(
    val rkey: String,
    val kind: Kind,
    val rejectedAt: Instant,
    val title: String? = null,
    val sport: String? = null,
    val startedAt: Instant? = null,
) {
    enum class Kind {
        Activity,
        Like,
        Comment,
        CommentHide,
        Block,
        Mute,
        Segment,
        SegmentStar,
        Other,
    }
}

/**
 * The athlete's changes their PDS refused, one per rejected write ([OutboxProcessor] keeps them), and the two ways out: [retry] sends them
 * again, [discard] drops them and shows what the PDS holds instead. The upload queue lists them ([UploadQueue]) and cancels through
 * [discard].
 */
@SingleIn(SessionScope::class)
@Inject
class RejectedChanges(
    private val db: AppDatabase,
    private val clients: SessionClients,
    private val outbox: OutboxProcessor,
    private val feed: FeedRepository,
    private val localLikes: LocalLikes,
) {
    private val did: String
        get() = clients.did.value

    val changes: Flow<List<RejectedChange>> =
        db.outbox().rejected(did, Nsid.FEED_ACTIVITY).map { rows -> rows.distinctBy { it.rkey }.map { it.toChange() } }

    /**
     * Sends [change]'s writes again, in their place in the outbox, and returns how many writes are left unsent. A write the PDS refuses
     * again is rejected again.
     */
    suspend fun retry(change: RejectedChange): Int = retry(change.rkey)

    /** [retry] for the change at [rkey]. */
    suspend fun retry(rkey: String): Int {
        db.outbox().clearRejected(did, rkey)
        return outbox.drain()
    }

    /** Drops [change]'s writes from the device, and the athlete's own changes laid over what the AppView serves for its record. */
    suspend fun discard(change: RejectedChange) {
        discard(change.rkey)
    }

    /**
     * Drops [rkey]'s writes and its live upload item from the device (data/upload-queue), and the athlete's own changes laid over what the
     * AppView serves for its record, unless one of its writes is being sent or [refuse] says no: both are asked under the claim
     * ([AccountPacing.claim]), so no write of it can start meanwhile. A new activity leaves the athlete's lists, since none of it is
     * published; an edit shows what the PDS holds again. Returns whether it dropped them.
     */
    internal suspend fun discard(rkey: String, refuse: suspend () -> Boolean = { false }): Boolean {
        val pacing = clients.pacing
        val (rows, item) =
            pacing.claim.withLock {
                if (pacing.sending.value == rkey || refuse()) return false
                val rows = db.outbox().pending(did, rkey)
                val item = db.uploads().live(did, rkey)
                db.uploads().cancel(did, rkey)
                rows to item
            }
        val changed = rows.flatMap { forget(it) }.distinct()
        if (item?.kind == UploadItemEntity.KIND_NEW) {
            val uri = AtUri.of(clients.did, Nsid.FEED_ACTIVITY, rkey)
            feed.deletes.delete(uri)
            db.feed().deleteByUri(uri.value)
        }
        changed.forEach { feed.reloadCached(it) }
        return true
    }

    /** Forgets what [row] laid over the AppView's reads, and returns the activities whose cached views it changed. */
    private fun forget(row: OutboxEntity): List<AtUri> {
        val uri = AtUri.of(clients.did, row.collection, row.rkey)
        return when (row.collection) {
            Nsid.FEED_ACTIVITY -> {
                feed.rewrites.forget(uri)
                feed.deletes.forget(uri)
                listOf(uri)
            }
            Nsid.FEED_LIKE -> {
                localLikes.forget(uri)
                listOfNotNull(row.recordJson?.let { decodeOrNull(LikeRecord.serializer(), it) }?.subject?.uri)
            }
            Nsid.FEED_COMMENT -> {
                feed.localComments.restore(uri, null)
                listOfNotNull(row.recordJson?.let { decodeOrNull(CommentRecord.serializer(), it) }?.subject?.uri)
            }
            else -> emptyList()
        }
    }

    private fun RejectedOutboxWrite.toChange(): RejectedChange {
        val activity = activityJson?.let { decodeOrNull(ActivityRecord.serializer(), it) }
        return RejectedChange(
            rkey = rkey,
            kind = kindOf(collection),
            rejectedAt = Instant.fromEpochMilliseconds(rejectedAt),
            title = activity?.title,
            sport = activity?.sport,
            startedAt = activity?.startedAt,
        )
    }

    private fun <T> decodeOrNull(serializer: KSerializer<T>, json: String): T? = runCatching {
        AtJson.decodeFromString(serializer, json)
    }
        .getOrNull()

    private companion object {
        /** An activity's own records, which share its rkey and so stand for it. */
        val ACTIVITY_RECORDS =
            setOf(
                Nsid.FEED_ACTIVITY,
                Nsid.FEED_ACTIVITY_DETAILS,
                Nsid.FEED_ACTIVITY_STREAMS,
                Nsid.FEED_ACTIVITY_TRACK,
                Nsid.FEED_ACTIVITY_SOURCE,
                Nsid.FEED_ACTIVITY_DEVICES,
                Nsid.FEED_ACTIVITY_ZONES,
                Nsid.FEED_SEGMENT_TRACK,
            )

        fun kindOf(collection: String): RejectedChange.Kind =
            when (collection) {
                in ACTIVITY_RECORDS -> RejectedChange.Kind.Activity
                Nsid.FEED_LIKE -> RejectedChange.Kind.Like
                Nsid.FEED_COMMENT -> RejectedChange.Kind.Comment
                Nsid.FEED_COMMENT_HIDE -> RejectedChange.Kind.CommentHide
                Nsid.GRAPH_BLOCK -> RejectedChange.Kind.Block
                Nsid.GRAPH_MUTE -> RejectedChange.Kind.Mute
                Nsid.FEED_SEGMENT,
                Nsid.FEED_SEGMENT_NAME_PROPOSAL,
                Nsid.FEED_SEGMENT_NAME_VOTE -> RejectedChange.Kind.Segment
                Nsid.FEED_SEGMENT_STAR -> RejectedChange.Kind.SegmentStar
                else -> RejectedChange.Kind.Other
            }
    }
}
