package io.github.solcott.activitytracker.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import app.cash.turbine.ReceiveTurbine
import co.touchlab.kermit.Logger
import com.slack.circuit.test.FakeNavigator
import io.github.solcott.activitytracker.app.detail.ActivityDetailPresenter
import io.github.solcott.activitytracker.app.detail.ActivityDetailScreen
import io.github.solcott.activitytracker.app.detail.ActivityDetailState
import io.github.solcott.activitytracker.app.detail.CombinePickerPresenter
import io.github.solcott.activitytracker.app.detail.CombinePickerScreen
import io.github.solcott.activitytracker.app.detail.CombinePickerState
import io.github.solcott.activitytracker.data.activity.ActivityExporter
import io.github.solcott.activitytracker.data.activity.ActivityManager
import io.github.solcott.activitytracker.data.activity.ActivityPlaces
import io.github.solcott.activitytracker.data.activity.ActivityRepository
import io.github.solcott.activitytracker.data.activity.CombineSuggestions
import io.github.solcott.activitytracker.data.activity.DataKind
import io.github.solcott.activitytracker.data.activity.PrivacyZones
import io.github.solcott.activitytracker.data.activity.RouteTrimmer
import io.github.solcott.activitytracker.data.di.signedInTestSession
import io.github.solcott.activitytracker.data.feed.FeedRepository
import io.github.solcott.activitytracker.data.feed.LocalLikes
import io.github.solcott.activitytracker.data.moderation.ReportRepository
import io.github.solcott.activitytracker.data.profile.BlockRepository
import io.github.solcott.activitytracker.data.profile.ProfileRepository
import io.github.solcott.activitytracker.data.segments.SegmentRepository
import io.github.solcott.activitytracker.data.sport.RecentActivityTypes
import io.github.solcott.activitytracker.data.units.UnitSystem
import io.github.solcott.activitytracker.data.units.UnitsRepository
import io.github.solcott.activitytracker.lexicon.AtJson
import io.github.solcott.activitytracker.lexicon.AtUri
import io.github.solcott.activitytracker.lexicon.Did
import io.github.solcott.activitytracker.lexicon.Nsid
import io.github.solcott.activitytracker.lexicon.app.ActivityRecord
import io.github.solcott.activitytracker.lexicon.app.ActivityStreamData
import io.github.solcott.activitytracker.lexicon.app.ActivityStreamsRecord
import io.github.solcott.activitytracker.lexicon.app.GetActivityStreams
import io.github.solcott.activitytracker.lexicon.app.Visibility
import io.github.solcott.activitytracker.lexicon.atproto.BlobRef
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import io.ktor.http.headersOf
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.coroutines.CompletableDeferred
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** The fakes shared by the combine tests: Alice's repo, the AppView serving it, and a session graph built on them. */
open class CombineTestBase {
    protected val alice = Did("did:plc:alice")
    protected val aliceProfile = """{"did":"did:plc:alice","handle":"alice.test"}"""

    protected fun uri(rkey: String) = "at://${alice.value}/${Nsid.FEED_ACTIVITY}/$rkey"

    /** Alice's repo: `rkey → feed.activity`, as the PDS holds it and the AppView serves it. */
    protected val records = mutableMapOf<String, JsonObject>()

    /** Every write the PDS took, in order, as "put|delete collection/rkey". */
    protected val writes = mutableListOf<String>()

    /** Holds each `putRecord` until completed. */
    protected var gate = CompletableDeferred(Unit)
    protected var failReads = false
    protected var failByStart = false
    protected var byStartQueries = 0

    /** Completed once the AppView has been asked for activities by start. */
    protected var byStartAsked = CompletableDeferred<Unit>()

    /** Suggestions look at the last week: a day after the activities here. */
    protected val suggestionClock =
        object : Clock {
            override fun now() = Instant.parse("2026-10-15T00:00:00Z")
        }

    /** Alice's `feed.activityStreams` records, by rkey, and the blobs they name, by CID. */
    protected val streamsRecords = mutableMapOf<String, JsonObject>()
    protected val blobs = mutableMapOf<String, ByteArray>()

    /**
     * The streams the AppView serves, by rkey: set by the test alone, so they lag what the PDS holds, as the real one does until Jetstream
     * brings it a rebuild.
     */
    protected val servedStreams = mutableMapOf<String, ActivityStreamData>()

    /** Activities the AppView serves as Bob's, not Alice's own. */
    protected val bobs = mutableSetOf<String>()

    /** Stores [data] as [rkey]'s streams, in the repo. */
    protected fun streams(rkey: String, data: ActivityStreamData) {
        val bytes = AtJson.encodeToString(ActivityStreamData.serializer(), data).encodeToByteArray()
        val cid = "bafyblob${blobs.size}"
        blobs[cid] = bytes
        val record =
            ActivityStreamsRecord(
                activity = AtUri(uri(rkey)),
                channels = data.channels,
                createdAt = Clock.System.now(),
                data = BlobRef(ref = BlobRef.Link(cid), mimeType = ActivityStreamsRecord.MIME_TYPE, size = bytes.size.toLong()),
            )
        streamsRecords[rkey] = AtJson.encodeToJsonElement(ActivityStreamsRecord.serializer(), record).jsonObject
    }

    protected fun streamsOf(rkey: String): ActivityStreamData? {
        val cid = streamsRecords[rkey]?.let { AtJson.decodeFromJsonElement(ActivityStreamsRecord.serializer(), it) }?.data?.ref?.link
        return cid?.let { AtJson.decodeFromString(ActivityStreamData.serializer(), blobs.getValue(it).decodeToString()) }
    }

    protected fun ride(start: String, minutes: Int = 60, members: List<String> = emptyList()): JsonObject {
        val combined =
            members
                .takeIf { it.isNotEmpty() }
                ?.joinToString(",", prefix = ""","combined":{"members":[""", postfix = """],"sources":[]}""") { "\"${uri(it)}\"" }
                .orEmpty()
        return AtJson.parseToJsonElement(
                """{"sport":"ride","startedAt":"$start","elapsedTime":${minutes * 60},"createdAt":"$start"$combined}"""
            )
            .jsonObject
    }

    /** The AppView's view of [rkey]: its members, and the combined activity it's part of. */
    protected fun view(rkey: String): String {
        val record = records.getValue(rkey)
        val combinedInto =
            records.entries.firstOrNull { (_, r) -> uri(rkey) in r.membersOf() }?.let { (key, _) -> ""","combinedInto":"${uri(key)}"""" }
        val members =
            record
                .membersOf()
                .takeIf { it.isNotEmpty() }
                ?.joinToString(",", prefix = ""","members":[""", postfix = "]") { m ->
                    val r = AtJson.decodeFromJsonElement(ActivityRecord.serializer(), records.getValue(m.substringAfterLast('/')))
                    """{"uri":"$m","startedAt":"${r.startedAt}","elapsedTime":${r.elapsedTime}}"""
                }
        val author = if (rkey in bobs) """{"did":"did:plc:bob","handle":"bob.test"}""" else aliceProfile
        return """{"uri":"${uri(rkey)}","cid":"bafy$rkey","author":$author,"record":$record,""" +
            """"likeCount":0,"commentCount":0,"indexedAt":"2026-10-14T12:00:00Z","viewer":{}${combinedInto.orEmpty()}${members.orEmpty()}}"""
    }

    protected fun JsonObject.membersOf(): List<String> =
        AtJson.decodeFromJsonElement(ActivityRecord.serializer(), this).combined?.members.orEmpty().map { it.value }

    protected val http =
        HttpClient(
            MockEngine { request ->
                val json = headersOf("Content-Type", "application/json")
                fun notFound() = respond("""{"error":"NotFound"}""", HttpStatusCode.BadRequest, json)
                when (request.url.encodedPath) {
                    "/xrpc/com.atproto.server.getServiceAuth" -> respond("""{"token":"service-jwt"}""", headers = json)

                    "/xrpc/${Nsid.FEED_GET_ACTIVITY}" -> {
                        val rkey = request.url.parameters["uri"]!!.substringAfterLast('/')
                        if (rkey in records) respond("""{"activity":${view(rkey)},"comments":[]}""", headers = json) else notFound()
                    }

                    "/xrpc/${Nsid.FEED_GET_ACTIVITY_STREAMS}" -> {
                        val uri = AtUri(request.url.parameters["uri"]!!)
                        val data = servedStreams[uri.rkey] ?: return@MockEngine notFound()
                        val output = GetActivityStreams.Output(uri, Visibility.PUBLIC, data.channels, data)
                        respond(AtJson.encodeToString(GetActivityStreams.Output.serializer(), output), headers = json)
                    }

                    "/xrpc/${Nsid.FEED_GET_AUTHOR_ACTIVITIES_BY_START}" -> {
                        byStartQueries++
                        byStartAsked.complete(Unit)
                        if (failByStart) {
                            respond("""{"error":"InternalServerError"}""", HttpStatusCode.InternalServerError, json)
                        } else {
                            respond("""{"activities":[${records.keys.sorted().joinToString(",") { view(it) }}]}""", headers = json)
                        }
                    }

                    "/xrpc/com.atproto.repo.getRecord" -> {
                        if (failReads)
                            return@MockEngine respond("""{"error":"InternalServerError"}""", HttpStatusCode.InternalServerError, json)
                        val rkey = request.url.parameters["rkey"]!!
                        val record =
                            when (request.url.parameters["collection"]) {
                                Nsid.FEED_ACTIVITY -> records[rkey]
                                Nsid.FEED_ACTIVITY_STREAMS -> streamsRecords[rkey]
                                else -> null
                            }
                        if (record == null) respond("""{"error":"RecordNotFound"}""", HttpStatusCode.BadRequest, json)
                        else respond("""{"uri":"${uri(rkey)}","cid":"bafy$rkey","value":$record}""", headers = json)
                    }

                    "/xrpc/com.atproto.repo.putRecord" -> {
                        gate.await()
                        val body =
                            AtJson.parseToJsonElement((request.body as OutgoingContent.ByteArrayContent).bytes().decodeToString())
                                .jsonObject
                        val collection = body.getValue("collection").jsonPrimitive.content
                        val rkey = body.getValue("rkey").jsonPrimitive.content
                        writes += "put $collection/$rkey"
                        if (collection == Nsid.FEED_ACTIVITY) records[rkey] = body.getValue("record").jsonObject
                        if (collection == Nsid.FEED_ACTIVITY_STREAMS) streamsRecords[rkey] = body.getValue("record").jsonObject
                        respond("""{"uri":"${uri(rkey)}","cid":"bafyrec"}""", headers = json)
                    }

                    "/xrpc/com.atproto.repo.uploadBlob" -> {
                        val bytes = (request.body as OutgoingContent.ByteArrayContent).bytes()
                        val cid = "bafyblob${blobs.size}"
                        blobs[cid] = bytes
                        respond(
                            """{"blob":{"${'$'}type":"blob","ref":{"${'$'}link":"$cid"},""" +
                                """"mimeType":"application/json","size":${bytes.size}}}""",
                            headers = json,
                        )
                    }

                    "/xrpc/com.atproto.sync.getBlob" -> {
                        if (failReads)
                            return@MockEngine respond("""{"error":"InternalServerError"}""", HttpStatusCode.InternalServerError, json)
                        blobs[request.url.parameters["cid"]]?.let { respond(it, headers = json) } ?: notFound()
                    }

                    "/xrpc/com.atproto.repo.deleteRecord" -> {
                        val body =
                            AtJson.parseToJsonElement((request.body as OutgoingContent.ByteArrayContent).bytes().decodeToString())
                                .jsonObject
                        val collection = body.getValue("collection").jsonPrimitive.content
                        val rkey = body.getValue("rkey").jsonPrimitive.content
                        writes += "delete $collection/$rkey"
                        if (collection == Nsid.FEED_ACTIVITY) records -= rkey
                        if (collection == Nsid.FEED_ACTIVITY_STREAMS) streamsRecords -= rkey
                        respond("{}", headers = json)
                    }

                    else -> notFound()
                }
            }
        ) {
            expectSuccess = false
        }

    protected val localLikes = LocalLikes()

    @Suppress("LongParameterList") // One per repository the presenters under test need.
    protected class Graph(
        val feed: FeedRepository,
        val activities: ActivityRepository,
        val profiles: ProfileRepository,
        val manager: ActivityManager,
        val exporter: ActivityExporter,
        val suggestions: CombineSuggestions,
        val blocks: BlockRepository,
        val reports: ReportRepository,
        val segments: SegmentRepository,
    )

    /** A session that can write to the repo only, so the PDS here is just Alice's repo. */
    protected suspend fun graph(): Graph {
        val (db, _, clients) = signedInTestSession(http, did = alice, scope = "atproto transition:generic")
        val feed = FeedRepository(db, clients, localLikes, Logger)
        val zones = TestZones(db, clients)
        val (outbox, writer, streams) = Triple(zones.outbox, zones.writer, zones.streams)
        val tracks = zones.tracks
        val privacy = PrivacyZones(clients, Clock.System, Logger)
        val places = ActivityPlaces(clients, Clock.System, Logger)
        val activities =
            ActivityRepository(
                db,
                clients,
                outbox,
                writer,
                streams,
                tracks,
                zones.sources,
                zones.devices,
                zones.store,
                places,
                privacy,
                RouteTrimmer(db, clients, writer, outbox, privacy, tracks, feed, Clock.System, Logger),
                feed,
                localLikes,
                RecentActivityTypes(db, clients, feed),
                Clock.System,
                testSegments(db, clients, writer, outbox, privacy, tracks).tracks,
                testSegments(db, clients, writer, outbox, privacy, tracks).sweeper,
            )
        val manager =
            ActivityManager(
                db,
                clients,
                outbox,
                writer,
                streams,
                tracks,
                zones.sources,
                zones.devices,
                zones.store,
                places,
                feed,
                privacy,
                Clock.System,
                activities,
                testSegments(db, clients, writer, outbox, privacy, tracks).tracks,
            )
        return Graph(
            feed,
            activities,
            ProfileRepository(clients, Clock.System, Logger, zones.store),
            manager,
            ActivityExporter(db, clients, tracks, zones.sources, feed),
            CombineSuggestions(db, clients, feed, tracks, suggestionClock),
            BlockRepository(clients, writer, outbox, feed, Clock.System),
            ReportRepository(clients),
            SegmentRepository(clients),
        )
    }

    protected suspend fun picker(uri: String, navigator: FakeNavigator): @Composable () -> CombinePickerState {
        val g = graph()
        return { CombinePickerPresenter(CombinePickerScreen(uri), navigator, g.feed, g.manager) }
    }

    protected suspend fun detail(
        uri: String,
        navigator: FakeNavigator,
        suggestOnOpen: Boolean = false,
        shared: Graph? = null,
    ): @Composable () -> ActivityDetailState {
        val g = shared ?: graph()
        val units = UnitsRepository(g.profiles, Clock.System, Logger, UnitSystem.Metric)
        return {
            ActivityDetailPresenter(
                ActivityDetailScreen(uri, suggestOnOpen),
                navigator,
                g.feed,
                g.activities,
                g.profiles,
                localLikes,
                g.manager,
                g.exporter,
                units,
                g.suggestions,
                g.blocks,
                g.reports,
                g.segments,
            )
        }
    }

    protected fun day() {
        records["3kwatch"] = ride("2026-10-14T09:00:00Z")
        records["3kedge"] = ride("2026-10-14T08:58:00Z", minutes = 63)
        records["3kevening"] = ride("2026-10-14T18:00:00Z")
        records["3kother"] = ride("2026-10-14T06:00:00Z")
        records["3kday"] = ride("2026-10-14T06:00:00Z", members = listOf("3kother", "3kmorning"))
        records["3kmorning"] = ride("2026-10-14T06:30:00Z")
    }

    /** A watch with heart rate and a bike computer with heart rate and power, combined as 3kride. */
    protected fun ride() {
        val time = (0..3600 step 2).toList()
        records["3kwatch"] = ride("2026-10-14T09:00:00Z")
        records["3kedge"] = ride("2026-10-14T09:00:00Z")
        streams("3kwatch", ActivityStreamData(time = time, heartRate = time.map { 150 }))
        streams("3kedge", ActivityStreamData(time = time, heartRate = time.map { 140 }, power = time.map { 250 }))
        records["3kride"] = ride("2026-10-14T09:00:00Z", members = listOf("3kwatch", "3kedge"))
    }

    protected suspend fun ReceiveTurbine<ActivityDetailState>.awaitSources() =
        (awaitUntil { it.page()?.dataSources?.kinds?.isNotEmpty() == true }.page()!!)

    /** A morning ride and, after lunch, an afternoon one: the afternoon was just saved. */
    protected fun lunch() {
        records["3kam"] = ride("2026-10-14T09:00:00Z", minutes = 165)
        records["3kpm"] = ride("2026-10-14T13:00:00Z", minutes = 120)
    }

    protected fun ActivityDetailState.suggestion() = page()?.combineSuggestion

    protected fun ActivityDetailState.rows() = page()?.dataSources?.kinds

    protected fun ActivityDetailState.powerRow() = rows()?.firstOrNull { it.kind == DataKind.Power }

    protected fun ActivityDetailState.heartRateRow() = rows()?.firstOrNull { it.kind == DataKind.HeartRate }

    protected fun ActivityDetailState.saving() = page()?.dataSources?.saving

    protected suspend fun ReceiveTurbine<ActivityDetailState>.awaitLoaded() = awaitUntil { it.page() != null }.page()!!
}
