package io.github.solcott.activitytracker.appview.index

import io.github.solcott.activitytracker.appview.ProfileDetailRules
import io.github.solcott.activitytracker.appview.db.Activities
import io.github.solcott.activitytracker.appview.db.ActivityMediaBlobs
import io.github.solcott.activitytracker.appview.db.ActivityStreams
import io.github.solcott.activitytracker.appview.db.Actors
import io.github.solcott.activitytracker.appview.db.Comments
import io.github.solcott.activitytracker.appview.db.FollowApprovals
import io.github.solcott.activitytracker.appview.db.Follows
import io.github.solcott.activitytracker.appview.db.Likes
import io.github.solcott.activitytracker.appview.db.RecordShadows
import io.github.solcott.activitytracker.appview.db.SpaceSync
import io.github.solcott.activitytracker.appview.db.tx
import io.github.solcott.activitytracker.lexicon.AtJson
import io.github.solcott.activitytracker.lexicon.AtUri
import io.github.solcott.activitytracker.lexicon.Did
import io.github.solcott.activitytracker.lexicon.Nsid
import io.github.solcott.activitytracker.lexicon.app.AthleteSpace
import io.github.solcott.activitytracker.lexicon.app.CommentRecord
import io.github.solcott.activitytracker.lexicon.app.FollowApprovalRecord
import io.github.solcott.activitytracker.lexicon.app.FollowPolicy
import io.github.solcott.activitytracker.lexicon.app.FollowRecord
import io.github.solcott.activitytracker.lexicon.app.LikeRecord
import io.github.solcott.activitytracker.lexicon.app.ProfileRecord
import io.github.solcott.activitytracker.lexicon.app.Visibility
import io.github.solcott.activitytracker.lexicon.atproto.SpaceUri
import kotlin.time.Clock
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.like
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.JdbcTransaction
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insertIgnore
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.update
import org.jetbrains.exposed.v1.jdbc.upsert
import org.slf4j.LoggerFactory

/**
 * Applies repo commits, and writes synced from athletes' tier and interactions spaces, to the index. Every write is an upsert/delete keyed
 * by AT-URI, so replaying events (e.g. after rewinding the Jetstream cursor) is safe. Records that fail to decode are dropped: repos are
 * untrusted input.
 *
 * Activities and their streams come from the repo (public) or a tier space, each an origin ([Placement]). Mid-move one URI can be held by
 * two origins: the narrower tier's copy is the indexed row, the other waits in [RecordShadows], and a delete removes only its own origin's
 * copy, promoting a shadow left behind. Likes come from the repo (on public activities) or the giver's interactions space, and are never
 * moved, so they need no shadows: a like's row just records its origin, and only that origin's delete removes it.
 */
class Indexer(
    private val db: Database,
    private val handles: HandleLookup,
    private val clock: Clock = Clock.System,
) {
    private val log = LoggerFactory.getLogger(Indexer::class.java)

    suspend fun handle(event: JetstreamEvent) {
        when (event) {
            is JetstreamEvent.Commit -> {
                commit(Did(event.did), event)
            }

            is JetstreamEvent.Identity -> {
                event.identity.handle?.let { updateHandle(Did(event.did), it) }
            }

            is JetstreamEvent.Account -> {
                if (!event.account.active && event.account.status == "deleted") {
                    deleteActor(Did(event.did))
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
            commit.operation == "delete" -> db.tx { delete(commit.collection, uri, Placement.REPO) }
            record != null && cid != null -> index(author, commit.collection, uri, cid, record, Placement.REPO)
        }
    }

    /** A put of [collection]/[rkey] in [space]'s authority repo. Only [spaceCollections] for the space's key are indexed. */
    suspend fun spaceWrite(space: SpaceUri, collection: String, rkey: String, cid: String, record: JsonObject) {
        if (collection !in spaceCollections(space.skey)) return
        index(space.authority, collection, AtUri.of(space.authority, collection, rkey), cid, record, Placement.of(space))
    }

    /** A delete of [collection]/[rkey] in [space]'s authority repo. */
    suspend fun spaceDelete(space: SpaceUri, collection: String, rkey: String) {
        if (collection !in spaceCollections(space.skey)) return
        db.tx { delete(collection, AtUri.of(space.authority, collection, rkey), Placement.of(space)) }
    }

    /** Everything indexed from [space], after it was deleted: as if each record in it were deleted. */
    suspend fun dropSpace(space: SpaceUri) = db.tx {
        val placement = Placement.of(space)
        for (table in listOf(Activities, ActivityStreams)) {
            val (uriColumn, originColumn, collection) =
                if (table == Activities) Triple(Activities.uri, Activities.origin, Nsid.FEED_ACTIVITY)
                else Triple(ActivityStreams.uri, ActivityStreams.origin, Nsid.FEED_ACTIVITY_STREAMS)
            table
                .select(uriColumn)
                .where { originColumn eq placement.origin }
                .map { it[uriColumn] }
                .forEach { delete(collection, AtUri(it), placement) }
        }
        RecordShadows.deleteWhere { origin eq placement.origin }
        Likes.deleteWhere { origin eq placement.origin }
        Comments.deleteWhere { origin eq placement.origin }
    }

    @Suppress("LongParameterList") // The record, and where it's from.
    private suspend fun index(author: Did, collection: String, uri: AtUri, cid: String, record: JsonObject, placement: Placement) {
        val handle = handles.handleFor(author)
        try {
            db.tx {
                ensureActor(author, handle, clock.now().toEpochMilliseconds())
                upsert(collection, author, uri, cid, record, placement)
            }
        } catch (e: SerializationException) {
            // Before IllegalArgumentException, which SerializationException extends.
            log.debug("Skipping undecodable {} record {}: {}", collection, uri, e.message)
        } catch (e: IllegalArgumentException) {
            log.debug("Skipping invalid {} record {}: {}", collection, uri, e.message)
        }
    }

    @Suppress("LongParameterList") // The record, and where it's from.
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
            Nsid.FEED_ACTIVITY_STREAMS -> {
                upsertPlaced(collection, author, uri, cid, json, placement, now)
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
                val record = AtJson.decodeFromJsonElement(CommentRecord.serializer(), json)
                Comments.upsert {
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
                }
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
            }

            Nsid.GRAPH_FOLLOW_APPROVAL -> {
                upsertApproval(AtJson.decodeFromJsonElement(FollowApprovalRecord.serializer(), json), author, uri, now)
            }

            Nsid.ACTOR_PROFILE -> {
                if (uri.rkey == ProfileRecord.RKEY)
                    updateProfile(AtJson.decodeFromJsonElement(ProfileRecord.serializer(), json), author, now)
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

    /** A delete from [placement]'s origin: only its own copy goes, and a copy another origin still holds takes over the row. */
    private fun JdbcTransaction.delete(collection: String, uri: AtUri, placement: Placement) {
        when (collection) {
            Nsid.FEED_ACTIVITY,
            Nsid.FEED_ACTIVITY_STREAMS -> {
                RecordShadows.deleteWhere { (RecordShadows.uri eq uri.value) and (origin eq placement.origin) }
                if (heldCopy(collection, uri)?.origin != placement.origin) return
                if (collection == Nsid.FEED_ACTIVITY) {
                    Activities.deleteWhere { Activities.uri eq uri.value }
                    ActivityMediaBlobs.deleteWhere { activityUri eq uri.value }
                } else {
                    ActivityStreams.deleteWhere { ActivityStreams.uri eq uri.value }
                }
                promoteShadow(collection, uri)
                dropOrphanedStreamData()
            }

            Nsid.FEED_LIKE -> {
                Likes.deleteWhere { (Likes.uri eq uri.value) and (origin eq placement.origin) }
            }

            Nsid.FEED_COMMENT -> {
                Comments.deleteWhere { (Comments.uri eq uri.value) and (origin eq placement.origin) }
            }

            Nsid.GRAPH_FOLLOW -> {
                Follows.deleteWhere { Follows.uri eq uri.value }
            }

            Nsid.GRAPH_FOLLOW_APPROVAL -> {
                FollowApprovals.deleteWhere { FollowApprovals.uri eq uri.value }
            }

            Nsid.ACTOR_PROFILE -> {
                Actors.update({ Actors.did eq uri.authority }) {
                    it[displayName] = null
                    it[description] = null
                    it[units] = null
                    it[avatarCid] = null
                    it[avatarMime] = null
                    it[locality] = null
                    it[region] = null
                    it[country] = null
                    it[birthDate] = null
                    it[gender] = null
                    it[weightGrams] = null
                    it[heightMm] = null
                    it[training] = null
                    it[followPolicy] = null
                }
            }
        }
    }

    private suspend fun updateHandle(did: Did, handle: String) {
        handles.invalidate(did)
        db.tx { Actors.update({ Actors.did eq did.value }) { it[Actors.handle] = handle } }
    }

    private suspend fun deleteActor(did: Did) = db.tx {
        Activities.deleteWhere { authorDid eq did.value }
        ActivityMediaBlobs.deleteWhere { authorDid eq did.value }
        ActivityStreams.deleteWhere { authorDid eq did.value }
        dropOrphanedStreamData()
        Likes.deleteWhere { authorDid eq did.value }
        Comments.deleteWhere { authorDid eq did.value }
        Follows.deleteWhere { authorDid eq did.value }
        FollowApprovals.deleteWhere { authorDid eq did.value }
        RecordShadows.deleteWhere { uri like "at://${did.value}/%" }
        SpaceSync.deleteWhere { authorityDid eq did.value }
        Actors.deleteWhere { Actors.did eq did.value }
    }

    companion object {
        private val TIER_COLLECTIONS = setOf(Nsid.FEED_ACTIVITY, Nsid.FEED_ACTIVITY_STREAMS)
        private val INTERACTION_COLLECTIONS = setOf(Nsid.FEED_LIKE, Nsid.FEED_COMMENT)

        /**
         * What the AppView indexes from the athlete space with key [skey]: likes and comments from the interactions space, and activities
         * and their streams from a tier (an unknown key is a tier only the athlete sees). Private details, follow declines and defaults are
         * never stored.
         */
        fun spaceCollections(skey: String): Set<String> =
            if (skey == AthleteSpace.INTERACTIONS) INTERACTION_COLLECTIONS else TIER_COLLECTIONS
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

private fun JdbcTransaction.updateProfile(record: ProfileRecord, author: Did, now: Long) {
    val avatar = ProfileDetailRules.avatarCid(record.avatar)
    val details = ProfileDetailRules.sanitize(record.details)
    Actors.update({ Actors.did eq author.value }) {
        it[displayName] = record.displayName?.take(MAX_DISPLAY_NAME_LENGTH)
        it[description] = record.description?.take(MAX_DESCRIPTION_LENGTH)
        it[units] = record.units?.take(MAX_UNITS_LENGTH)
        it[avatarCid] = avatar
        it[avatarMime] = record.avatar?.mimeType?.takeIf { avatar != null }
        it[locality] = details?.location?.locality
        it[region] = details?.location?.region
        it[country] = details?.location?.country
        it[birthDate] = details?.birthDate
        it[gender] = details?.gender
        it[weightGrams] = details?.weight
        it[heightMm] = details?.height
        it[training] = ProfileDetailRules.encodeTraining(details?.training)
        it[followPolicy] = record.followPolicy?.takeIf { policy -> policy == FollowPolicy.APPROVE }
        it[indexedAt] = now
    }
}

private fun JdbcTransaction.ensureActor(did: Did, handle: String, now: Long) {
    Actors.insertIgnore {
        it[Actors.did] = did.value
        it[Actors.handle] = handle
        it[indexedAt] = now
    }
}

// Records are untrusted, so free text is clamped to the lexicon maxLength (or the column size) before it's stored.
private const val MAX_COMMENT_LENGTH = 10_000
private const val MAX_DISPLAY_NAME_LENGTH = 640
private const val MAX_DESCRIPTION_LENGTH = 2560
private const val MAX_UNITS_LENGTH = 16
