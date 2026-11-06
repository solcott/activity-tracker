package io.github.solcott.activitytracker.appview.index

import io.github.solcott.activitytracker.appview.ProfileDetailRules
import io.github.solcott.activitytracker.appview.db.Activities
import io.github.solcott.activitytracker.appview.db.ActivityDetails
import io.github.solcott.activitytracker.appview.db.ActivityGear
import io.github.solcott.activitytracker.appview.db.ActivityMediaBlobs
import io.github.solcott.activitytracker.appview.db.ActivityMembers
import io.github.solcott.activitytracker.appview.db.ActivityStreams
import io.github.solcott.activitytracker.appview.db.ActivityZones
import io.github.solcott.activitytracker.appview.db.Actors
import io.github.solcott.activitytracker.appview.db.Blocks
import io.github.solcott.activitytracker.appview.db.CommentHides
import io.github.solcott.activitytracker.appview.db.Comments
import io.github.solcott.activitytracker.appview.db.DiveSiteCreations
import io.github.solcott.activitytracker.appview.db.FollowApprovals
import io.github.solcott.activitytracker.appview.db.Follows
import io.github.solcott.activitytracker.appview.db.GearItems
import io.github.solcott.activitytracker.appview.db.GearMountRows
import io.github.solcott.activitytracker.appview.db.GroupPrefs
import io.github.solcott.activitytracker.appview.db.LeaderboardBrackets
import io.github.solcott.activitytracker.appview.db.Likes
import io.github.solcott.activitytracker.appview.db.Mutes
import io.github.solcott.activitytracker.appview.db.Origins
import io.github.solcott.activitytracker.appview.db.RecordShadows
import io.github.solcott.activitytracker.appview.db.SegmentCreations
import io.github.solcott.activitytracker.appview.db.SegmentPrefs
import io.github.solcott.activitytracker.appview.db.SpaceSync
import io.github.solcott.activitytracker.appview.db.tx
import io.github.solcott.activitytracker.appview.segments.SegmentJobs
import io.github.solcott.activitytracker.appview.segments.SegmentNameRules
import io.github.solcott.activitytracker.atproto.commentHideRkey
import io.github.solcott.activitytracker.atproto.identity.IdentityResolver
import io.github.solcott.activitytracker.lexicon.AtJson
import io.github.solcott.activitytracker.lexicon.AtUri
import io.github.solcott.activitytracker.lexicon.Did
import io.github.solcott.activitytracker.lexicon.Nsid
import io.github.solcott.activitytracker.lexicon.app.ActivityDetailsRecord
import io.github.solcott.activitytracker.lexicon.app.ActivityInviteRecord
import io.github.solcott.activitytracker.lexicon.app.ActivityStreamsRecord
import io.github.solcott.activitytracker.lexicon.app.AthleteSpace
import io.github.solcott.activitytracker.lexicon.app.BlockRecord
import io.github.solcott.activitytracker.lexicon.app.CommentHideRecord
import io.github.solcott.activitytracker.lexicon.app.CommentRecord
import io.github.solcott.activitytracker.lexicon.app.FollowApprovalRecord
import io.github.solcott.activitytracker.lexicon.app.FollowPolicy
import io.github.solcott.activitytracker.lexicon.app.FollowRecord
import io.github.solcott.activitytracker.lexicon.app.LikeRecord
import io.github.solcott.activitytracker.lexicon.app.MuteRecord
import io.github.solcott.activitytracker.lexicon.app.ProfileRecord
import io.github.solcott.activitytracker.lexicon.app.ReplyRef
import io.github.solcott.activitytracker.lexicon.app.Visibility
import io.github.solcott.activitytracker.lexicon.atproto.SpaceUri
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.jetbrains.exposed.v1.core.Column
import org.jetbrains.exposed.v1.core.Op
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.like
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.JdbcTransaction
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insertIgnore
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import org.jetbrains.exposed.v1.jdbc.upsert
import org.slf4j.LoggerFactory

/**
 * Applies repo commits, and writes synced from athletes' tier and interactions spaces, to the index. Every write is an upsert/delete keyed
 * by AT-URI, so replaying events (e.g. after rewinding the Jetstream cursor) is safe. Records that fail to decode are dropped: repos are
 * untrusted input.
 *
 * Activities and their zones come from the repo (public) or a tier space, each an origin ([Placement]). Mid-move one URI can be held by two
 * origins: the narrower tier's copy is the indexed row, the other waits in [RecordShadows], and a delete removes only its own origin's
 * copy, promoting a shadow left behind. An activity's streams and details are split by tier, so each origin's copy is a row of its own.
 * Likes come from the repo (on public activities) or the giver's interactions space, and are never moved, so they need no shadows: a like's
 * row just records its origin, and only that origin's delete removes it.
 */
@Suppress("TooManyFunctions") // Each kind of change it applies, and the transactions they share.
class Indexer(
    private val db: Database,
    private val handles: HandleLookup,
    private val clock: Clock = Clock.System,
    /** The segment importer's account (`SEGMENT_IMPORTER_DID`), whose new segments aren't limited per day and start active. */
    private val segmentImporter: Did? = null,
    /** Who may propose segment names (segments/segment-names). */
    private val segmentNames: SegmentNameRules = SegmentNameRules(),
    /** Whether terrain tiles are configured (segments/segment-elevation): new and re-lined segments are then queued to be sampled. */
    terrain: Boolean = false,
) {
    private val log = LoggerFactory.getLogger(Indexer::class.java)

    /** Bluesky blocks, from Jetstream here and from athletes' repos through `BskyBlockBackfill`. */
    val bskyBlocks = BskyBlockIndex(db, clock)

    /** The segment matcher's queue, which indexing fills in the transaction of each change that calls for a match. */
    val segmentJobs = SegmentJobs(terrain)

    /** DIDs known to have an `actors` row, whose handle was checked at first sight; later checks come from [recheckHandle]. */
    private val knownActors = ConcurrentHashMap.newKeySet<String>()

    /**
     * Locks by AT-URI (striped), held around each transaction that writes or deletes a record: Jetstream and the space syncer can index two
     * copies of one URI at once (a move between the repo and a space), and each decides from the other's row, so they mustn't overlap.
     */
    private val uriLocks = Array(URI_LOCK_STRIPES) { Mutex() }

    private suspend fun <T> lockedOn(uri: AtUri, block: suspend () -> T): T =
        uriLocks[uri.value.hashCode().mod(URI_LOCK_STRIPES)].withLock { block() }

    suspend fun handle(event: JetstreamEvent) {
        when (event) {
            is JetstreamEvent.Commit -> {
                commit(Did(event.did), event)
            }

            // identity/handle-verification: the event's handle is never trusted; the account is checked again instead.
            is JetstreamEvent.Identity -> {
                recheckHandle(Did(event.did))
            }

            // `moderation/account-status`: a deleted account's data goes; a takendown, suspended or deactivated one is kept but not served
            // (`actors.status`, read by `served`) until it's active again. Other statuses change nothing.
            is JetstreamEvent.Account -> {
                val did = Did(event.did)
                val status = event.account.status.takeUnless { event.account.active }
                when {
                    status == DELETED -> deleteActor(did)
                    event.account.active || status in INACTIVE ->
                        db.tx { Actors.update({ Actors.did eq did.value }) { it[Actors.status] = status } }
                }
            }
        }
    }

    private suspend fun commit(author: Did, commit: JetstreamEvent.Commit) {
        if (commit.collection !in Nsid.indexedCollections) return
        val uri = AtUri.of(author, commit.collection, commit.rkey)
        val record = commit.record
        val cid = commit.cid
        when {
            commit.operation == "delete" -> lockedOn(uri) { db.tx { delete(commit.collection, uri, Placement.REPO) } }
            record != null && cid != null && commit.collection == Nsid.BSKY_GRAPH_BLOCK -> bskyBlocks.put(author, uri, record)
            record != null && cid != null -> index(author, commit.collection, uri, cid, record, Placement.REPO)
        }
    }

    /**
     * A put of [collection]/[rkey] in [space]'s authority repo. Only [spaceCollections] for the space's key are indexed. [whileSynced] (the
     * syncer's writes) indexes it only while the space is still synced, checked in the same transaction: an account deleted meanwhile
     * ([deleteActor]) drops the space first, so a sync still running can't bring its records back.
     */
    @Suppress("LongParameterList") // The record, where it's from, and whether its space must still be synced.
    suspend fun spaceWrite(
        space: SpaceUri,
        collection: String,
        rkey: String,
        cid: String,
        record: JsonObject,
        whileSynced: Boolean = false,
    ) {
        if (collection !in spaceCollections(space.skey)) return
        val uri = AtUri.of(space.authority, collection, rkey)
        index(space.authority, collection, uri, cid, record, Placement.of(space), syncedSpace = space.takeIf { whileSynced })
    }

    /** A delete of [collection]/[rkey] in [space]'s authority repo. */
    suspend fun spaceDelete(space: SpaceUri, collection: String, rkey: String) {
        if (collection !in spaceCollections(space.skey)) return
        val uri = AtUri.of(space.authority, collection, rkey)
        lockedOn(uri) { db.tx { delete(collection, uri, Placement.of(space)) } }
    }

    /**
     * Everything indexed from [space], after it was deleted: each record [spaceRkeys] lists for its collections deleted as from the space,
     * then whatever no record names any more (its copies waiting in [RecordShadows], rows held by its origin, and what `self`'s tracks and
     * invites derived).
     */
    suspend fun dropSpace(space: SpaceUri) = db.tx {
        val placement = Placement.of(space)
        for (collection in spaceCollections(space.skey)) {
            spaceRkeys(space, collection).forEach { delete(collection, AtUri.of(space.authority, collection, it), placement) }
        }
        RecordShadows.deleteWhere { origin eq placement.origin }
        for (held in HELD_ROWS) held.origin?.let { column -> held.table.deleteWhere { column eq placement.origin } }
        if (space.skey == AthleteSpace.SELF) {
            dropSegmentTracks(space.authority)
            dropInvitesOf(space.authority)
        }
    }

    @Suppress("LongParameterList") // The record, and where it's from.
    private suspend fun index(
        author: Did,
        collection: String,
        uri: AtUri,
        cid: String,
        record: JsonObject,
        placement: Placement,
        syncedSpace: SpaceUri? = null,
    ) {
        val check = firstSightCheck(author)
        try {
            // Checked before the transaction: the rkey is a SHA-256, which suspends.
            if (collection == Nsid.FEED_COMMENT_HIDE) requireHideKey(uri, record)
            lockedOn(uri) {
                db.tx {
                    // Locks the space's sync row until this commits, so deleteActor (which drops it first) waits for this write or skips
                    // it.
                    if (syncedSpace != null && SpaceSync.selectAll().where { SpaceSync.space eq syncedSpace.value }.forUpdate().empty()) {
                        return@tx
                    }
                    val now = clock.now().toEpochMilliseconds()
                    ensureActor(author, check, now)
                    // Segment tracks and the setting come only from `self` (spaceCollections), so they have no placement to keep.
                    if (collection in SEGMENT_COLLECTIONS) {
                        upsertSegments(collection, record, author, uri, segmentJobs, now)
                    } else {
                        val activity = effortActivity(collection, uri)
                        val before = activity?.let { matchInputs(it) }
                        upsert(collection, author, uri, cid, record, placement)
                        if (activity != null) activityChanged(activity, before, segmentJobs, now)
                    }
                }
            }
        } catch (e: SerializationException) {
            // Before IllegalArgumentException, which SerializationException extends.
            log.debug("Skipping undecodable {} record {}: {}", collection, uri, e.message)
        } catch (e: IllegalArgumentException) {
            log.debug("Skipping invalid {} record {}: {}", collection, uri, e.message)
        }
    }

    @Suppress("LongParameterList", "LongMethod", "CyclomaticComplexMethod") // The record, and where it's from; a branch per collection.
    private fun JdbcTransaction.upsert(
        collection: String,
        author: Did,
        uri: AtUri,
        cid: String,
        json: JsonObject,
        placement: Placement,
    ) {
        val now = clock.now().toEpochMilliseconds()
        when (collection) {
            Nsid.FEED_ACTIVITY,
            Nsid.FEED_ACTIVITY_ZONES,
            Nsid.GEAR_ITEM -> {
                upsertPlaced(collection, author, uri, cid, json, placement, now)
            }

            // Each tier's copy is a row of its own: where it's stored is who may see it, and a `visibility` it carries is ignored.
            Nsid.FEED_ACTIVITY_STREAMS -> {
                upsertStreams(
                    AtJson.decodeFromJsonElement(ActivityStreamsRecord.serializer(), json),
                    author,
                    uri,
                    cid,
                    json,
                    placement,
                    now,
                )
            }

            Nsid.FEED_ACTIVITY_DETAILS -> {
                upsertDetails(AtJson.decodeFromJsonElement(ActivityDetailsRecord.serializer(), json), author, uri, cid, placement, now)
            }

            Nsid.FEED_LIKE -> {
                val record = AtJson.decodeFromJsonElement(LikeRecord.serializer(), json)
                require(record.subject.uri.authority != author.value) { "like on own activity" }
                Likes.upsert {
                    it[Likes.uri] = uri.value
                    it[authorDid] = author.value
                    it[subjectUri] = record.subject.uri.value
                    it[subjectCid] = record.subject.cid.value
                    it[createdAt] = record.createdAt.toEpochMilliseconds()
                    it[indexedAt] = now
                    it[origin] = placement.origin
                }
            }

            Nsid.FEED_COMMENT -> {
                upsertComment(AtJson.decodeFromJsonElement(CommentRecord.serializer(), json), author, uri, cid, placement, now)
            }

            Nsid.FEED_COMMENT_HIDE -> {
                upsertHide(AtJson.decodeFromJsonElement(CommentHideRecord.serializer(), json), author, uri, placement, now)
            }

            Nsid.GRAPH_FOLLOW -> {
                val record = AtJson.decodeFromJsonElement(FollowRecord.serializer(), json)
                Follows.upsert {
                    it[Follows.uri] = uri.value
                    it[authorDid] = author.value
                    it[subjectDid] = record.subject.value
                    it[createdAt] = record.createdAt.toEpochMilliseconds()
                    it[indexedAt] = now
                }
                regroupRecent(author, record.subject, segmentJobs, now)
            }

            Nsid.GRAPH_FOLLOW_APPROVAL -> {
                val record = AtJson.decodeFromJsonElement(FollowApprovalRecord.serializer(), json)
                upsertApproval(record, author, uri, now)
                regroupRecent(author, record.subject, segmentJobs, now)
            }

            in BLOCKS_AND_MUTES -> upsertBlockOrMute(collection, json, author, uri, placement, now)

            // Public: from the repo only (spaceCollections never lists it).
            Nsid.FEED_SEGMENT -> {
                if (placement.origin == Origins.REPO) upsertSegment(json, author, uri, cid, segmentJobs, now, segmentImporter)
            }

            // Public: from the repo only (spaceCollections never lists it).
            Nsid.FEED_DIVE_SITE -> {
                if (placement.origin == Origins.REPO) upsertDiveSite(json, author, uri, cid, segmentJobs, now, segmentImporter)
            }

            // Public, so from the repo only; a copy in a space is ignored.
            Nsid.FEED_SEGMENT_NAME_PROPOSAL -> {
                if (placement.origin == Origins.REPO) upsertNameProposal(json, author, uri, cid, segmentNames, now)
            }

            // Private, so from the self space only (spaceCollections); the firehose never delivers it (indexedCollections).
            Nsid.FEED_ACTIVITY_INVITE -> {
                if (placement.origin != Origins.REPO)
                    upsertInvite(AtJson.decodeFromJsonElement(ActivityInviteRecord.serializer(), json), author, uri, now)
            }

            // Private, so from the interactions space only (spaceCollections); the firehose never delivers it (indexedCollections).
            Nsid.FEED_SEGMENT_NAME_VOTE -> {
                if (placement.origin != Origins.REPO) upsertNameVote(json, author, uri, now)
            }

            Nsid.ACTOR_PROFILE -> {
                if (uri.rkey == ProfileRecord.RKEY)
                    writeProfile(author, AtJson.decodeFromJsonElement(ProfileRecord.serializer(), json), now)
            }
        }
    }

    /** After the row for [uri] was deleted: the narrowest copy another origin still holds becomes the row. */
    private fun JdbcTransaction.promoteShadow(collection: String, uri: AtUri) {
        val next = takeShadow(uri) ?: return
        val origin = next[RecordShadows.origin]
        try {
            val record = AtJson.parseToJsonElement(next[RecordShadows.record]).jsonObject
            upsert(collection, Did(uri.authority), uri, next[RecordShadows.cid], record, Placement.of(origin))
        } catch (e: IllegalArgumentException) {
            log.debug("Dropping unpromotable {} copy of {}: {}", origin, uri, e.message)
        }
    }

    /**
     * A delete from [placement]'s origin: only its own copy goes, and a copy another origin still holds takes over the row. An activity's
     * efforts follow it ([activityChanged]).
     */
    private fun JdbcTransaction.delete(collection: String, uri: AtUri, placement: Placement) {
        if (collection in SEGMENT_COLLECTIONS) return deleteSegments(collection, uri)
        val activity = effortActivity(collection, uri)
        val before = activity?.let { matchInputs(it) }
        deleteRecord(collection, uri, placement)
        if (activity != null) activityChanged(activity, before, segmentJobs, clock.now().toEpochMilliseconds())
    }

    @Suppress("LongMethod", "CyclomaticComplexMethod") // A branch per indexed collection.
    private fun JdbcTransaction.deleteRecord(collection: String, uri: AtUri, placement: Placement) {
        val held = HELD_ROWS.firstOrNull { it.collection == collection }
        if (held != null) {
            val record = requireNotNull(held.uri)
            val origin = held.origin
            held.table.deleteWhere { (record eq uri.value) and (if (origin == null) Op.TRUE else origin eq placement.origin) }
            return
        }
        when (collection) {
            Nsid.FEED_ACTIVITY,
            Nsid.FEED_ACTIVITY_ZONES,
            Nsid.GEAR_ITEM -> {
                RecordShadows.deleteWhere { (RecordShadows.uri eq uri.value) and (origin eq placement.origin) }
                if (heldCopy(collection, uri)?.origin != placement.origin) return
                when (collection) {
                    Nsid.FEED_ACTIVITY -> {
                        Activities.deleteWhere { (Activities.uri eq uri.value) and (Activities.origin eq placement.origin) }
                        ActivityMediaBlobs.deleteWhere { activityUri eq uri.value }
                        ActivityMembers.deleteWhere { combinedUri eq uri.value }
                        ActivityGear.deleteWhere { (activityUri eq uri.value) and (listedIn eq ActivityGear.SOURCE_RECORD) }
                    }
                    Nsid.GEAR_ITEM -> deleteHeldGear(uri)
                    else -> ActivityZones.deleteWhere { ActivityZones.uri eq uri.value }
                }
                promoteShadow(collection, uri)
            }

            Nsid.FEED_ACTIVITY_STREAMS -> {
                val dropped = streamBlobs { (ActivityStreams.uri eq uri.value) and (ActivityStreams.origin eq placement.origin) }
                ActivityStreams.deleteWhere { (ActivityStreams.uri eq uri.value) and (origin eq placement.origin) }
                dropOrphanedStreamData(dropped)
            }

            Nsid.FEED_ACTIVITY_DETAILS -> {
                ActivityDetails.deleteWhere { (ActivityDetails.uri eq uri.value) and (origin eq placement.origin) }
                val activity = AtUri.of(Did(uri.authority), Nsid.FEED_ACTIVITY, requireNotNull(uri.rkey))
                ActivityGear.deleteWhere {
                    (activityUri eq activity.value) and (listedIn eq ActivityGear.SOURCE_DETAILS) and (origin eq placement.origin)
                }
            }

            in BLOCKS_AND_MUTES -> deleteBlockOrMute(collection, uri, placement)

            Nsid.FEED_SEGMENT -> {
                if (placement.origin == Origins.REPO) deleteSegment(uri, segmentJobs, clock.now().toEpochMilliseconds(), segmentImporter)
            }

            Nsid.FEED_SEGMENT_NAME_PROPOSAL -> {
                if (placement.origin == Origins.REPO) deleteNameProposals(listOf(uri.value))
            }

            Nsid.FEED_DIVE_SITE -> {
                if (placement.origin == Origins.REPO) deleteDiveSite(uri, segmentJobs, clock.now().toEpochMilliseconds(), segmentImporter)
            }

            Nsid.FEED_SEGMENT_NAME_VOTE -> {
                if (placement.origin != Origins.REPO) deleteNameVote(uri)
            }

            Nsid.FEED_ACTIVITY_INVITE -> {
                if (placement.origin != Origins.REPO) deleteInvite(uri)
            }

            Nsid.ACTOR_PROFILE -> {
                if (uri.rkey == ProfileRecord.RKEY) writeProfile(Did(uri.authority), null, clock.now().toEpochMilliseconds())
            }
        }
    }

    /**
     * Checks a known account's handle both ways again and stores the result (identity/handle-verification): the verified handle or
     * `handle.invalid`, and the next check a day on; when the lookups can't be reached, the stored handle stays and the next check is
     * sooner. An account with no `actors` row is left alone. Called for `#identity` events and by `HandleSweep`.
     */
    suspend fun recheckHandle(did: Did) {
        if (!actorExists(did)) return
        val check = handles.check(did)
        val now = clock.now()
        db.tx {
            Actors.update({ Actors.did eq did.value }) {
                handleOf(check)?.let { handle -> it[Actors.handle] = handle }
                it[handleCheckDueAt] = nextHandleCheck(check, now)
            }
        }
    }

    /** The handle check for [author]'s first record, or null when their `actors` row already exists. */
    private suspend fun firstSightCheck(author: Did): HandleCheck? =
        when {
            author.value in knownActors -> null
            actorExists(author) -> null.also { knownActors += author.value }
            else -> handles.check(author)
        }

    private suspend fun actorExists(did: Did) = db.tx { Actors.select(Actors.did).where { Actors.did eq did.value }.any() }

    /**
     * Inserts [did]'s row unless it exists, with the handle [check] found (`handle.invalid` until a check verifies one). With no check (a
     * row seen before), it only restores a row deleted since, due for a check at once.
     */
    private fun JdbcTransaction.ensureActor(did: Did, check: HandleCheck?, now: Long) {
        Actors.insertIgnore {
            it[Actors.did] = did.value
            it[handle] = check?.let(::handleOf) ?: IdentityResolver.INVALID_HANDLE.value
            it[handleCheckDueAt] = check?.let { found -> nextHandleCheck(found, Instant.fromEpochMilliseconds(now)) }
            it[indexedAt] = now
        }
        knownActors += did.value
    }

    private suspend fun deleteActor(did: Did) = db.tx {
        // First, so a space sync still running finds its space gone ([spaceWrite]), or this waits for its write and then deletes it too.
        SpaceSync.deleteWhere { authorityDid eq did.value }
        knownActors -= did.value
        Activities.deleteWhere { authorDid eq did.value }
        ActivityMediaBlobs.deleteWhere { authorDid eq did.value }
        ActivityMembers.deleteWhere { authorDid eq did.value }
        val streamBlobs = streamBlobs { ActivityStreams.authorDid eq did.value }
        ActivityStreams.deleteWhere { authorDid eq did.value }
        ActivityDetails.deleteWhere { authorDid eq did.value }
        ActivityZones.deleteWhere { authorDid eq did.value }
        GearItems.deleteWhere { authorDid eq did.value }
        GearMountRows.deleteWhere { authorDid eq did.value }
        dropOrphanedStreamData(streamBlobs)
        for (held in HELD_ROWS) held.table.deleteWhere { held.author eq did.value }
        SegmentPrefs.deleteWhere { SegmentPrefs.did eq did.value }
        GroupPrefs.deleteWhere { GroupPrefs.did eq did.value }
        LeaderboardBrackets.deleteWhere { LeaderboardBrackets.did eq did.value }
        SegmentCreations.deleteWhere { authorDid eq did.value }
        DiveSiteCreations.deleteWhere { authorDid eq did.value }
        dropSegmentTracks(did)
        dropInvitesOfAccount(did)
        deleteSegmentNamesBy(did)
        deleteSegmentsBy(did, segmentJobs, clock.now().toEpochMilliseconds(), segmentImporter)
        deleteDiveSitesBy(did, segmentJobs, clock.now().toEpochMilliseconds(), segmentImporter)
        RecordShadows.deleteWhere { uri like "at://${did.value}/%" }
        Actors.deleteWhere { Actors.did eq did.value }
    }

    companion object {
        private const val DELETED = "deleted"
        private const val URI_LOCK_STRIPES = 64

        /** `#account` statuses whose account is kept but not served. */
        private val INACTIVE = setOf("takendown", "suspended", "deactivated")

        private val TIER_COLLECTIONS =
            setOf(Nsid.FEED_ACTIVITY, Nsid.FEED_ACTIVITY_DETAILS, Nsid.FEED_ACTIVITY_STREAMS, Nsid.FEED_ACTIVITY_ZONES, Nsid.GEAR_ITEM)

        /**
         * Tables whose rows go with their author's account ([deleteActor]) and, when they have an [HeldRows.origin], with the space they
         * came from ([dropSpace]). Those naming a [HeldRows.collection] are also deleted by URI (and origin) when their record is
         * ([deleteRecord]). A new table of this shape joins this list, so no delete path misses it.
         */
        private val HELD_ROWS =
            listOf(
                HeldRows(Likes, Likes.authorDid, Likes.origin, Nsid.FEED_LIKE, Likes.uri),
                HeldRows(Comments, Comments.authorDid, Comments.origin, Nsid.FEED_COMMENT, Comments.uri),
                HeldRows(CommentHides, CommentHides.authorDid, CommentHides.origin, Nsid.FEED_COMMENT_HIDE, CommentHides.uri),
                HeldRows(Follows, Follows.authorDid, origin = null, Nsid.GRAPH_FOLLOW, Follows.uri),
                HeldRows(FollowApprovals, FollowApprovals.authorDid, origin = null, Nsid.GRAPH_FOLLOW_APPROVAL, FollowApprovals.uri),
                // Deleted by record through deleteBlockOrMute and deleteRecord's activity branches.
                HeldRows(Blocks, Blocks.authorDid, Blocks.origin),
                HeldRows(Mutes, Mutes.authorDid, Mutes.origin),
                HeldRows(ActivityGear, ActivityGear.authorDid, ActivityGear.origin),
            )

        /** Blocks (the app's and Bluesky's) and mutes, which all go through [upsertBlockOrMute] and [deleteBlockOrMute]. */
        private val BLOCKS_AND_MUTES = setOf(Nsid.GRAPH_BLOCK, Nsid.BSKY_GRAPH_BLOCK, Nsid.GRAPH_MUTE)

        private val INTERACTION_COLLECTIONS =
            setOf(Nsid.FEED_LIKE, Nsid.FEED_COMMENT, Nsid.FEED_COMMENT_HIDE, Nsid.GRAPH_BLOCK, Nsid.GRAPH_MUTE, Nsid.FEED_SEGMENT_NAME_VOTE)

        /** Segment tracks, the segment matching setting, the grouping choice and the leaderboard brackets, indexed only from `self`. */
        private val SEGMENT_COLLECTIONS =
            setOf(Nsid.FEED_SEGMENT_TRACK, Nsid.ACTOR_SEGMENT_PREFS, Nsid.ACTOR_GROUP_PREFS, Nsid.ACTOR_LEADERBOARD_BRACKETS)
        private val SELF_COLLECTIONS = TIER_COLLECTIONS + SEGMENT_COLLECTIONS + Nsid.FEED_ACTIVITY_INVITE

        /**
         * What the AppView indexes from the athlete space with key [skey]: likes, comments, comment hides, blocks, mutes and segment name
         * votes from the interactions space, and activities, their details, streams and zones and gear items from a tier (an unknown key is
         * a tier only the athlete sees), plus from `self` the segment matching setting, the grouping choice and segment tracks
         * (zone-trimmed copies of tracks, used for each purpose only while it's on and never served), the leaderboard brackets (used only
         * to filter leaderboards, never served) and activity invites (served only to the inviter and the invitee). Private details, follow
         * declines, invite declines (`feed.activityInviteDecline`), defaults, full tracks, imported files and their devices
         * (`feed.activityTrack`, `feed.activitySource`, `feed.activityDevices`, in `self`), privacy zones and their trim marker
         * (`actor.privacyZones`, `actor.routeTrim`, in `self`), stars on segments (`feed.segmentStar`, in `self`) and gear collections
         * (`gear.collection`, in `self`) are never stored.
         */
        fun spaceCollections(skey: String): Set<String> =
            when (skey) {
                AthleteSpace.INTERACTIONS -> INTERACTION_COLLECTIONS
                AthleteSpace.SELF -> SELF_COLLECTIONS
                else -> TIER_COLLECTIONS
            }
    }
}

/**
 * A comment hide counts only under the rkey derived from its subject, so unhiding (deleting that rkey) always removes it; one under any
 * other rkey, or of something that isn't a comment, is skipped.
 */
private suspend fun requireHideKey(uri: AtUri, json: JsonObject) {
    val subject = AtJson.decodeFromJsonElement(CommentHideRecord.serializer(), json).subject
    require(subject.collection == Nsid.FEED_COMMENT && subject.rkey != null) { "hide of a non-comment $subject" }
    require(uri.rkey == commentHideRkey(subject)) { "hide of $subject under another rkey" }
}

/**
 * A comment or reply. A reply naming anything but comments ([namesOnlyComments]) isn't indexed, and an earlier version of the record from
 * the same place is dropped, so an edit can't keep a malformed reply listed. A replay keeps the row's first `indexed_at`, which places its
 * thread (`threadPlace` in `api/CommentQueries.kt`).
 */
@Suppress("LongParameterList") // The record, and where it's from.
private fun JdbcTransaction.upsertComment(record: CommentRecord, author: Did, uri: AtUri, cid: String, placement: Placement, now: Long) {
    val reply = record.reply
    if (reply != null && !namesOnlyComments(reply)) {
        Comments.deleteWhere { (Comments.uri eq uri.value) and (origin eq placement.origin) }
        return
    }
    Comments.upsert(onUpdateExclude = listOf(Comments.indexedAt)) {
        it[Comments.uri] = uri.value
        it[Comments.cid] = cid
        it[authorDid] = author.value
        it[subjectUri] = record.subject.uri.value
        it[text] = record.text.take(MAX_COMMENT_LENGTH)
        it[createdAt] = record.createdAt.toEpochMilliseconds()
        it[indexedAt] = now
        it[origin] = placement.origin
        // Absent is public, and an unknown value only the athlete's, so serving never shows it wider than written.
        it[audience] = Visibility.known(record.audience)
        // Grouped by the root the record names, whether or not it's indexed (or on this activity): serving sorts that out.
        it[threadUri] = reply?.root?.uri?.value ?: uri.value
        it[threadCid] = reply?.root?.cid?.value
        it[parentUri] = reply?.parent?.uri?.value
        it[parentCid] = reply?.parent?.cid?.value
    }
}

/** A reply's root and parent must both be comments: a reply "to" a like or an activity is malformed. */
private fun namesOnlyComments(reply: ReplyRef): Boolean =
    listOf(reply.root.uri, reply.parent.uri).all { it.collection == Nsid.FEED_COMMENT && it.rkey != null }

private fun JdbcTransaction.upsertHide(record: CommentHideRecord, author: Did, uri: AtUri, placement: Placement, now: Long) {
    CommentHides.upsert {
        it[CommentHides.uri] = uri.value
        it[origin] = placement.origin
        it[authorDid] = author.value
        it[commentUri] = record.subject.value
        it[createdAt] = record.createdAt.toEpochMilliseconds()
        it[indexedAt] = now
    }
}

/**
 * A `graph.block` or `graph.mute`, only from the interactions space ([Indexer.spaceCollections]): one per pair, at rkey = its subject, so
 * unblocking or unmuting always removes it. One under any other rkey is dropped.
 */
@Suppress("LongParameterList") // The record, and where it's from.
private fun JdbcTransaction.upsertBlockOrMute(
    collection: String,
    json: JsonObject,
    author: Did,
    uri: AtUri,
    placement: Placement,
    now: Long,
) {
    if (collection == Nsid.GRAPH_BLOCK) {
        val record = AtJson.decodeFromJsonElement(BlockRecord.serializer(), json)
        if (uri.rkey == BlockRecord.rkey(record.subject))
            upsertBlock(uri, placement, author, record.subject, record.createdAt.toEpochMilliseconds(), now)
        else deleteBlockOrMute(collection, uri, placement)
    } else {
        val record = AtJson.decodeFromJsonElement(MuteRecord.serializer(), json)
        if (uri.rkey == MuteRecord.rkey(record.subject)) upsertMute(uri, placement, author, record, now)
        else deleteBlockOrMute(collection, uri, placement)
    }
}

/** A block (either kind) or mute deleted from [placement]'s origin. */
private fun JdbcTransaction.deleteBlockOrMute(collection: String, uri: AtUri, placement: Placement) {
    if (collection == Nsid.GRAPH_MUTE) Mutes.deleteWhere { (Mutes.uri eq uri.value) and (origin eq placement.origin) }
    else Blocks.deleteWhere { (Blocks.uri eq uri.value) and (origin eq placement.origin) }
}

@Suppress("LongParameterList") // The block, and where it's from.
internal fun JdbcTransaction.upsertBlock(uri: AtUri, placement: Placement, author: Did, subject: Did, createdAt: Long, now: Long) {
    require(subject != author) { "block of self" }
    // A block ends grouping between the two: an unblock pairs them again only once one of their tracks is matched again.
    dropPairsBetween(author, subject)
    dropInvitePairsBetween(author, subject)
    Blocks.upsert {
        it[Blocks.uri] = uri.value
        it[origin] = placement.origin
        it[authorDid] = author.value
        it[subjectDid] = subject.value
        it[Blocks.createdAt] = createdAt
        it[indexedAt] = now
    }
}

private fun JdbcTransaction.upsertMute(uri: AtUri, placement: Placement, author: Did, record: MuteRecord, now: Long) {
    require(record.subject != author) { "mute of self" }
    Mutes.upsert {
        it[Mutes.uri] = uri.value
        it[origin] = placement.origin
        it[authorDid] = author.value
        it[subjectDid] = record.subject.value
        it[createdAt] = record.createdAt.toEpochMilliseconds()
        it[indexedAt] = now
    }
}

/** One approval per pair: only the one at rkey = its subject counts, so deleting that record really removes the follower. */
private fun JdbcTransaction.upsertApproval(record: FollowApprovalRecord, author: Did, uri: AtUri, now: Long) {
    if (uri.rkey != FollowApprovalRecord.rkey(record.subject)) {
        FollowApprovals.deleteWhere { FollowApprovals.uri eq uri.value }
        return
    }
    FollowApprovals.upsert {
        it[FollowApprovals.uri] = uri.value
        it[authorDid] = author.value
        it[subjectDid] = record.subject.value
        it[createdAt] = record.createdAt.toEpochMilliseconds()
        it[indexedAt] = now
    }
}

/** Writes every profile column of [author] from [record], or clears them all when it's deleted (null), so no column outlives it. */
private fun JdbcTransaction.writeProfile(author: Did, record: ProfileRecord?, now: Long) {
    val avatar = record?.let { ProfileDetailRules.avatarCid(it.avatar) }
    val details = record?.let { ProfileDetailRules.sanitize(it.details) }
    Actors.update({ Actors.did eq author.value }) {
        it[displayName] = record?.displayName?.take(MAX_DISPLAY_NAME_LENGTH)
        it[description] = record?.description?.take(MAX_DESCRIPTION_LENGTH)
        it[units] = record?.units?.take(MAX_UNITS_LENGTH)
        it[avatarCid] = avatar
        it[avatarMime] = record?.avatar?.mimeType?.takeIf { avatar != null }
        it[locality] = details?.location?.locality
        it[region] = details?.location?.region
        it[country] = details?.location?.country
        it[birthDate] = details?.birthDate
        it[gender] = details?.gender
        it[weightGrams] = details?.weight
        it[heightMm] = details?.height
        it[training] = ProfileDetailRules.encodeTraining(details?.training)
        it[followPolicy] = record?.followPolicy?.takeIf { policy -> policy == FollowPolicy.APPROVE }
        it[indexedAt] = now
    }
}

// Records are untrusted, so free text is clamped to the lexicon maxLength (or the column size) before it's stored.
private const val MAX_COMMENT_LENGTH = 10_000
private const val MAX_DISPLAY_NAME_LENGTH = 640
private const val MAX_DESCRIPTION_LENGTH = 2560
private const val MAX_UNITS_LENGTH = 16

/**
 * A table of [Indexer.HELD_ROWS]: its [author] column, its [origin] column when its rows are held by origin, and the [collection] and [uri]
 * column of the records it holds one row each of, when deleting a record is just deleting its row.
 */
private class HeldRows(
    val table: Table,
    val author: Column<String>,
    val origin: Column<String>?,
    val collection: String? = null,
    val uri: Column<String>? = null,
)
