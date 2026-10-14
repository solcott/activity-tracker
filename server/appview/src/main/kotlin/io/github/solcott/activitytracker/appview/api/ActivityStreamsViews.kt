package io.github.solcott.activitytracker.appview.api

import io.github.solcott.activitytracker.appview.db.ActivityStreamData as StreamDataCache
import io.github.solcott.activitytracker.appview.db.ActivityStreams
import io.github.solcott.activitytracker.appview.db.Actors
import io.github.solcott.activitytracker.appview.db.tx
import io.github.solcott.activitytracker.atproto.runSuspendCatching
import io.github.solcott.activitytracker.lexicon.AtJson
import io.github.solcott.activitytracker.lexicon.AtUri
import io.github.solcott.activitytracker.lexicon.Did
import io.github.solcott.activitytracker.lexicon.app.ActivityStreamData
import io.github.solcott.activitytracker.lexicon.app.ActivityStreamsRecord
import io.github.solcott.activitytracker.lexicon.app.GetActivityStreams
import io.github.solcott.activitytracker.lexicon.app.Visibility
import io.github.solcott.activitytracker.lexicon.app.ZoneSnapshot
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
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.JdbcTransaction
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.upsert
import org.slf4j.LoggerFactory

/**
 * Where an activity's stream data comes from: the JSON blob a `feed.activityStreams` record names.
 *
 * TODO(spaces): streams are public records for now, read from the author's PDS. When they move to a private atproto Space, the AppView
 *   reads them as a member of it instead, and only this binding changes (see BACKLOG.md).
 */
fun interface StreamSource {
    /** The blob's bytes, or null when it can't be had. */
    suspend fun fetch(did: Did, blobCid: String): ByteArray?
}

/** Fetches stream blobs with `com.atproto.sync.getBlob` from the author's PDS, refusing any over the lexicon's size limit. */
class PdsBlobStreamSource(private val http: HttpClient, private val pds: PdsLookup) : StreamSource {
    override suspend fun fetch(did: Did, blobCid: String): ByteArray? {
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
            ?.takeIf { it.size <= ActivityStreamsRecord.MAX_DATA_BYTES }
    }
}

/**
 * Serves `feed.getActivityStreams`. The records are public for now, but this is the only way clients read them, so visibility holds here:
 * `public` for anyone, `mutuals` for accounts the athlete and the viewer both follow, and anything else (`self`, unknown) for the athlete
 * only. Zones reveal thresholds, so they also need the viewer to be allowed the athlete's training details.
 */
class ActivityStreamsViews(private val db: Database, private val source: StreamSource, private val clock: Clock = Clock.System) {
    private val log = LoggerFactory.getLogger(ActivityStreamsViews::class.java)

    suspend fun streams(viewer: Did?, params: GetActivityStreams.Params): GetActivityStreams.Output {
        val visible = db.tx { visibleStreams(viewer, params.uri) } ?: notFound()
        val data = cached(visible.blobCid) ?: fetch(Did(visible.author), visible.blobCid) ?: notFound()
        return GetActivityStreams.Output(
            uri = params.uri,
            visibility = visible.visibility,
            channels = visible.channels,
            data = data,
            heartRateZones = visible.heartRateZones,
            powerZones = visible.powerZones,
        )
    }

    private class Visible(
        val author: String,
        val visibility: String,
        val channels: List<String>,
        val blobCid: String,
        val heartRateZones: ZoneSnapshot?,
        val powerZones: ZoneSnapshot?,
    )

    private fun JdbcTransaction.visibleStreams(viewer: Did?, activity: AtUri): Visible? {
        val row =
            ActivityStreams.selectAll()
                .where { ActivityStreams.activityUri eq activity.value }
                .singleOrNull()
                ?.takeIf { mayView(it, viewer) } ?: return null
        val author = row[ActivityStreams.authorDid]
        val isSelf = viewer?.value == author
        val actor = Actors.selectAll().where { Actors.did eq author }.singleOrNull()
        val seesTraining = isSelf || (actor != null && publicDetails(actor)?.training != null)
        fun zones(json: String?) = json?.takeIf { seesTraining }?.let { AtJson.decodeFromString(ZoneSnapshot.serializer(), it) }
        return Visible(
            author = author,
            visibility = row[ActivityStreams.visibility],
            channels = AtJson.decodeFromString(ListSerializer(String.serializer()), row[ActivityStreams.channels]),
            blobCid = row[ActivityStreams.blobCid],
            heartRateZones = zones(row[ActivityStreams.heartRateZones]),
            powerZones = zones(row[ActivityStreams.powerZones]),
        )
    }

    private fun JdbcTransaction.mayView(row: ResultRow, viewer: Did?): Boolean {
        val author = row[ActivityStreams.authorDid]
        val isSelf = viewer?.value == author
        return when (row[ActivityStreams.visibility]) {
            Visibility.PUBLIC -> true
            Visibility.MUTUALS -> isSelf || viewer != null && isMutual(viewer.value, author)
            else -> isSelf
        }
    }

    private suspend fun cached(blobCid: String): ActivityStreamData? = db.tx {
        StreamDataCache.selectAll()
            .where { StreamDataCache.blobCid eq blobCid }
            .singleOrNull()
            // A row an older AppView wrote in another shape is fetched again.
            ?.let { runCatching { AtJson.decodeFromString(ActivityStreamData.serializer(), it[StreamDataCache.data]) }.getOrNull() }
    }

    /** The blob from [StreamSource], held to sane limits (it's untrusted) and cached. Null when it's missing or isn't stream data. */
    private suspend fun fetch(author: Did, blobCid: String): ActivityStreamData? {
        val data =
            runSuspendCatching { source.fetch(author, blobCid) }
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
