package io.github.solcott.activitytracker.appview.api

import io.github.solcott.activitytracker.appview.db.Activities
import io.github.solcott.activitytracker.appview.db.ActivityStreamData as StreamDataCache
import io.github.solcott.activitytracker.appview.db.ActivityStreamFetchFailures
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
import io.github.solcott.activitytracker.lexicon.app.Visibility
import io.github.solcott.activitytracker.lexicon.app.ZoneSnapshot
import io.github.solcott.activitytracker.lexicon.app.mergeStreams
import io.github.solcott.activitytracker.lexicon.atproto.SpaceUri
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.HttpStatusCode
import io.ktor.utils.io.readBuffer
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Clock
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.io.readByteArray
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import org.jetbrains.exposed.v1.core.NotExists
import org.jetbrains.exposed.v1.core.Op
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.not
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.JdbcTransaction
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.upsert
import org.slf4j.LoggerFactory

/**
 * Where an activity's stream data comes from: the JSON blob a `feed.activityStreams` record names, held where the record is ([origin]: the
 * athlete's repo, or one of their tier spaces).
 */
fun interface StreamSource {
    /**
     * The blob's bytes, or null when it's missing; throws when its host can't be reached. Bytes past [ActivityStreamsRecord.MAX_DATA_BYTES]
     * may be cut off, but there are more of them than that, so a blob too large is still told apart.
     */
    suspend fun fetch(did: Did, blobCid: String, origin: String): ByteArray?
}

/**
 * Fetches public stream blobs with `com.atproto.sync.getBlob` from the author's PDS, and tier-space ones as a member through [spaceBlobs]
 * (none when syncing is off), refusing any over the lexicon's size limit.
 */
class PdsBlobStreamSource(private val http: HttpClient, private val pds: PdsLookup, private val spaceBlobs: SpaceBlobs? = null) :
    StreamSource {
    override suspend fun fetch(did: Did, blobCid: String, origin: String): ByteArray? =
        if (origin == Origins.REPO) fromPds(did, blobCid) else spaceBlobs?.fetch(SpaceUri(origin), did, blobCid)?.bytes

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
            ?.readBuffer(ActivityStreamsRecord.MAX_DATA_BYTES + 1)
            ?.readByteArray()
    }
}

/**
 * Serves `feed.getActivityStreams`, the only way clients read streams, so visibility holds here: `public` for anyone, `followers` for the
 * athlete's effective followers, `mutuals` for accounts the athlete and the viewer both follow, and anything else (`self`, unknown) for the
 * athlete only. An activity has one streams record per tier holding its channels, and a viewer gets the union of those they're admitted to.
 * A row's visibility is where its record is stored: `public` in the repo, the tier in a space. Streams are never shown to a viewer who may
 * not see their activity, when it's indexed. Zones reveal thresholds, so they're a record of their own (`feed.activityZones`), which the
 * athlete stores at the narrowest of the heart rate, power and training visibilities, and which needs the viewer admitted to its tier too.
 */
class ActivityStreamsViews(private val db: Database, private val source: StreamSource, private val clock: Clock = Clock.System) {
    private val log = LoggerFactory.getLogger(ActivityStreamsViews::class.java)

    /** Fetches running now, by where they fetch from, so views asking at once for the same data share one (feed/detail-visibility). */
    private val inFlight = ConcurrentHashMap<Source, CompletableDeferred<ActivityStreamData?>>()

    /**
     * The union of the activity's streams records in the tiers [viewer] is admitted to, widest first, each narrower one's channels and lap
     * and leg fields laid over the wider ([mergeStreams]). A part whose blob can't be had is left out; with none left it's `NotFound`. The
     * parts not held are fetched at once, and a failed fetch is remembered ([ActivityStreamFetchFailures]) so later views don't repeat it.
     */
    suspend fun streams(viewer: Did?, params: GetActivityStreams.Params): GetActivityStreams.Output {
        val visible = db.tx { visibleStreams(viewer, params.uri) } ?: notFound()
        val held = db.tx { held(visible.parts, clock.now().toEpochMilliseconds()) }
        val parts = coroutineScope {
            visible.parts
                .map { part ->
                    async {
                        when (val h = held.getValue(part.source)) {
                            is Held.Data -> h.data
                            Held.Skip -> null
                            Held.Fetch -> fetchShared(part.source)
                        }?.let { part to it }
                    }
                }
                .awaitAll()
        }
            .filterNotNull()
        val data = mergeStreams(parts.map { it.second }) ?: notFound()
        return GetActivityStreams.Output(
            uri = params.uri,
            visibility = parts.first().first.visibility,
            channels = data.channels,
            data = data,
            heartRateZones = visible.heartRateZones,
            powerZones = visible.powerZones,
            trainingLoad = visible.trainingLoad,
            parts = parts.map { (part, _) -> GetActivityStreams.StreamsPart(part.visibility, part.channels) },
        )
    }

    /** One streams record the viewer may see: where its blob is, and the tier it's served at. */
    private class Part(val author: String, val origin: String, val visibility: String, val channels: List<String>, val blobCid: String) {
        val source = Source(blobCid, author, origin)
    }

    /** Where a blob is fetched from: a failure, and a fetch in flight, are per source, since a missing blob depends on its host. */
    private data class Source(val blobCid: String, val author: String, val origin: String)

    /** What a part's data is without fetching it: held, known not to be had for now, or to be fetched. */
    private sealed interface Held {
        class Data(val data: ActivityStreamData) : Held

        data object Skip : Held

        data object Fetch : Held
    }

    private class Visible(
        /** Widest first. */
        val parts: List<Part>,
        val heartRateZones: ZoneSnapshot?,
        val powerZones: ZoneSnapshot?,
        val trainingLoad: TrainingLoad?,
    )

    private fun JdbcTransaction.visibleStreams(viewer: Did?, activity: AtUri): Visible? {
        val parts =
            ActivityStreams.selectAll()
                .where {
                    (ActivityStreams.activityUri eq activity.value) and
                        visibleTo(viewer, ActivityStreams.visibility, ActivityStreams.authorDid, ActivityStreams.activityUri, null) and
                        activityVisible(activity, viewer)
                }
                .map { row ->
                    Part(
                        author = row[ActivityStreams.authorDid],
                        origin = row[ActivityStreams.origin],
                        visibility = row[ActivityStreams.visibility],
                        channels = AtJson.decodeFromString(ListSerializer(String.serializer()), row[ActivityStreams.channels]),
                        blobCid = row[ActivityStreams.blobCid],
                    )
                }
                // Several origins never share a tier, but sort by origin too so the order is stable.
                .sortedWith(compareBy({ Visibility.rank(it.visibility) }, { it.origin }))
                .takeIf { it.isNotEmpty() } ?: return null
        // Zones are their own record, stored (and so served) at the narrowest of the heart rate, power and training visibilities.
        val zonesRow =
            ActivityZones.selectAll()
                .where {
                    (ActivityZones.activityUri eq activity.value) and
                        visibleTo(viewer, ActivityZones.visibility, ActivityZones.authorDid, ActivityZones.activityUri, null)
                }
                .singleOrNull()
        fun zones(json: String?) = json?.let { AtJson.decodeFromString(ZoneSnapshot.serializer(), it) }
        return Visible(
            parts = parts,
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
                exerciseLoad = load.exerciseLoad.within(TrainingLoad.MAX_EXERCISE_LOAD),
                staminaStart = load.staminaStart.within(TrainingLoad.MAX_STAMINA),
                staminaEnd = load.staminaEnd.within(TrainingLoad.MAX_STAMINA),
                staminaMin = load.staminaMin.within(TrainingLoad.MAX_STAMINA),
                sweatLoss = load.sweatLoss.within(TrainingLoad.MAX_SWEAT_LOSS),
                hrvRmssd = load.hrvRmssd.within(MAX_HRV_RMSSD),
            )
        }
        ?.takeUnless { it.isEmpty }

    /** False only when the streams' activity is indexed and [viewer] may not see it: charts are never more visible than their activity. */
    private fun activityVisible(activity: AtUri, viewer: Did?): Op<Boolean> =
        NotExists(Activities.select(Activities.uri).where { (Activities.uri eq activity.value) and not(visibleTo(viewer)) })

    /** Each part's [Held] at [now], from the cached data and the remembered failures of every part's blob in one read. */
    private fun JdbcTransaction.held(parts: List<Part>, now: Long): Map<Source, Held> {
        val cids = parts.map { it.blobCid }.distinct()
        val cached =
            StreamDataCache.selectAll()
                .where { StreamDataCache.blobCid inList cids }
                .mapNotNull { row ->
                    // A row an older AppView wrote in another shape is fetched again.
                    runCatching { AtJson.decodeFromString(ActivityStreamData.serializer(), row[StreamDataCache.data]) }
                        .getOrNull()
                        ?.let { row[StreamDataCache.blobCid] to it }
                }
                .toMap()
        val failures = ActivityStreamFetchFailures.selectAll().where { ActivityStreamFetchFailures.blobCid inList cids }.toList()
        val badContent =
            failures
                .filter { it[ActivityStreamFetchFailures.kind] == ActivityStreamFetchFailures.KIND_CONTENT }
                .map { it[ActivityStreamFetchFailures.blobCid] }
        val waiting =
            failures
                .filter { it[ActivityStreamFetchFailures.kind] == ActivityStreamFetchFailures.KIND_UNAVAILABLE }
                .filter { (it[ActivityStreamFetchFailures.retryAt] ?: 0) > now }
                .map {
                    Source(
                        it[ActivityStreamFetchFailures.blobCid],
                        it[ActivityStreamFetchFailures.authorDid],
                        it[ActivityStreamFetchFailures.origin],
                    )
                }
                .toSet()
        return parts.associate { part ->
            part.source to
                when {
                    part.blobCid in cached -> Held.Data(cached.getValue(part.blobCid))
                    part.blobCid in badContent || part.source in waiting -> Held.Skip
                    else -> Held.Fetch
                }
        }
    }

    /** [fetch], or the fetch from [source] already running for another view. Null for a view whose own fetch was cancelled. */
    private suspend fun fetchShared(source: Source): ActivityStreamData? {
        val mine = CompletableDeferred<ActivityStreamData?>()
        inFlight.putIfAbsent(source, mine)?.let {
            return it.await()
        }
        try {
            return fetch(source).also { mine.complete(it) }
        } finally {
            mine.complete(null)
            inFlight.remove(source, mine)
        }
    }

    /**
     * The blob from [StreamSource], held to sane limits (it's untrusted) and cached. Null when it's missing or isn't stream data, which is
     * remembered as a failure.
     */
    private suspend fun fetch(source: Source): ActivityStreamData? {
        val bytes = runSuspendCatching {
            this.source.fetch(Did(source.author), source.blobCid, source.origin)
        }
            .onFailure { log.debug("Couldn't fetch stream data {} from {}: {}", source.blobCid, source.author, it.message) }
            .getOrNull()
        val data = bytes?.takeIf { it.size <= ActivityStreamsRecord.MAX_DATA_BYTES }?.let { decoded(source, it) }?.let(::bounded)
        when {
            bytes == null -> failed(source, ActivityStreamFetchFailures.KIND_UNAVAILABLE)
            data == null -> failed(source, ActivityStreamFetchFailures.KIND_CONTENT)
            else -> held(source, data)
        }
        return data
    }

    /** [bytes] as stream data, or null when they aren't. */
    private fun decoded(source: Source, bytes: ByteArray): ActivityStreamData? = runCatching {
        AtJson.decodeFromString(ActivityStreamData.serializer(), bytes.decodeToString())
    }
        .onFailure { log.debug("Skipping undecodable stream data {} from {}: {}", source.blobCid, source.author, it.message) }
        .getOrNull()

    /** Caches [data] by its CID and forgets the CID's failures. */
    private suspend fun held(source: Source, data: ActivityStreamData) {
        db.tx {
            StreamDataCache.upsert {
                it[StreamDataCache.blobCid] = source.blobCid
                it[StreamDataCache.data] = AtJson.encodeToString(ActivityStreamData.serializer(), data)
                it[fetchedAt] = clock.now().toEpochMilliseconds()
            }
            ActivityStreamFetchFailures.deleteWhere { blobCid eq source.blobCid }
        }
    }

    /** Remembers a failed fetch from [source]: [kind] content for good, unavailable until a pause that doubles with each failure. */
    private suspend fun failed(source: Source, kind: String) {
        val now = clock.now().toEpochMilliseconds()
        db.tx {
            val key =
                (ActivityStreamFetchFailures.blobCid eq source.blobCid) and
                    (ActivityStreamFetchFailures.authorDid eq source.author) and
                    (ActivityStreamFetchFailures.origin eq source.origin)
            val failures =
                (ActivityStreamFetchFailures.selectAll().where { key }.singleOrNull()?.get(ActivityStreamFetchFailures.failures) ?: 0) + 1
            ActivityStreamFetchFailures.upsert {
                it[blobCid] = source.blobCid
                it[authorDid] = source.author
                it[origin] = source.origin
                it[this.kind] = kind
                it[this.failures] = failures
                it[retryAt] = if (kind == ActivityStreamFetchFailures.KIND_CONTENT) null else now + retryAfterMillis(failures)
            }
        }
    }

    private fun notFound(): Nothing = throw XrpcError(HttpStatusCode.NotFound.value, "NotFound", "No streams the viewer may see")

    companion object {
        /** The pause after a first failed fetch of data that was missing or unreachable, doubling with each failure after. */
        const val FIRST_RETRY_MS = 60_000L

        /** The longest pause between fetches of data that keeps failing. */
        const val MAX_RETRY_MS = 3_600_000L

        /** How long after its [failures]th failure a fetch is tried again. */
        fun retryAfterMillis(failures: Int): Long = minOf(MAX_RETRY_MS, FIRST_RETRY_MS shl (failures - 1).coerceIn(0, MAX_DOUBLINGS))

        // 2^6 minutes is past the hour already.
        private const val MAX_DOUBLINGS = 6

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
