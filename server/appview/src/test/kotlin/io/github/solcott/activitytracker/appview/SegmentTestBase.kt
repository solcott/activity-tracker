package io.github.solcott.activitytracker.appview

import io.github.solcott.activitytracker.appview.api.SegmentCandidateViews
import io.github.solcott.activitytracker.appview.api.SpaceBlobs
import io.github.solcott.activitytracker.appview.auth.ServiceAuthVerifier
import io.github.solcott.activitytracker.appview.db.DatabaseFactory
import io.github.solcott.activitytracker.appview.db.SegmentCandidateRiders
import io.github.solcott.activitytracker.appview.db.SegmentCandidateRows
import io.github.solcott.activitytracker.appview.db.SegmentEfforts
import io.github.solcott.activitytracker.appview.db.SegmentMatchJobs
import io.github.solcott.activitytracker.appview.db.tx
import io.github.solcott.activitytracker.appview.index.Indexer
import io.github.solcott.activitytracker.appview.index.JetstreamEvent
import io.github.solcott.activitytracker.appview.segments.SegmentMatcher
import io.github.solcott.activitytracker.atproto.xrpc.XrpcBytes
import io.github.solcott.activitytracker.lexicon.AtJson
import io.github.solcott.activitytracker.lexicon.Did
import io.github.solcott.activitytracker.lexicon.Nsid
import io.github.solcott.activitytracker.lexicon.XrpcQuery
import io.github.solcott.activitytracker.lexicon.app.AthleteSpace
import io.github.solcott.activitytracker.lexicon.app.PutSegmentCandidates
import io.github.solcott.activitytracker.lexicon.app.SegmentCandidate
import io.github.solcott.activitytracker.lexicon.app.SegmentSource
import io.github.solcott.activitytracker.lexicon.app.SegmentTrackData
import io.github.solcott.activitytracker.lexicon.app.SegmentTrackPiece
import io.github.solcott.activitytracker.lexicon.app.Visibility
import io.github.solcott.activitytracker.lexicon.atproto.SpaceUri
import io.github.solcott.activitytracker.location.GeoPoint
import io.github.solcott.activitytracker.location.SegmentGeometry
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.encodeURLParameter
import java.util.UUID
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.test.assertEquals
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.jsonObject
import org.h2.jdbcx.JdbcDataSource
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.selectAll

/**
 * Shared setup for the segment tests (segments/segment-records, segments/segment-efforts): an H2 index, a matcher over fake `self`-space
 * blobs, and helpers that lay out segments and tracks in meters east and north of a point.
 */
open class SegmentTestBase {
    protected val service = "did:web:localhost%3A8081"
    protected val alice = "did:plc:alice"
    protected val bob = "did:plc:bob"
    protected val carol = "did:plc:carol"
    /** The segment importer's account (segments/segment-candidates): the indexer's always, the AppView's only where a test says so. */
    protected val importer = "did:plc:importer"
    protected val keys = mapOf(alice to TestAccountKey(), bob to TestAccountKey(), carol to TestAccountKey(), importer to TestAccountKey())

    protected val dataSource =
        JdbcDataSource().apply {
            setURL("jdbc:h2:mem:${UUID.randomUUID()};MODE=PostgreSQL;DB_CLOSE_DELAY=-1")
            DatabaseFactory.migrate(this)
        }
    protected val db: Database = Database.connect(dataSource)
    protected val indexer = Indexer(db, { did -> did.value.substringAfterLast(':') + ".test" }, segmentImporter = Did(importer))
    protected val config = AppViewConfig.fromEnv(emptyMap())
    protected val verifier = ServiceAuthVerifier(service, keys = { keys[it.value]?.multibase })

    /** Segment-track blobs in athletes' `self` spaces, by CID. */
    protected val blobs = mutableMapOf<String, ByteArray>()
    /** The CIDs the matcher fetched, in order. */
    protected val fetched = mutableListOf<String>()
    protected var blobsDown = false
    protected val matcher =
        SegmentMatcher(
            db,
            indexer.segmentJobs,
            SpaceBlobs { space, repo, cid ->
                check(space == self(repo.value)) { "fetched from $space" }
                fetched += cid
                check(!blobsDown) { "the PDS is down" }
                blobs[cid]?.let { XrpcBytes(it, "application/json") }
            },
        )

    private val origin = GeoPoint(47.0, 8.0)
    private val metersPerDegreeLat = 6_371_000.0 * PI / 180
    private val metersPerDegreeLng = metersPerDegreeLat * cos(origin.lat * PI / 180)

    /** A point [east] and [north] meters from the origin. */
    protected fun at(east: Double, north: Double) =
        GeoPoint(origin.lat + north / metersPerDegreeLat, origin.lng + east / metersPerDegreeLng)

    /** A 1 km stretch running north from the origin. */
    protected val straight = listOf(0.0 to 0.0, 0.0 to 1000.0)

    /** A run along [straight], from 100 m before it to 100 m after. */
    protected val alongStraight = listOf(0.0 to -100.0, 0.0 to 1100.0)

    protected fun self(did: String) = SpaceUri.of(Did(did), Nsid.SPACE_ATHLETE, AthleteSpace.SELF)

    protected fun space(tier: String, did: String = alice) = SpaceUri.of(Did(did), Nsid.SPACE_ATHLETE, tier)

    protected fun activityUri(rkey: String, did: String = alice) = "at://$did/${Nsid.FEED_ACTIVITY}/$rkey"

    protected fun segmentUri(rkey: String, did: String = alice) = "at://$did/${Nsid.FEED_SEGMENT}/$rkey"

    protected fun trackUri(rkey: String, did: String = alice) = "at://$did/${Nsid.FEED_SEGMENT_TRACK}/$rkey"

    protected fun commit(did: String, collection: String, rkey: String, record: String?) = runBlocking {
        indexer.handle(
            JetstreamEvent.Commit(
                did = did,
                time = "2026-10-14T12:00:00.000000Z",
                operation = if (record == null) "delete" else "create",
                collection = collection,
                rkey = rkey,
                record = record?.let { AtJson.parseToJsonElement(it).jsonObject },
                cid = "bafy$rkey${record?.hashCode()}",
            )
        )
    }

    protected fun spaceWrite(space: SpaceUri, collection: String, rkey: String, json: String) = runBlocking {
        indexer.spaceWrite(space, collection, rkey, "bafy$rkey", AtJson.parseToJsonElement(json).jsonObject)
    }

    /** [did]'s [collection] record [json] at [tier]: in their repo when public, in their tier space otherwise. */
    protected fun put(did: String, collection: String, rkey: String, json: String, tier: String = Visibility.PUBLIC) {
        if (tier == Visibility.PUBLIC) commit(did, collection, rkey, json) else spaceWrite(space(tier, did), collection, rkey, json)
    }

    protected fun remove(did: String, collection: String, rkey: String, tier: String = Visibility.PUBLIC) {
        if (tier == Visibility.PUBLIC) commit(did, collection, rkey, null)
        else runBlocking { indexer.spaceDelete(space(tier, did), collection, rkey) }
    }

    protected fun lineOf(path: List<Pair<Double, Double>>) = SegmentGeometry.encodePolyline(path.map { (e, n) -> at(e, n) })

    @Suppress("LongParameterList")
    protected fun segmentJson(
        path: List<Pair<Double, Double>> = straight,
        sport: String = "run",
        name: String = "Hill",
        line: String = lineOf(path),
    ) = """{"name":"$name","sport":"$sport","line":"$line","source":"athlete","license":"CC0-1.0","createdAt":"2026-10-01T00:00:00Z"}"""

    protected fun segment(
        rkey: String,
        path: List<Pair<Double, Double>> = straight,
        sport: String = "run",
        did: String = alice,
        name: String = "Hill",
    ) = commit(did, Nsid.FEED_SEGMENT, rkey, segmentJson(path, sport, name))

    @Suppress("LongParameterList")
    protected fun activity(
        rkey: String,
        tier: String = Visibility.PUBLIC,
        sport: String = "run",
        subSport: String? = null,
        did: String = alice,
        extra: String = "",
        startedAt: String = "2026-10-05T23:30:00Z",
        utcOffset: Int = 120,
    ) =
        put(
            did,
            Nsid.FEED_ACTIVITY,
            rkey,
            """{"sport":"$sport",${subSport?.let { "\"subSport\":\"$it\"," }.orEmpty()}""" +
                """"startedAt":"$startedAt","utcOffset":$utcOffset,""" +
                """"elapsedTime":3600,$extra"createdAt":"2026-10-05T08:00:00Z"}""",
            tier,
        )

    protected fun matching(on: Boolean, did: String = alice) =
        spaceWrite(self(did), Nsid.ACTOR_SEGMENT_PREFS, "self", """{"matching":$on,"updatedAt":"2026-10-15T12:00:00Z"}""")

    /**
     * [did]'s segment track for activity [rkey]: [pieces] of waypoints (meters east, north), sampled every 5 m at [speed] m/s from the
     * first piece's start. Its blob is put in the fake `self` space under a CID that changes with its content.
     */
    protected fun track(
        rkey: String,
        pieces: List<List<Pair<Double, Double>>> = listOf(alongStraight),
        did: String = alice,
        speed: Double = 5.0,
        excluded: Boolean = false,
    ) = putTrack(rkey, AtJson.encodeToString(SegmentTrackData.serializer(), trackData(pieces, speed)).encodeToByteArray(), did, excluded)

    /** A segment track's data for [pieces] of waypoints (meters east, north), sampled every 5 m at [speed] m/s, as [track] puts it. */
    protected fun trackData(pieces: List<List<Pair<Double, Double>>> = listOf(alongStraight), speed: Double = 5.0): SegmentTrackData {
        var clock = 0.0
        return SegmentTrackData(
            pieces.map { path ->
                val lat = mutableListOf<Int>()
                val lng = mutableListOf<Int>()
                val t = mutableListOf<Long>()
                fun sample(east: Double, north: Double) {
                    val p = at(east, north)
                    lat += (p.lat * 1e7).roundToInt()
                    lng += (p.lng * 1e7).roundToInt()
                    t += clock.toLong()
                }
                for ((a, b) in path.zipWithNext()) {
                    val length = hypot(b.first - a.first, b.second - a.second)
                    var d = 0.0
                    while (d < length) {
                        sample(a.first + (b.first - a.first) * d / length, a.second + (b.second - a.second) * d / length)
                        d += 5.0
                        clock += 5.0 / speed
                    }
                }
                sample(path.last().first, path.last().second)
                clock += 60
                SegmentTrackPiece(lat, lng, t)
            }
        )
    }

    /** [did]'s segment track for activity [rkey] with [bytes] as its blob, under a CID that changes with them. */
    protected fun putTrack(rkey: String, bytes: ByteArray, did: String = alice, excluded: Boolean = false) {
        val cid = "bafkdata$rkey${bytes.contentHashCode()}"
        blobs[cid] = bytes
        spaceWrite(
            self(did),
            Nsid.FEED_SEGMENT_TRACK,
            rkey,
            """{"activity":"${activityUri(rkey, did)}","data":{"${'$'}type":"blob","ref":{"${'$'}link":"$cid"},""" +
                """"mimeType":"application/json","size":${bytes.size}},""" +
                (if (excluded) """"excludeFromLeaderboards":true,""" else "") +
                """"createdAt":"2026-10-15T12:00:00Z"}""",
        )
    }

    protected fun follow(author: String, subject: String) =
        commit(
            author,
            Nsid.GRAPH_FOLLOW,
            "3kf${author.last()}${subject.last()}",
            """{"subject":"$subject","createdAt":"2026-10-01T09:00:00Z"}""",
        )

    protected fun block(author: String, subject: String) =
        spaceWrite(
            space(AthleteSpace.INTERACTIONS, author),
            Nsid.GRAPH_BLOCK,
            subject,
            """{"subject":"$subject","createdAt":"2026-10-21T09:00:00Z"}""",
        )

    @Suppress("LongParameterList")
    protected fun candidate(
        id: String,
        path: List<Pair<Double, Double>> = straight,
        sport: String = "ride",
        name: String = "Trail",
        line: String = lineOf(path),
    ) = SegmentCandidate(id, sport, name, line, SegmentSource.OSM, "ODbL-1.0")

    /** Stages [candidates] for [region] in [revision] as the importer, as `putSegmentCandidates` does, and returns the rejected ids. */
    protected fun stage(vararg candidates: SegmentCandidate, region: String = "us-vermont", revision: String = "r1"): List<String> =
        runBlocking {
            SegmentCandidateViews(db, Did(importer), indexer.segmentJobs)
                .put(Did(importer), PutSegmentCandidates.Input(region, revision, candidates.toList()))
                .rejected
                .map { it.id }
        }

    /** The athletes counted as riders of the candidate [id]. */
    protected fun riders(id: String): Set<String> = runBlocking {
        db.tx {
            SegmentCandidateRiders.selectAll()
                .where { SegmentCandidateRiders.candidateId eq id }
                .map { it[SegmentCandidateRiders.authorDid] }
                .toSet()
        }
    }

    /** Whether the candidate [id] is ridden; null when it isn't staged. */
    protected fun ridden(id: String): Boolean? = runBlocking {
        db.tx { SegmentCandidateRows.selectAll().where { SegmentCandidateRows.id eq id }.firstOrNull()?.get(SegmentCandidateRows.ridden) }
    }

    /** Runs every due matcher job. */
    protected fun match() = runBlocking { matcher.runDue() }

    protected data class Effort(
        val segment: String,
        val activity: String,
        val startOffset: Long,
        val elapsed: Long,
        val visibility: String,
        val eBike: Boolean,
        val startedOn: String,
    )

    protected fun efforts(): List<Effort> = runBlocking {
        db.tx {
            SegmentEfforts.selectAll()
                .orderBy(SegmentEfforts.activityUri to SortOrder.ASC, SegmentEfforts.startOffsetS to SortOrder.ASC)
                .map {
                    Effort(
                        it[SegmentEfforts.segmentUri],
                        it[SegmentEfforts.activityUri],
                        it[SegmentEfforts.startOffsetS],
                        it[SegmentEfforts.elapsedS],
                        it[SegmentEfforts.visibility],
                        it[SegmentEfforts.eBike],
                        it[SegmentEfforts.startedOn],
                    )
                }
        }
    }

    protected fun jobs(): List<Pair<String, String>> = runBlocking {
        db.tx { SegmentMatchJobs.selectAll().map { it[SegmentMatchJobs.kind] to it[SegmentMatchJobs.subjectUri] } }
    }

    /** [query] (`k=v` pairs, unencoded values) as [viewer], or signed out. */
    protected suspend fun HttpClient.xrpc(method: XrpcQuery<*, *>, query: Map<String, String>, viewer: String? = null): HttpResponse =
        get("/xrpc/${method.nsid}?" + query.entries.joinToString("&") { (k, v) -> "$k=${v.encodeURLParameter()}" }) {
            viewer?.let { header(HttpHeaders.Authorization, "Bearer ${keys.getValue(it).serviceJwt(it, service, method.nsid)}") }
        }

    protected suspend fun <O> HttpClient.xrpcOk(method: XrpcQuery<*, O>, query: Map<String, String>, viewer: String? = null): O {
        val response = xrpc(method, query, viewer)
        val body = response.bodyAsText()
        assertEquals(HttpStatusCode.OK, response.status, body)
        return AtJson.decodeFromString(method.output, body)
    }

    protected suspend fun HttpClient.xrpcError(method: XrpcQuery<*, *>, query: Map<String, String>, viewer: String? = null): String {
        val response = xrpc(method, query, viewer)
        val body = response.bodyAsText()
        assertEquals(HttpStatusCode.BadRequest, response.status, body)
        return AtJson.parseToJsonElement(body).jsonObject["error"].toString().trim('"')
    }
}
