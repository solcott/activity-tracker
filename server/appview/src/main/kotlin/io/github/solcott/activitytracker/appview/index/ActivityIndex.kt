package io.github.solcott.activitytracker.appview.index

import io.github.solcott.activitytracker.appview.ProfileDetailRules
import io.github.solcott.activitytracker.appview.db.Activities
import io.github.solcott.activitytracker.appview.db.ActivityMediaBlobs
import io.github.solcott.activitytracker.appview.db.ActivityStreams
import io.github.solcott.activitytracker.appview.db.Likes
import io.github.solcott.activitytracker.appview.db.Origins
import io.github.solcott.activitytracker.appview.db.RecordShadows
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
import io.github.solcott.activitytracker.lexicon.app.Visibility
import io.github.solcott.activitytracker.lexicon.atproto.SpaceUri
import kotlinx.serialization.json.JsonObject
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.JdbcTransaction
import org.jetbrains.exposed.v1.jdbc.batchInsert
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.upsert

// Indexing of `feed.activity` records, and the placing of activities and their streams by origin, which the Indexer hands off here.

/**
 * [placement]'s copy of an activity or its streams: decoded, then indexed as the row unless another origin's narrower copy holds it
 * ([place]).
 */
@Suppress("LongParameterList") // The record, and where it's from.
internal fun JdbcTransaction.upsertPlaced(
    collection: String,
    author: Did,
    uri: AtUri,
    cid: String,
    json: JsonObject,
    placement: Placement,
    now: Long,
) {
    if (collection == Nsid.FEED_ACTIVITY) {
        val record = AtJson.decodeFromJsonElement(ActivityRecord.serializer(), json)
        place(collection, uri, cid, json, placement, placement.visibility, now) { upsertActivity(record, author, uri, cid, placement, now) }
    } else {
        val record = AtJson.decodeFromJsonElement(ActivityStreamsRecord.serializer(), json)
        // In a space, never wider than the tier; in the repo, the record's own field (kept as is, for records from before spaces).
        val visibility =
            if (placement == Placement.REPO) record.visibility.take(MAX_VISIBILITY_LENGTH)
            else Visibility.narrower(Visibility.known(record.visibility), placement.visibility)
        place(collection, uri, cid, json, placement, visibility, now) {
            upsertStreams(record, author, uri, cid, json, placement.origin, visibility, now)
        }
    }
}

/** Removes and returns the narrowest copy of [uri] another origin holds in [RecordShadows], if any. */
internal fun JdbcTransaction.takeShadow(uri: AtUri): ResultRow? {
    val next =
        RecordShadows.selectAll()
            .where { RecordShadows.uri eq uri.value }
            .maxByOrNull { Visibility.rank(Placement.of(it[RecordShadows.origin]).visibility) } ?: return null
    RecordShadows.deleteWhere { (RecordShadows.uri eq uri.value) and (origin eq next[RecordShadows.origin]) }
    return next
}

/**
 * Makes [placement]'s copy of [uri], served at [visibility], the indexed row by running [write], unless another origin's narrower copy
 * holds the row: then this one waits in [RecordShadows]. A wider copy holding the row steps aside into [RecordShadows] instead.
 */
@Suppress("LongParameterList") // The copy, where it's from, and how to write it.
private fun JdbcTransaction.place(
    collection: String,
    uri: AtUri,
    cid: String,
    json: JsonObject,
    placement: Placement,
    visibility: String,
    now: Long,
    write: JdbcTransaction.() -> Unit,
) {
    val held = heldCopy(collection, uri)
    if (held != null && held.origin != placement.origin) {
        if (Visibility.rank(held.visibility) >= Visibility.rank(visibility)) {
            shadow(uri, placement.origin, collection, visibility, cid, AtJson.encodeToString(JsonObject.serializer(), json), now)
            return
        }
        // Rows indexed before V15 kept no raw streams record; that copy is the repo's, whose own delete follows the move.
        held.record?.let { shadow(uri, held.origin, collection, held.visibility, held.cid, it, now) }
    }
    RecordShadows.deleteWhere { (RecordShadows.uri eq uri.value) and (origin eq placement.origin) }
    write()
}

/** The rkeys of the [collection] records indexed from [space]: the rows it holds, and its copies waiting in [RecordShadows]. */
internal fun JdbcTransaction.spaceRkeys(space: SpaceUri, collection: String): Set<String> {
    val origin = Placement.of(space).origin
    if (collection == Nsid.FEED_LIKE) {
        // Likes are never moved, so none is ever shadowed.
        return Likes.select(Likes.uri).where { Likes.origin eq origin }.mapNotNull { AtUri(it[Likes.uri]).rkey }.toSet()
    }
    val (uriColumn, originColumn) =
        if (collection == Nsid.FEED_ACTIVITY) Activities.uri to Activities.origin else ActivityStreams.uri to ActivityStreams.origin
    val held = uriColumn.table.select(uriColumn).where { originColumn eq origin }.map { it[uriColumn] }
    val shadowed =
        RecordShadows.select(RecordShadows.uri)
            .where { (RecordShadows.origin eq origin) and (RecordShadows.collection eq collection) }
            .map { it[RecordShadows.uri] }
    return (held + shadowed).mapNotNull { AtUri(it).rkey }.toSet()
}

internal fun JdbcTransaction.heldCopy(collection: String, uri: AtUri): HeldCopy? =
    when (collection) {
        Nsid.FEED_ACTIVITY ->
            Activities.selectAll()
                .where { Activities.uri eq uri.value }
                .singleOrNull()
                ?.let { HeldCopy(it[Activities.origin], it[Activities.visibility], it[Activities.cid], it[Activities.record]) }
        Nsid.FEED_ACTIVITY_STREAMS ->
            ActivityStreams.selectAll()
                .where { ActivityStreams.uri eq uri.value }
                .singleOrNull()
                ?.let {
                    HeldCopy(
                        it[ActivityStreams.origin],
                        it[ActivityStreams.visibility],
                        it[ActivityStreams.cid],
                        it[ActivityStreams.record],
                    )
                }
        else -> null
    }

@Suppress("LongParameterList") // One column each.
private fun JdbcTransaction.shadow(
    uri: AtUri,
    origin: String,
    collection: String,
    visibility: String,
    cid: String,
    record: String,
    now: Long,
) {
    RecordShadows.upsert {
        it[RecordShadows.uri] = uri.value
        it[RecordShadows.origin] = origin
        it[RecordShadows.collection] = collection
        it[RecordShadows.visibility] = visibility
        it[RecordShadows.cid] = cid
        it[RecordShadows.record] = record
        it[indexedAt] = now
    }
}

@Suppress("LongParameterList") // The record, and where it's from.
private fun JdbcTransaction.upsertActivity(
    record: ActivityRecord,
    author: Did,
    uri: AtUri,
    cid: String,
    placement: Placement,
    now: Long,
) {
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
        it[visibility] = placement.visibility
        it[origin] = placement.origin
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

/** An origin's copy of a record that holds the indexed row. [record] is null only for a streams row indexed before V15. */
internal class HeldCopy(val origin: String, val visibility: String, val cid: String, val record: String?)

/** Where a copy of an activity or its streams comes from ([origin]), and the tier it's served at. */
data class Placement(val origin: String, val visibility: String) {
    companion object {
        val REPO = Placement(Origins.REPO, Visibility.PUBLIC)

        fun of(space: SpaceUri) = Placement(space.value, Visibility.known(space.skey))

        /** The placement an [origin] stands for. */
        fun of(origin: String) = if (origin == Origins.REPO) REPO else of(SpaceUri(origin))
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

// Records are untrusted, so free text is clamped to the lexicon maxLength (or the column size) before it's stored.
private const val MAX_SPORT_LENGTH = 64
private const val MAX_URI_LENGTH = 512
private const val MAX_SENSOR_KIND_LENGTH = 64
private const val MAX_SENSOR_NAME_LENGTH = 128
private const val MAX_VISIBILITY_LENGTH = 16
