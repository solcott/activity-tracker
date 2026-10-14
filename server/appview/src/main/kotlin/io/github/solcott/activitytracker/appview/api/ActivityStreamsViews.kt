package io.github.solcott.activitytracker.appview.api

import io.github.solcott.activitytracker.appview.db.Activities
import io.github.solcott.activitytracker.appview.db.ActivityStreamData as StreamDataCache
import io.github.solcott.activitytracker.appview.db.ActivityStreams
import io.github.solcott.activitytracker.appview.db.Actors
import io.github.solcott.activitytracker.appview.db.Origins
import io.github.solcott.activitytracker.appview.db.tx
import io.github.solcott.activitytracker.atproto.runSuspendCatching
import io.github.solcott.activitytracker.lexicon.AtJson
import io.github.solcott.activitytracker.lexicon.AtUri
import io.github.solcott.activitytracker.lexicon.Did
import io.github.solcott.activitytracker.lexicon.app.ActivityStreamData
import io.github.solcott.activitytracker.lexicon.app.ActivityStreamsRecord
import io.github.solcott.activitytracker.lexicon.app.GetActivityStreams
import io.github.solcott.activitytracker.lexicon.app.ZoneSnapshot
import io.github.solcott.activitytracker.lexicon.atproto.SpaceUri
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.HttpStatusCode
import io.ktor.utils.io.readRemaining
import kotlin.time.Clock
import kotlinx.io.readByteArray
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import org.jetbrains.exposed.v1.core.NotExists
import org.jetbrains.exposed.v1.core.Op
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.not
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.JdbcTransaction
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.upsert
import org.slf4j.LoggerFactory

/**
 * Where an activity's stream data comes from: the JSON blob a `feed.activityStreams` record names, held where the record is ([origin]: the
 * athlete's repo, or one of their tier spaces).
 */
fun interface StreamSource {
    /** The blob's bytes, or null when it can't be had. */
    suspend fun fetch(did: Did, blobCid: String, origin: String): ByteArray?
}

/**
 * Fetches public stream blobs with `com.atproto.sync.getBlob` from the author's PDS, and tier-space ones as a member through [spaceBlobs]
 * (none when syncing is off), refusing any over the lexicon's size limit.
 */
class PdsBlobStreamSource(private val http: HttpClient, private val pds: PdsLookup, private val spaceBlobs: SpaceBlobs? = null) :
    StreamSource {
    override suspend fun fetch(did: Did, blobCid: String, origin: String): ByteArray? =
        if (origin == Origins.REPO) fromPds(did, blobCid) else spaceBlobs?.fetch(SpaceUri(origin), did, blobCid)?.bytes?.takeIf(::fits)

    private suspend fun fromPds(did: Did, blobCid: String): ByteArray? {
        val host = pds.pdsFor(did) ?: return null
        val response =
            http.get("$host/xrpc/com.atproto.sync.getBlob") {
                parameter("did", did.value)
                parameter("cid", blobCid)
            }
        return response
            .takeIf { it.status == HttpStatusCode.OK }
            ?.bodyAsChannel()
            ?.readRemaining(ActivityStreamsRecord.MAX_DATA_BYTES + 1)
            ?.readByteArray()
            ?.takeIf(::fits)
    }

    private fun fits(bytes: ByteArray) = bytes.size <= ActivityStreamsRecord.MAX_DATA_BYTES
}

/**
 * Serves `feed.getActivityStreams`, the only way clients read streams, so visibility holds here: `public` for anyone, `followers` for the
 * athlete's effective followers, `mutuals` for accounts the athlete and the viewer both follow, and anything else (`self`, unknown) for the
 * athlete only. A space-held record's visibility is already capped at its tier. Streams are never shown to a viewer who may not see their
 * activity, when it's indexed. Zones reveal thresholds, so they also need the viewer to be allowed the athlete's training details.
 */
class ActivityStreamsViews(private val db: Database, private val source: StreamSource, private val clock: Clock = Clock.System) {
    private val log = LoggerFactory.getLogger(ActivityStreamsViews::class.java)

    suspend fun streams(viewer: Did?, params: GetActivityStreams.Params): GetActivityStreams.Output {
        val visible = db.tx { visibleStreams(viewer, params.uri) } ?: notFound()
        val data = cached(visible.blobCid) ?: fetch(Did(visible.author), visible.blobCid, visible.origin) ?: notFound()
        return GetActivityStreams.Output(
            uri = params.uri,
            visibility = visible.visibility,
            channels = visible.channels,
            data = data,
            heartRateZones = visible.heartRateZones,
            powerZones = visible.powerZones,
        )
    }

    @Suppress("LongParameterList") // One per part of the output, and where the data is.
    private class Visible(
        val author: String,
        val origin: String,
        val visibility: String,
        val channels: List<String>,
        val blobCid: String,
        val heartRateZones: ZoneSnapshot?,
        val powerZones: ZoneSnapshot?,
    )

    private fun JdbcTransaction.visibleStreams(viewer: Did?, activity: AtUri): Visible? {
        val row =
            ActivityStreams.selectAll()
                .where {
                    (ActivityStreams.activityUri eq activity.value) and
                        visibleTo(viewer, ActivityStreams.visibility, ActivityStreams.authorDid) and
                        activityVisible(activity, viewer)
                }
                .singleOrNull() ?: return null
        val author = row[ActivityStreams.authorDid]
        val isSelf = viewer?.value == author
        val actor = Actors.selectAll().where { Actors.did eq author }.singleOrNull()
        val seesTraining = isSelf || (actor != null && publicDetails(actor)?.training != null)
        fun zones(json: String?) = json?.takeIf { seesTraining }?.let { AtJson.decodeFromString(ZoneSnapshot.serializer(), it) }
        return Visible(
            author = author,
            origin = row[ActivityStreams.origin],
            visibility = row[ActivityStreams.visibility],
            channels = AtJson.decodeFromString(ListSerializer(String.serializer()), row[ActivityStreams.channels]),
            blobCid = row[ActivityStreams.blobCid],
            heartRateZones = zones(row[ActivityStreams.heartRateZones]),
            powerZones = zones(row[ActivityStreams.powerZones]),
        )
    }

    /** False only when the streams' activity is indexed and [viewer] may not see it: charts are never more visible than their activity. */
    private fun activityVisible(activity: AtUri, viewer: Did?): Op<Boolean> =
        NotExists(Activities.select(Activities.uri).where { (Activities.uri eq activity.value) and not(visibleTo(viewer)) })

    private suspend fun cached(blobCid: String): ActivityStreamData? = db.tx {
        StreamDataCache.selectAll()
            .where { StreamDataCache.blobCid eq blobCid }
            .singleOrNull()
            // A row an older AppView wrote in another shape is fetched again.
            ?.let { runCatching { AtJson.decodeFromString(ActivityStreamData.serializer(), it[StreamDataCache.data]) }.getOrNull() }
    }

    /** The blob from [StreamSource], held to sane limits (it's untrusted) and cached. Null when it's missing or isn't stream data. */
    private suspend fun fetch(author: Did, blobCid: String, origin: String): ActivityStreamData? {
        val data =
            runSuspendCatching { source.fetch(author, blobCid, origin) }
                .getOrNull()
                ?.let { bytes ->
                    runCatching { AtJson.decodeFromString(ActivityStreamData.serializer(), bytes.decodeToString()) }
                        .onFailure { log.debug("Skipping undecodable stream data {} from {}: {}", blobCid, author, it.message) }
                        .getOrNull()
                }
                ?.let(::bounded) ?: return null
        db.tx {
            StreamDataCache.upsert {
                it[StreamDataCache.blobCid] = blobCid
                it[StreamDataCache.data] = AtJson.encodeToString(ActivityStreamData.serializer(), data)
                it[fetchedAt] = clock.now().toEpochMilliseconds()
            }
        }
        return data
    }

    private fun notFound(): Nothing = throw XrpcError(HttpStatusCode.NotFound.value, "NotFound", "No streams the viewer may see")

    companion object {
        /** Four days at one sample a second. */
        const val MAX_SAMPLES = 345_600
        const val MAX_GEAR_CHANGES = 100_000

        /** [data] capped at [MAX_SAMPLES], with any channel that doesn't line up with its times dropped. */
        fun bounded(data: ActivityStreamData): ActivityStreamData {
            val time = data.time.take(MAX_SAMPLES)
            fun List<Int?>?.aligned() = this?.takeIf { it.size == data.time.size }?.take(MAX_SAMPLES)
            return ActivityStreamData(
                time = time,
                distance = data.distance.aligned(),
                altitude = data.altitude.aligned(),
                speed = data.speed.aligned(),
                heartRate = data.heartRate.aligned(),
                power = data.power.aligned(),
                cadence = data.cadence.aligned(),
                temperature = data.temperature.aligned(),
                depth = data.depth.aligned(),
                gears = data.gears?.take(MAX_GEAR_CHANGES),
            )
        }
    }
}
