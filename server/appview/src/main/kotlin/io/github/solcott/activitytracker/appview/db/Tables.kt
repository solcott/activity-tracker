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
    override val primaryKey = PrimaryKey(uri)
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
    /** `activityStreams#zoneSnapshot` as JSON. */
    val heartRateZones = text("heart_rate_zones").nullable()
    val powerZones = text("power_zones").nullable()
    val blobCid = varchar("blob_cid", 255)
    val indexedAt = long("indexed_at")
    /** As [Activities.origin]. */
    val origin = varchar("origin", 512).default(Origins.REPO)
    /** The raw record JSON, so the row can become a [RecordShadows] copy mid-move; null on rows indexed before V15. */
    val record = text("record").nullable()
    override val primaryKey = PrimaryKey(uri)
}

/** Decoded `activityStreams#streamData`, cached by the blob's CID. */
object ActivityStreamData : Table("activity_stream_data") {
    val blobCid = varchar("blob_cid", 255)
    val data = text("stream_data")
    val fetchedAt = long("fetched_at")
    override val primaryKey = PrimaryKey(blobCid)
}

/** Values of [Activities.origin] and [ActivityStreams.origin] besides a space URI. */
object Origins {
    /** The athlete's repo, through Jetstream. */
    const val REPO = "repo"
}

/**
 * The wider copy of a record that two origins hold at once mid-move. The narrower copy is the row in [Activities] or [ActivityStreams];
 * this one is promoted to it if that copy is deleted first.
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
