package io.github.solcott.activitytracker.appview.index

import io.github.solcott.activitytracker.appview.db.Activities
import io.github.solcott.activitytracker.appview.db.Actors
import io.github.solcott.activitytracker.appview.db.Comments
import io.github.solcott.activitytracker.appview.db.Follows
import io.github.solcott.activitytracker.appview.db.Kudos
import io.github.solcott.activitytracker.appview.db.tx
import io.github.solcott.activitytracker.lexicon.AtJson
import io.github.solcott.activitytracker.lexicon.AtUri
import io.github.solcott.activitytracker.lexicon.Did
import io.github.solcott.activitytracker.lexicon.Nsid
import io.github.solcott.activitytracker.lexicon.app.ActivityRecord
import io.github.solcott.activitytracker.lexicon.app.CommentRecord
import io.github.solcott.activitytracker.lexicon.app.FollowRecord
import io.github.solcott.activitytracker.lexicon.app.KudosRecord
import io.github.solcott.activitytracker.lexicon.app.ProfileRecord
import kotlin.time.Clock
import kotlinx.serialization.json.JsonObject
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.JdbcTransaction
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insertIgnore
import org.jetbrains.exposed.v1.jdbc.update
import org.jetbrains.exposed.v1.jdbc.upsert
import org.slf4j.LoggerFactory

/**
 * Applies repo commits to the index. Every write is an upsert/delete keyed by AT-URI, so replaying events (e.g. after rewinding the
 * Jetstream cursor) is safe. Records that fail to decode are dropped: repos are untrusted input.
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
        if (commit.operation == "delete") {
            db.tx { delete(commit.collection, uri) }
            return
        }
        val record = commit.record ?: return
        val cid = commit.cid ?: return
        val handle = handles.handleFor(author)
        try {
            db.tx {
                ensureActor(author, handle)
                upsert(commit.collection, author, uri, cid, record)
            }
        } catch (e: IllegalArgumentException) {
            log.debug("Skipping invalid {} record {}: {}", commit.collection, uri, e.message)
        } catch (e: kotlinx.serialization.SerializationException) {
            log.debug("Skipping undecodable {} record {}: {}", commit.collection, uri, e.message)
        }
    }

    private fun JdbcTransaction.upsert(
        collection: String,
        author: Did,
        uri: AtUri,
        cid: String,
        json: JsonObject,
    ) {
        val now = clock.now().toEpochMilliseconds()
        when (collection) {
            Nsid.FEED_ACTIVITY -> {
                val record = AtJson.decodeFromJsonElement(ActivityRecord.serializer(), json)
                Activities.upsert {
                    it[Activities.uri] = uri.value
                    it[Activities.cid] = cid
                    it[authorDid] = author.value
                    it[Activities.record] = AtJson.encodeToString(ActivityRecord.serializer(), record)
                    it[sport] = record.sport.take(64)
                    it[startedAt] = record.startedAt.toEpochMilliseconds()
                    it[sortAt] = minOf(record.createdAt.toEpochMilliseconds(), now)
                    it[indexedAt] = now
                }
            }

            Nsid.FEED_KUDOS -> {
                val record = AtJson.decodeFromJsonElement(KudosRecord.serializer(), json)
                Kudos.upsert {
                    it[Kudos.uri] = uri.value
                    it[authorDid] = author.value
                    it[subjectUri] = record.subject.uri.value
                    it[subjectCid] = record.subject.cid.value
                    it[createdAt] = record.createdAt.toEpochMilliseconds()
                    it[indexedAt] = now
                }
            }

            Nsid.FEED_COMMENT -> {
                val record = AtJson.decodeFromJsonElement(CommentRecord.serializer(), json)
                Comments.upsert {
                    it[Comments.uri] = uri.value
                    it[Comments.cid] = cid
                    it[authorDid] = author.value
                    it[subjectUri] = record.subject.uri.value
                    it[text] = record.text.take(10_000)
                    it[createdAt] = record.createdAt.toEpochMilliseconds()
                    it[indexedAt] = now
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

            Nsid.ACTOR_PROFILE -> {
                if (uri.rkey != ProfileRecord.RKEY) return
                val record = AtJson.decodeFromJsonElement(ProfileRecord.serializer(), json)
                Actors.update({ Actors.did eq author.value }) {
                    it[displayName] = record.displayName?.take(640)
                    it[description] = record.description?.take(2560)
                    it[units] = record.units?.take(16)
                    it[indexedAt] = now
                }
            }
        }
    }

    private fun JdbcTransaction.delete(collection: String, uri: AtUri) {
        when (collection) {
            Nsid.FEED_ACTIVITY -> {
                Activities.deleteWhere { Activities.uri eq uri.value }
            }

            Nsid.FEED_KUDOS -> {
                Kudos.deleteWhere { Kudos.uri eq uri.value }
            }

            Nsid.FEED_COMMENT -> {
                Comments.deleteWhere { Comments.uri eq uri.value }
            }

            Nsid.GRAPH_FOLLOW -> {
                Follows.deleteWhere { Follows.uri eq uri.value }
            }

            Nsid.ACTOR_PROFILE -> {
                Actors.update({ Actors.did eq uri.authority }) {
                    it[displayName] = null
                    it[description] = null
                    it[units] = null
                }
            }
        }
    }

    private fun JdbcTransaction.ensureActor(did: Did, handle: String) {
        Actors.insertIgnore {
            it[Actors.did] = did.value
            it[Actors.handle] = handle
            it[indexedAt] = clock.now().toEpochMilliseconds()
        }
    }

    private suspend fun updateHandle(did: Did, handle: String) {
        handles.invalidate(did)
        db.tx { Actors.update({ Actors.did eq did.value }) { it[Actors.handle] = handle } }
    }

    private suspend fun deleteActor(did: Did) = db.tx {
        Activities.deleteWhere { authorDid eq did.value }
        Kudos.deleteWhere { authorDid eq did.value }
        Comments.deleteWhere { authorDid eq did.value }
        Follows.deleteWhere { authorDid eq did.value }
        Actors.deleteWhere { Actors.did eq did.value }
    }
}
