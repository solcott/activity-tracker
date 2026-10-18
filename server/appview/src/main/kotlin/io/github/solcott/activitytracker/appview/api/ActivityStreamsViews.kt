package io.github.solcott.activitytracker.appview.api

import io.github.solcott.activitytracker.appview.db.Activities
import io.github.solcott.activitytracker.appview.db.ActivityStreamData as StreamDataCache
import io.github.solcott.activitytracker.appview.db.ActivityStreams
import io.github.solcott.activitytracker.appview.db.ActivityZones
import io.github.solcott.activitytracker.appview.db.Origins
import io.github.solcott.activitytracker.appview.db.tx
import io.github.solcott.activitytracker.atproto.runSuspendCatching
import io.github.solcott.activitytracker.lexicon.AtJson
import io.github.solcott.activitytracker.lexicon.AtUri
import io.github.solcott.activitytracker.lexicon.Did
import io.github.solcott.activitytracker.lexicon.app.ActivityStreamData
import io.github.solcott.activitytracker.lexicon.app.ActivityStreamsRecord
import io.github.solcott.activitytracker.lexicon.app.ActivityZonesRecord
import io.github.solcott.activitytracker.lexicon.app.DiveAlert
import io.github.solcott.activitytracker.lexicon.app.GetActivityStreams
import io.github.solcott.activitytracker.lexicon.app.TankPressure
import io.github.solcott.activitytracker.lexicon.app.TrainingLoad
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
 * athlete only. A row's visibility is where its record is stored: `public` in the repo, the tier in a space. Streams are never shown to a
 * viewer who may not see their activity, when it's indexed. Zones reveal thresholds, so they're a record of their own
 * (`feed.activityZones`), which the athlete stores at the narrower of the charts' and the training's visibility, and which needs the viewer
 * admitted to its tier too.
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
            trainingLoad = visible.trainingLoad,
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
        val trainingLoad: TrainingLoad?,
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
        // Zones are their own record, stored (and so served) at the narrower of the charts' and the training's visibility.
        val zonesRow =
            ActivityZones.selectAll()
                .where {
                    (ActivityZones.activityUri eq activity.value) and visibleTo(viewer, ActivityZones.visibility, ActivityZones.authorDid)
                }
                .singleOrNull()
        fun zones(json: String?) = json?.let { AtJson.decodeFromString(ZoneSnapshot.serializer(), it) }
        return Visible(
            author = row[ActivityStreams.authorDid],
            origin = row[ActivityStreams.origin],
            visibility = row[ActivityStreams.visibility],
            channels = AtJson.decodeFromString(ListSerializer(String.serializer()), row[ActivityStreams.channels]),
            blobCid = row[ActivityStreams.blobCid],
            heartRateZones = zones(zonesRow?.get(ActivityZones.heartRateZones)),
            powerZones = zones(zonesRow?.get(ActivityZones.powerZones)),
            trainingLoad = zonesRow?.let { trainingLoad(it[ActivityZones.record]) },
        )
    }

    /** The zones record's training load, from its raw JSON (no queries need a column), or null when it has none or it doesn't decode. */
    private fun trainingLoad(record: String): TrainingLoad? = runCatching {
        AtJson.decodeFromString(ActivityZonesRecord.serializer(), record).trainingLoad
    }
        .getOrNull()
        ?.let { load ->
            fun Int?.within(max: Int) = this?.takeIf { it in 0..max }
            TrainingLoad(
                ftp = load.ftp.within(MAX_LOAD_VALUE),
                intensityFactor = load.intensityFactor.within(MAX_LOAD_VALUE),
                trainingStressScore = load.trainingStressScore.within(MAX_LOAD_VALUE),
                aerobicTrainingEffect = load.aerobicTrainingEffect.within(MAX_TRAINING_EFFECT),
                anaerobicTrainingEffect = load.anaerobicTrainingEffect.within(MAX_TRAINING_EFFECT),
                hrvRmssd = load.hrvRmssd.within(MAX_HRV_RMSSD),
            )
        }
        ?.takeUnless { it.isEmpty }

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

        /** Above any FTP, IF or TSS in their units; values out of range are dropped. */
        const val MAX_LOAD_VALUE = 100_000

        /** Training effect runs from 0 to 5.0, in tenths. */
        const val MAX_TRAINING_EFFECT = 50

        /** RMSSD in milliseconds: a few hundred at most at rest, far less in exercise. */
        private const val MAX_HRV_RMSSD = 500
        const val MAX_GAS_SWITCHES = 1_000
        const val MAX_DIVE_ALERTS = 500
        const val MAX_TANKS = 8
        const val MAX_LAPS = 10_000
        const val MAX_LENGTHS = 2_000
        const val MAX_SETS = 500
        const val MAX_LEGS = 20
        private const val MAX_PERCENT = 100
        private const val MAX_HEART_RATE = 300

        /** The lexicon's `maxLength` for a lap's, length's or set's short tokens, and for an exercise or sport token. */
        private const val MAX_TOKEN = 32
        private const val MAX_LONG_TOKEN = 64

        /**
         * [data] capped at [MAX_SAMPLES], with any channel or tank series that doesn't line up with its times dropped, each list capped,
         * counts and durations no lower than 0, heart rates within range, and tokens no longer than the lexicon allows.
         */
        fun bounded(data: ActivityStreamData): ActivityStreamData {
            fun List<Int?>?.aligned() = this?.takeIf { it.size == data.time.size }?.take(MAX_SAMPLES)
            val base =
                ActivityStreamData(
                    time = data.time.take(MAX_SAMPLES),
                    gears = data.gears?.take(MAX_GEAR_CHANGES),
                    gasSwitches =
                        data.gasSwitches?.take(MAX_GAS_SWITCHES)?.map {
                            it.copy(oxygen = it.oxygen.coerceIn(0, MAX_PERCENT), helium = it.helium.coerceIn(0, MAX_PERCENT))
                        },
                    diveAlerts = data.diveAlerts?.take(MAX_DIVE_ALERTS)?.map { it.copy(kind = it.kind.take(DiveAlert.MAX_KIND_LENGTH)) },
                    tankPressures =
                        data.tankPressures?.take(MAX_TANKS)?.map { tank ->
                            TankPressure(tank.pressure.aligned() ?: List(minOf(data.time.size, MAX_SAMPLES)) { null })
                        },
                    laps =
                        data.laps?.take(MAX_LAPS)?.map {
                            it.copy(
                                avgHeartRate = it.avgHeartRate?.coerceIn(0, MAX_HEART_RATE),
                                maxHeartRate = it.maxHeartRate?.coerceIn(0, MAX_HEART_RATE),
                                calories = it.calories?.coerceAtLeast(0),
                                intensity = it.intensity?.take(MAX_TOKEN),
                                trigger = it.trigger?.take(MAX_TOKEN),
                            )
                        },
                    lengths =
                        data.lengths?.take(MAX_LENGTHS)?.map {
                            it.copy(
                                elapsed = it.elapsed.coerceAtLeast(0),
                                stroke = it.stroke?.take(MAX_TOKEN),
                                strokes = it.strokes?.coerceAtLeast(0),
                            )
                        },
                    sets =
                        data.sets?.take(MAX_SETS)?.map {
                            it.copy(
                                elapsed = it.elapsed.coerceAtLeast(0),
                                category = it.category?.take(MAX_LONG_TOKEN),
                                exercise = it.exercise?.take(MAX_LONG_TOKEN),
                                reps = it.reps?.coerceAtLeast(0),
                                weight = it.weight?.coerceAtLeast(0),
                            )
                        },
                    legs =
                        data.legs?.take(MAX_LEGS)?.map {
                            it.copy(
                                elapsed = it.elapsed.coerceAtLeast(0),
                                sport = it.sport.take(MAX_LONG_TOKEN),
                                subSport = it.subSport?.take(MAX_LONG_TOKEN),
                                avgHeartRate = it.avgHeartRate?.coerceIn(0, MAX_HEART_RATE),
                                maxHeartRate = it.maxHeartRate?.coerceIn(0, MAX_HEART_RATE),
                                calories = it.calories?.coerceAtLeast(0),
                            )
                        },
                )
            return ActivityStreamData.INT_CHANNELS.fold(base) { bounded, channel -> channel.with(bounded, channel.get(data).aligned()) }
        }
    }
}
