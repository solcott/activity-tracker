package io.github.solcott.activitytracker.appview.db

import org.jetbrains.exposed.v1.core.Table

object Actors : Table("actors") {
    val did = varchar("did", 255)
    val handle = varchar("handle", 255)
    val displayName = text("display_name").nullable()
    val description = text("description").nullable()
    val units = varchar("units", 16).nullable()
    val indexedAt = long("indexed_at")
    override val primaryKey = PrimaryKey(did)
}

object Activities : Table("activities") {
    val uri = varchar("uri", 512)
    val cid = varchar("cid", 255)
    val authorDid = varchar("author_did", 255)
    val record = text("record")
    val sport = varchar("sport", 64)
    val startedAt = long("started_at")
    val sortAt = long("sort_at")
    val indexedAt = long("indexed_at")
    override val primaryKey = PrimaryKey(uri)
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

object JetstreamCursor : Table("jetstream_cursor") {
    val id = integer("id")
    val timeUs = long("time_us")
    override val primaryKey = PrimaryKey(id)
}
