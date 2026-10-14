package io.github.solcott.activitytracker.appview.db

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

object Kudos : Table("kudos") {
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
    override val primaryKey = PrimaryKey(uri)
}

/** Decoded `activityStreams#streamData`, cached by the blob's CID. */
object ActivityStreamData : Table("activity_stream_data") {
    val blobCid = varchar("blob_cid", 255)
    val data = text("stream_data")
    val fetchedAt = long("fetched_at")
    override val primaryKey = PrimaryKey(blobCid)
}
