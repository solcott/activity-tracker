package io.github.solcott.activitytracker.appview.index

import io.github.solcott.activitytracker.appview.ProfileDetailRules
import io.github.solcott.activitytracker.appview.db.Activities
import io.github.solcott.activitytracker.appview.db.ActivityMediaBlobs
import io.github.solcott.activitytracker.appview.db.ActivityStreams
import io.github.solcott.activitytracker.appview.db.ActorPrivateDetails
import io.github.solcott.activitytracker.appview.db.Actors
import io.github.solcott.activitytracker.appview.db.Comments
import io.github.solcott.activitytracker.appview.db.Follows
import io.github.solcott.activitytracker.appview.db.Kudos
import io.github.solcott.activitytracker.appview.db.tx
import io.github.solcott.activitytracker.lexicon.AtJson
import io.github.solcott.activitytracker.lexicon.AtUri
import io.github.solcott.activitytracker.lexicon.Did
import io.github.solcott.activitytracker.lexicon.Nsid
import io.github.solcott.activitytracker.lexicon.app.ActivityDive
import io.github.solcott.activitytracker.lexicon.app.ActivityDiveGas
import io.github.solcott.activitytracker.lexicon.app.ActivityMedia
import io.github.solcott.activitytracker.lexicon.app.ActivityRecord
import io.github.solcott.activitytracker.lexicon.app.ActivitySensor
import io.github.solcott.activitytracker.lexicon.app.ActivityStreamsRecord
import io.github.solcott.activitytracker.lexicon.app.CommentRecord
import io.github.solcott.activitytracker.lexicon.app.FollowRecord
import io.github.solcott.activitytracker.lexicon.app.KudosRecord
import io.github.solcott.activitytracker.lexicon.app.ProfileRecord
import kotlin.time.Clock
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.JsonObject
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.JdbcTransaction
import org.jetbrains.exposed.v1.jdbc.batchInsert
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
        val record = commit.record
        val cid = commit.cid
        when {
            commit.operation == "delete" -> db.tx { delete(commit.collection, uri) }
            record != null && cid != null -> index(author, commit.collection, uri, cid, record)
        }
    }

    private suspend fun index(author: Did, collection: String, uri: AtUri, cid: String, record: JsonObject) {
        val handle = handles.handleFor(author)
        try {
            db.tx {
                ensureActor(author, handle)
                upsert(collection, author, uri, cid, record)
            }
        } catch (e: SerializationException) {
            // Before IllegalArgumentException, which SerializationException extends.
            log.debug("Skipping undecodable {} record {}: {}", collection, uri, e.message)
        } catch (e: IllegalArgumentException) {
            log.debug("Skipping invalid {} record {}: {}", collection, uri, e.message)
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
                upsertActivity(AtJson.decodeFromJsonElement(ActivityRecord.serializer(), json), author, uri, cid, now)
            }

            Nsid.FEED_KUDOS -> {
                val record = AtJson.decodeFromJsonElement(KudosRecord.serializer(), json)
                require(record.subject.uri.authority != author.value) { "kudos on own activity" }
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
                    it[text] = record.text.take(MAX_COMMENT_LENGTH)
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

            Nsid.FEED_ACTIVITY_STREAMS -> {
                upsertStreams(AtJson.decodeFromJsonElement(ActivityStreamsRecord.serializer(), json), author, uri, cid, now)
            }

            Nsid.ACTOR_PROFILE -> {
                if (uri.rkey == ProfileRecord.RKEY)
                    updateProfile(AtJson.decodeFromJsonElement(ProfileRecord.serializer(), json), author, now)
            }
        }
    }

    private fun JdbcTransaction.upsertActivity(record: ActivityRecord, author: Did, uri: AtUri, cid: String, now: Long) {
        val photos = boundedPhotos(record.media)
        Activities.upsert {
            it[Activities.uri] = uri.value
            it[Activities.cid] = cid
            it[authorDid] = author.value
            it[Activities.record] =
                AtJson.encodeToString(
                    ActivityRecord.serializer(),
                    record.copy(sensors = boundedSensors(record.sensors), dive = boundedDive(record.dive), media = photos),
                )
            it[sport] = record.sport.take(MAX_SPORT_LENGTH)
            it[subSport] = record.subSport?.take(MAX_SPORT_LENGTH)
            it[startedAt] = record.startedAt.toEpochMilliseconds()
            it[sortAt] = minOf(record.createdAt.toEpochMilliseconds(), now)
            it[indexedAt] = now
            // Untrusted: a recording links only to another of its author's activities, and must fit the column.
            it[recordingOf] =
                record.recordingOf
                    ?.takeIf { of -> of.authority == author.value && of.collection == Nsid.FEED_ACTIVITY && of.rkey != null && of != uri }
                    ?.value
                    ?.takeIf { of -> of.length <= MAX_URI_LENGTH }
        }
        ActivityMediaBlobs.deleteWhere { activityUri eq uri.value }
        ActivityMediaBlobs.batchInsert(photos.orEmpty().withIndex()) { (position, photo) ->
            this[ActivityMediaBlobs.activityUri] = uri.value
            this[ActivityMediaBlobs.sortIndex] = position
            this[ActivityMediaBlobs.authorDid] = author.value
            this[ActivityMediaBlobs.cid] = photo.image.ref.link
            this[ActivityMediaBlobs.mimeType] = photo.image.mimeType
        }
    }

    /** Records are untrusted: hold the sensors to the lexicon's limits before serving them. */
    private fun boundedSensors(sensors: List<ActivitySensor>?): List<ActivitySensor>? =
        sensors
            ?.take(ActivitySensor.MAX)
            ?.map {
                ActivitySensor(
                    kind = it.kind.take(MAX_SENSOR_KIND_LENGTH),
                    manufacturer = it.manufacturer?.take(MAX_SENSOR_NAME_LENGTH),
                    model = it.model?.take(MAX_SENSOR_NAME_LENGTH),
                )
            }
            ?.ifEmpty { null }

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
            it[indexedAt] = now
        }
    }

    private fun JdbcTransaction.delete(collection: String, uri: AtUri) {
        when (collection) {
            Nsid.FEED_ACTIVITY -> {
                Activities.deleteWhere { Activities.uri eq uri.value }
                ActivityMediaBlobs.deleteWhere { activityUri eq uri.value }
            }

            Nsid.FEED_ACTIVITY_STREAMS -> {
                ActivityStreams.deleteWhere { ActivityStreams.uri eq uri.value }
                dropOrphanedStreamData()
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
        ActivityMediaBlobs.deleteWhere { authorDid eq did.value }
        ActivityStreams.deleteWhere { authorDid eq did.value }
        dropOrphanedStreamData()
        Kudos.deleteWhere { authorDid eq did.value }
        Comments.deleteWhere { authorDid eq did.value }
        Follows.deleteWhere { authorDid eq did.value }
        ActorPrivateDetails.deleteWhere { ActorPrivateDetails.did eq did.value }
        Actors.deleteWhere { Actors.did eq did.value }
    }

    // Records are untrusted, so free text is clamped to the lexicon maxLength (or the column size) before it's stored.
    private companion object {
        const val MAX_SPORT_LENGTH = 64
        private const val MAX_URI_LENGTH = 512
        const val MAX_COMMENT_LENGTH = 10_000
        const val MAX_DISPLAY_NAME_LENGTH = 640
        const val MAX_DESCRIPTION_LENGTH = 2560
        const val MAX_UNITS_LENGTH = 16
        const val MAX_SENSOR_KIND_LENGTH = 64
        const val MAX_SENSOR_NAME_LENGTH = 128
    }
}

/** Records are untrusted: hold a dive's lists and gas percents to the lexicon's limits before serving them. */
private fun boundedDive(dive: ActivityDive?): ActivityDive? =
    dive?.copy(
        gases =
            dive.gases?.take(ActivityDive.MAX_GASES)?.map {
                ActivityDiveGas(it.oxygen.coerceIn(0, ActivityDiveGas.MAX_PERCENT), it.helium.coerceIn(0, ActivityDiveGas.MAX_PERCENT))
            },
        tanks = dive.tanks?.take(ActivityDive.MAX_TANKS),
        site = dive.site?.take(ActivityDive.MAX_SITE_LENGTH),
    )

/**
 * Records are untrusted: keep only photos the lexicon allows, with a real CID, and drop media kinds this AppView can't serve (they stay in
 * the athlete's repo).
 */
private fun boundedPhotos(media: List<ActivityMedia>?): List<ActivityMedia.Image>? =
    media
        ?.take(ActivityMedia.MAX)
        ?.filterIsInstance<ActivityMedia.Image>()
        ?.filter {
            it.image.mimeType in PHOTO_TYPES &&
                it.image.size in 1..ActivityMedia.Image.MAX_BYTES &&
                ProfileDetailRules.isCid(it.image.ref.link)
        }
        ?.map { photo ->
            photo.copy(
                alt = photo.alt?.take(ActivityMedia.Image.MAX_ALT_LENGTH),
                aspectRatio = photo.aspectRatio?.takeIf { it.width > 0 && it.height > 0 },
            )
        }
        ?.ifEmpty { null }

private val PHOTO_TYPES = setOf("image/webp", "image/jpeg")
