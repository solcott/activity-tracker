package io.github.solcott.activitytracker.appview.db

import io.github.solcott.activitytracker.lexicon.app.Visibility
import org.jetbrains.exposed.v1.core.Table

object Actors : Table("actors") {
    val did = varchar("did", 255)
    val handle = varchar("handle", 255)
    val displayName = text("display_name").nullable()
    val description = text("description").nullable()
    val units = varchar("units", 16).nullable()
    val avatarCid = varchar("avatar_cid", 255).nullable()
    val avatarMime = varchar("avatar_mime", 64).nullable()
    val locality = text("locality").nullable()
    val region = text("region").nullable()
    val country = varchar("country", 2).nullable()
    val birthDate = varchar("birth_date", 10).nullable()
    val gender = text("gender").nullable()
    val weightGrams = long("weight_g").nullable()
    val heightMm = long("height_mm").nullable()
    /** `actor.defs#training` as JSON. */
    val training = text("training").nullable()
    /** `FollowPolicy.APPROVE`, or null for an open policy (the profile record had none, or a value we don't know). */
    val followPolicy = varchar("follow_policy", 16).nullable()
    val indexedAt = long("indexed_at")
    override val primaryKey = PrimaryKey(did)
}

object Activities : Table("activities") {
    val uri = varchar("uri", 512)
    val cid = varchar("cid", 255)
    val authorDid = varchar("author_did", 255)
    val record = text("record")
    val sport = varchar("sport", 64)
    val subSport = varchar("sub_sport", 64).nullable()
    val startedAt = long("started_at")
    val sortAt = long("sort_at")
    val indexedAt = long("indexed_at")
    /** The athlete's own activity this one is another recording of; null for an activity in its own right. */
    val recordingOf = varchar("recording_of", 512).nullable()
    /** The [io.github.solcott.activitytracker.lexicon.app.Visibility] it's served at: its tier space's key, or public for the repo. */
    val visibility = varchar("visibility", 16).default(Visibility.PUBLIC)
    /** Where the row came from: [Origins.REPO] or the tier space's URI. Only that origin's delete removes it. */
    val origin = varchar("origin", 512).default(Origins.REPO)
    override val primaryKey = PrimaryKey(uri)
}

/**
 * A combined activity's claim on a member ([io.github.solcott.activitytracker.lexicon.app.ActivityRecord.combined]), one row per listed
 * member of the author's own. Whether it counts is checked at read time (`Views.validMembers`).
 */
object ActivityMembers : Table("activity_members") {
    val memberUri = varchar("member_uri", 512)
    val combinedUri = varchar("combined_uri", 512)
    val authorDid = varchar("author_did", 255)
    override val primaryKey = PrimaryKey(memberUri, combinedUri)
}

/** An athlete's segment matching setting (`actor.segmentPrefs`), from their `self` space. Segment tracks are used only while [matching]. */
object SegmentPrefs : Table("segment_prefs") {
    val did = varchar("did", 255)
    val matching = bool("matching")
    val updatedAt = long("updated_at")
    override val primaryKey = PrimaryKey(did)
}

/**
 * An athlete's segment track (`feed.segmentTrack`): an activity's track trimmed by their privacy zones, from their `self` space. Its points
 * stay in the blob at [dataCid], read only while their [SegmentPrefs.matching] is on ([usableSegmentTracks]); never served.
 */
object SegmentTracks : Table("segment_tracks") {
    val uri = varchar("uri", 512)
    val authorDid = varchar("author_did", 255)
    val activityUri = varchar("activity_uri", 512)
    val dataCid = varchar("data_cid", 255)
    val zonesRevision = varchar("zones_revision", 32).nullable()
    val indexedAt = long("indexed_at")
    override val primaryKey = PrimaryKey(uri)
}

/** An activity's photos ([io.github.solcott.activitytracker.lexicon.app.ActivityRecord.media]), in order. */
object ActivityMediaBlobs : Table("activity_media") {
    val activityUri = varchar("activity_uri", 512)
    val sortIndex = integer("sort_index")
    val authorDid = varchar("author_did", 255)
    val cid = varchar("cid", 255)
    val mimeType = varchar("mime_type", 64)
    override val primaryKey = PrimaryKey(activityUri, sortIndex)
}

object Likes : Table("likes") {
    val uri = varchar("uri", 512)
    val authorDid = varchar("author_did", 255)
    val subjectUri = varchar("subject_uri", 512)
    val subjectCid = varchar("subject_cid", 255)
    val createdAt = long("created_at")
    val indexedAt = long("indexed_at")

    /** `repo`, or the URI of the `interactions` space the like came from. */
    val origin = varchar("origin", 512).default(Origins.REPO)
    override val primaryKey = PrimaryKey(uri)
}

object Comments : Table("comments") {
    val uri = varchar("uri", 512)
    val cid = varchar("cid", 255)
    val authorDid = varchar("author_did", 255)
    val subjectUri = varchar("subject_uri", 512)
    val text = text("text")
    val createdAt = long("created_at")
    val indexedAt = long("indexed_at")

    /** `repo`, or the URI of the `interactions` space the comment came from. */
    val origin = varchar("origin", 512).default(Origins.REPO)

    /** The [Visibility] the comment was written for, normalised when indexed: never shown wider than this. */
    val audience = varchar("audience", 16).default(Visibility.PUBLIC)

    /** The top-level comment of the thread (`reply.root`), or [uri] itself for a top-level comment. */
    val threadUri = varchar("thread_uri", 512)

    /** The rest of `reply.root`'s strong ref; null on a top-level comment. */
    val threadCid = varchar("thread_cid", 255).nullable()

    /** The comment a reply answers (`reply.parent`); null on a top-level comment. */
    val parentUri = varchar("parent_uri", 512).nullable()
    val parentCid = varchar("parent_cid", 255).nullable()
    override val primaryKey = PrimaryKey(uri)
}

/**
 * An athlete's hide of [commentUri] (`feed.commentHide`). It counts only when [authorDid] is the athlete whose activity the comment is on,
 * which serving checks, so a stranger's hide is an inert row.
 */
object CommentHides : Table("comment_hides") {
    val uri = varchar("uri", 512)

    /** `repo`, or the URI of the `interactions` space the hide came from. */
    val origin = varchar("origin", 512)
    val authorDid = varchar("author_did", 255)
    val commentUri = varchar("comment_uri", 512)
    val createdAt = long("created_at")
    val indexedAt = long("indexed_at")
    override val primaryKey = PrimaryKey(uri, origin)
}

object Follows : Table("follows") {
    val uri = varchar("uri", 512)
    val authorDid = varchar("author_did", 255)
    val subjectDid = varchar("subject_did", 255)
    val createdAt = long("created_at")
    val indexedAt = long("indexed_at")
    override val primaryKey = PrimaryKey(uri)
}

/**
 * `graph.followApproval` records: [authorDid] approved [subjectDid]'s follow. Only approvals stored at rkey = their subject are indexed, so
 * there's at most one per pair and deleting it removes the follower.
 */
object FollowApprovals : Table("follow_approvals") {
    val uri = varchar("uri", 512)
    val authorDid = varchar("author_did", 255)
    val subjectDid = varchar("subject_did", 255)
    val createdAt = long("created_at")
    val indexedAt = long("indexed_at")
    override val primaryKey = PrimaryKey(uri)
}

object JetstreamCursor : Table("jetstream_cursor") {
    val id = integer("id")
    val timeUs = long("time_us")
    override val primaryKey = PrimaryKey(id)
}

/** `feed.activityStreams` records, which share their activity's rkey. Who may see one is enforced in `ActivityStreamsViews`. */
object ActivityStreams : Table("activity_streams") {
    val uri = varchar("uri", 512)
    val cid = varchar("cid", 255)
    val authorDid = varchar("author_did", 255)
    val activityUri = varchar("activity_uri", 512)
    val visibility = varchar("visibility", 16)
    /** JSON array of channel tokens. */
    val channels = text("channels")
    val blobCid = varchar("blob_cid", 255)
    val indexedAt = long("indexed_at")
    /** As [Activities.origin]. */
    val origin = varchar("origin", 512).default(Origins.REPO)
    /** The raw record JSON; null on rows indexed before V15. */
    val record = text("record").nullable()
    /** An activity has one streams record per tier holding its channels, all under one URI, so each origin's copy is a row. */
    override val primaryKey = PrimaryKey(uri, origin)
}

/**
 * `feed.activityDetails` records: an activity's summary fields in a detail category narrower than it, one per tier, all under the
 * activity's rkey, so each origin's copy is a row. Indexed only from tier spaces, and served at [visibility], where the record is stored
 * (`Views.hydrate`).
 */
object ActivityDetails : Table("activity_details") {
    val uri = varchar("uri", 512)
    /** As [Activities.origin]; never [Origins.REPO]. */
    val origin = varchar("origin", 512)
    val cid = varchar("cid", 255)
    val authorDid = varchar("author_did", 255)
    val activityUri = varchar("activity_uri", 512)
    val visibility = varchar("visibility", 16)
    /** The record as indexed: the shared `AtJson` model, re-encoded with only the fields it knows. */
    val record = text("record")
    val indexedAt = long("indexed_at")
    override val primaryKey = PrimaryKey(uri, origin)
}

/**
 * `feed.activityZones` records, which share their activity's rkey. Served at [visibility], where the record is stored, and only alongside
 * streams the viewer may see (`ActivityStreamsViews`).
 */
object ActivityZones : Table("activity_zones") {
    val uri = varchar("uri", 512)
    val cid = varchar("cid", 255)
    val authorDid = varchar("author_did", 255)
    val activityUri = varchar("activity_uri", 512)
    val visibility = varchar("visibility", 16)
    /** As [Activities.origin]. */
    val origin = varchar("origin", 512)
    /** `activityZones#zoneSnapshot` as JSON. */
    val heartRateZones = text("heart_rate_zones").nullable()
    val powerZones = text("power_zones").nullable()
    /** The raw record JSON, so the row can become a [RecordShadows] copy mid-move. */
    val record = text("record")
    val indexedAt = long("indexed_at")
    override val primaryKey = PrimaryKey(uri)
}

/** Decoded `activityStreams#streamData`, cached by the blob's CID. */
object ActivityStreamData : Table("activity_stream_data") {
    val blobCid = varchar("blob_cid", 255)
    val data = text("stream_data")
    val fetchedAt = long("fetched_at")
    override val primaryKey = PrimaryKey(blobCid)
}

/** Values of [Activities.origin], [ActivityStreams.origin] and [ActivityZones.origin] besides a space URI. */
object Origins {
    /** The athlete's repo, through Jetstream. */
    const val REPO = "repo"
}

/**
 * The wider copy of a record that two origins hold at once mid-move. The narrower copy is the row in [Activities], [ActivityStreams] or
 * [ActivityZones]; this one is promoted to it if that copy is deleted first.
 */
object RecordShadows : Table("record_shadows") {
    val uri = varchar("uri", 512)
    val origin = varchar("origin", 512)
    val collection = varchar("collection", 255)
    val visibility = varchar("visibility", 16)
    val cid = varchar("cid", 255)
    val record = text("record")
    val indexedAt = long("indexed_at")
    override val primaryKey = PrimaryKey(uri, origin)
}

/** An athlete tier space the AppView syncs as a member. */
object SpaceSync : Table("space_sync") {
    val space = varchar("space_uri", 512)
    val authorityDid = varchar("authority_did", 255)
    /** The authority repo's last indexed revision; null until the first backfill finishes. */
    val repoRev = varchar("repo_rev", 32).nullable()
    /** When the `notifyWrite` registration expires, epoch millis; null when not registered. */
    val registeredUntil = long("registered_until").nullable()
    /** Consecutive failed syncs, for backoff. */
    val failures = integer("failures").default(0)
    /** When the last sync succeeded, epoch millis; null before the first one. */
    val syncedAt = long("synced_at").nullable()
    val nextAttemptAt = long("next_attempt_at")
    val createdAt = long("created_at")
    override val primaryKey = PrimaryKey(space)
}
