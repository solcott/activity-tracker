package io.github.solcott.activitytracker.app

import app.cash.turbine.ReceiveTurbine
import co.touchlab.kermit.Logger
import com.slack.circuit.test.CircuitReceiveTurbine
import com.slack.circuit.test.FakeNavigator
import com.slack.circuit.test.presenterTestOf
import io.github.solcott.activitytracker.app.detail.ActivityDetailPresenter
import io.github.solcott.activitytracker.app.detail.ActivityDetailScreen
import io.github.solcott.activitytracker.app.detail.ActivityDetailState
import io.github.solcott.activitytracker.app.detail.CommentsPaging
import io.github.solcott.activitytracker.app.detail.LikesList
import io.github.solcott.activitytracker.atproto.OAuthClientConfig
import io.github.solcott.activitytracker.data.activity.ActivityExporter
import io.github.solcott.activitytracker.data.activity.ActivityManager
import io.github.solcott.activitytracker.data.activity.ActivityPlaces
import io.github.solcott.activitytracker.data.activity.ActivityRepository
import io.github.solcott.activitytracker.data.activity.ActivityZonesStore
import io.github.solcott.activitytracker.data.activity.CombineSuggestions
import io.github.solcott.activitytracker.data.activity.OutboxProcessor
import io.github.solcott.activitytracker.data.activity.OutboxWriter
import io.github.solcott.activitytracker.data.activity.PdsActivityDevicesStore
import io.github.solcott.activitytracker.data.activity.PdsActivitySourceStore
import io.github.solcott.activitytracker.data.activity.PdsActivityStreamsStore
import io.github.solcott.activitytracker.data.activity.PdsActivityTrackStore
import io.github.solcott.activitytracker.data.activity.PrivacyZones
import io.github.solcott.activitytracker.data.activity.RouteTrimmer
import io.github.solcott.activitytracker.data.db.AppDatabase
import io.github.solcott.activitytracker.data.di.signedInTestSession
import io.github.solcott.activitytracker.data.feed.FeedRepository
import io.github.solcott.activitytracker.data.feed.LocalLikes
import io.github.solcott.activitytracker.data.feed.fakeGetComments
import io.github.solcott.activitytracker.data.moderation.ReportRepository
import io.github.solcott.activitytracker.data.profile.BlockRepository
import io.github.solcott.activitytracker.data.profile.ProfileRepository
import io.github.solcott.activitytracker.data.segments.SegmentPublisher
import io.github.solcott.activitytracker.data.segments.SegmentRepository
import io.github.solcott.activitytracker.data.sport.RecentActivityTypes
import io.github.solcott.activitytracker.data.units.UnitSystem
import io.github.solcott.activitytracker.data.units.UnitsRepository
import io.github.solcott.activitytracker.lexicon.AtJson
import io.github.solcott.activitytracker.lexicon.AtUri
import io.github.solcott.activitytracker.lexicon.Did
import io.github.solcott.activitytracker.lexicon.Nsid
import io.github.solcott.activitytracker.lexicon.app.CommentView
import io.github.solcott.activitytracker.lexicon.app.GetComments
import io.github.solcott.activitytracker.location.Gpx
import io.github.solcott.activitytracker.location.LocationSample
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import io.ktor.http.headersOf
import java.io.ByteArrayOutputStream
import java.util.zip.GZIPOutputStream
import kotlin.time.Clock
import kotlinx.coroutines.CompletableDeferred
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** The top-level comments of the listed threads, leaving out placeholders. */
internal val CommentsPaging.roots: List<CommentView>
    get() = items.mapNotNull { it.root }

/** The detail screen's fakes, shared by its presenter tests: an AppView serving one activity, its likes, comments and streams. */
open class ActivityDetailTestBase {
    protected val alice = Did("did:plc:alice")
    protected val activityUri = "at://did:plc:bob/${Nsid.FEED_ACTIVITY}/3kbob1"
    protected val bob = """{"did":"did:plc:bob","handle":"bob.test","displayName":"Bob"}"""
    protected val carol = """{"did":"did:plc:carol","handle":"carol.test"}"""
    protected val staleAlice = """{"did":"did:plc:alice","handle":"alice.test"}"""
    protected var likesFromAppView = listOf(carol)
    protected var streams: String? = null
    protected var visibility = "public"

    /** The full track in Alice's `self` space for the activity's rkey; null for none. */
    protected var track: List<LocationSample>? = null

    /** The activity's original file in Alice's `self` space, as (name as picked, bytes as picked); null for none. Stored gzipped. */
    protected var original: Pair<String, ByteArray>? = null

    /** `space.getBlob` requests for the original so far. */
    protected var originalReads = 0

    /** Holds `space.getBlob` requests for the original until completed, while set. */
    protected var originalGate: CompletableDeferred<Unit>? = null

    /** Alice's `actor.privacyZones` record as last saved, as JSON; null for none. */
    protected var privacyZones: String? = null

    /** The activity's athlete, as JSON: Bob's unless a test makes it Alice's own. */
    protected var author = bob

    /** The activity's sport token. */
    protected var sport = "run"

    /** More of the activity's record, as JSON members each followed by a comma (`"poolLength":2500,`). */
    protected var recordExtra = ""

    /** More of the activity's view, as JSON members each followed by a comma (`"labels":[…],`). */
    protected var viewExtra = ""

    /** The comments the AppView lists, as JSON objects, oldest first. */
    protected var commentsFromAppView = listOf<String>()

    /** `getComments` page requests, by cursor (null for the first page). */
    protected val pageRequests = mutableListOf<String?>()

    /** `getComments` requests for a thread's replies, as (root rkey, cursor). */
    protected val replyRequests = mutableListOf<Pair<String?, String?>>()

    /** `getComments` answers InternalServerError while set. */
    protected var failComments = false

    /** `getComments` for a thread's replies answers InternalServerError while set. */
    protected var failReplies = false

    /** Holds `getComments` requests for a thread's replies until completed, while set. */
    protected var repliesGate: CompletableDeferred<Unit>? = null

    /** Every request answers InternalServerError while set. */
    protected var offline = false

    /** The `getActivitySegments` efforts the AppView answers with, as JSON; null to fail. */
    protected var segmentsFromAppView: String? = "[]"

    /** The AppView's `checkSegment` answer; null fails it. */
    protected var checkSegment: String? = """{"duplicates":[],"remaining":10}"""

    /** How many `checkSegment` calls were made; each waits on [checkGate] when set. */
    protected var checkRequests = 0
    protected var checkGate: CompletableDeferred<Unit>? = null

    /** Reading Alice's privacy zones fails. */
    protected var failZones = false

    /** `getActivityStreams` requests so far. */
    protected var streamsRequests = 0

    /** Holds `getActivityStreams` requests until completed, while set. */
    protected var streamsGate: CompletableDeferred<Unit>? = null

    /** Holds `getComments` pages (not lookups) until completed, while set. */
    protected var commentsGate: CompletableDeferred<Unit>? = null

    /** A comment's view as JSON; with [root], a reply in that comment's thread, to [parent]. */
    @Suppress("LongParameterList") // The view's fields.
    protected fun comment(
        author: String,
        did: String,
        rkey: String,
        hidden: Boolean = false,
        audience: String? = null,
        root: String? = null,
        parent: String? = root,
    ) =
        """{"uri":"at://$did/${Nsid.FEED_COMMENT}/$rkey","cid":"bafy$rkey","author":$author,"text":"Nice",""" +
            (if (hidden) """"hidden":true,""" else "") +
            audience?.let { """"audience":"$it",""" }.orEmpty() +
            root?.let { """"root":${strongRef(it)},"parent":${strongRef(parent!!)},""" }.orEmpty() +
            """"createdAt":"2026-09-30T11:00:00Z"}"""

    /** The URI of [did]'s comment [rkey]. */
    protected fun commentUri(did: String, rkey: String) = "at://$did/${Nsid.FEED_COMMENT}/$rkey"

    private fun strongRef(uri: String) = """{"uri":"$uri","cid":"bafy${uri.substringAfterLast('/')}"}"""

    protected val activity
        get() =
            """{"uri":"$activityUri","cid":"bafy3kbob1","author":$author,"visibility":"$visibility",""" +
                """"record":{"sport":"$sport",$recordExtra"startedAt":"2026-09-30T10:00:00Z",""" +
                """"elapsedTime":1800,"createdAt":"2026-09-30T10:31:00Z"},""" +
                """"likeCount":${likesFromAppView.size},"commentCount":${commentsFromAppView.count { "\"hidden\"" !in it }},"indexedAt":"2026-09-30T10:31:01Z",$viewExtra"viewer":{}}"""

    protected val http =
        HttpClient(
            MockEngine { request ->
                val json = headersOf("Content-Type", "application/json")
                if (offline) return@MockEngine respond("""{"error":"InternalServerError"}""", HttpStatusCode.InternalServerError, json)
                when (request.url.encodedPath) {
                    "/xrpc/com.atproto.server.getServiceAuth" -> {
                        respond("""{"token":"service-jwt"}""", headers = json)
                    }

                    "/xrpc/${Nsid.FEED_GET_ACTIVITY}" -> {
                        respond("""{"activity":$activity,"comments":[]}""", headers = json)
                    }

                    "/xrpc/${Nsid.FEED_GET_COMMENTS}" -> {
                        val params = request.url.parameters
                        val uris = params.getAll("uris")
                        val root = params["root"]
                        when {
                            root != null -> {
                                replyRequests += root.substringAfterLast('/') to params["cursor"]
                                repliesGate?.await()
                            }
                            uris == null -> {
                                pageRequests += params["cursor"]
                                commentsGate?.await()
                            }
                        }
                        if (failComments || (root != null && failReplies))
                            return@MockEngine respond("""{"error":"InternalServerError"}""", HttpStatusCode.InternalServerError, json)
                        val all = commentsFromAppView.map { AtJson.decodeFromString(CommentView.serializer(), it) }
                        val page = fakeGetComments(AtUri(activityUri), all, uris, root, params["cursor"], params["limit"]?.toInt())
                        respond(AtJson.encodeToString(GetComments.Output.serializer(), page), headers = json)
                    }

                    "/xrpc/${Nsid.FEED_GET_ACTIVITY_STREAMS}" -> {
                        streamsRequests++
                        streamsGate?.await()
                        streams?.let { respond(it, headers = json) }
                            ?: respond("""{"error":"NotFound","message":"none"}""", HttpStatusCode.NotFound, json)
                    }

                    "/xrpc/${Nsid.FEED_CHECK_SEGMENT}" -> {
                        checkRequests++
                        checkGate?.await()
                        checkSegment?.let { respond(it, headers = json) }
                            ?: respond("""{"error":"InternalServerError"}""", HttpStatusCode.InternalServerError, json)
                    }

                    "/xrpc/${Nsid.FEED_GET_ACTIVITY_SEGMENTS}" -> {
                        segmentsFromAppView?.let { respond("""{"efforts":$it}""", headers = json) }
                            ?: respond("""{"error":"InternalServerError"}""", HttpStatusCode.InternalServerError, json)
                    }

                    "/xrpc/${Nsid.FEED_GET_LIKES}" -> {
                        respond("""{"uri":"$activityUri","likes":[${likesFromAppView.joinToString(",")}]}""", headers = json)
                    }

                    "/xrpc/com.atproto.space.getRecord" -> {
                        val notFound = respond("""{"error":"RecordNotFound"}""", HttpStatusCode.BadRequest, json)
                        when (request.url.parameters["collection"]) {
                            Nsid.FEED_ACTIVITY_TRACK ->
                                if (track == null) notFound
                                else {
                                    val blob =
                                        $$"""{"$type":"blob","ref":{"$link":"bafytrack"},"mimeType":"application/gpx+xml","size":10}"""
                                    val value = """{"activity":"$activityUri","createdAt":"2026-09-30T10:31:00Z","track":$blob}"""
                                    respond("""{"uri":"at://did:plc:alice/x/y","cid":"bafyrec","value":$value}""", headers = json)
                                }
                            Nsid.FEED_ACTIVITY_SOURCE ->
                                original?.let { (name, bytes) ->
                                    val blob = $$"""{"$type":"blob","ref":{"$link":"bafysource"},"mimeType":"application/gzip","size":10}"""
                                    val value =
                                        """{"activity":"$activityUri","format":"fit","fileName":"$name","size":${bytes.size},""" +
                                            """"compressed":true,"createdAt":"2026-09-30T10:31:00Z","file":$blob}"""
                                    respond("""{"uri":"at://did:plc:alice/x/y","cid":"bafyrec","value":$value}""", headers = json)
                                } ?: notFound
                            Nsid.ACTOR_PRIVACY_ZONES ->
                                if (failZones) respond("""{"error":"InternalServerError"}""", HttpStatusCode.InternalServerError, json)
                                else
                                    privacyZones?.let {
                                        respond("""{"uri":"at://did:plc:alice/x/self","cid":"bafyrec","value":$it}""", headers = json)
                                    } ?: notFound
                            else -> notFound
                        }
                    }

                    "/xrpc/com.atproto.space.getBlob" -> {
                        val source = original
                        if (request.url.parameters["cid"] == "bafysource" && source != null) {
                            originalReads++
                            originalGate?.await()
                            val gzipped = ByteArrayOutputStream().also { out -> GZIPOutputStream(out).use { it.write(source.second) } }
                            return@MockEngine respond(gzipped.toByteArray(), headers = headersOf("Content-Type", "application/gzip"))
                        }
                        track?.let { respond(Gpx.write("Run", "run", it), headers = headersOf("Content-Type", "application/gpx+xml")) }
                            ?: respond("""{"error":"BlobNotFound"}""", HttpStatusCode.BadRequest, json)
                    }

                    "/.well-known/did.json" -> {
                        respond(
                            """{"id":"did:web:appview.test","service":[
                               {"id":"#activitytracker_space","type":"AtprotoSpaceService","serviceEndpoint":"http://appview.test"}]}""",
                            headers = json,
                        )
                    }

                    "/xrpc/com.atproto.simplespace.createSpace" -> {
                        respond("""{"uri":"at://did:plc:alice/space/${Nsid.SPACE_ATHLETE}/self"}""", headers = json)
                    }

                    "/xrpc/${Nsid.SPACE_NOTIFY_SPACE}" -> {
                        respond("{}", headers = json)
                    }

                    "/xrpc/com.atproto.space.putRecord" -> {
                        val input = AtJson.parseToJsonElement((request.body as OutgoingContent.ByteArrayContent).bytes().decodeToString())
                        val record = input.jsonObject["record"]
                        when (input.jsonObject["collection"]?.jsonPrimitive?.content) {
                            Nsid.ACTOR_PRIVACY_ZONES -> privacyZones = record.toString()
                            // Refused, as repo writes are, so they stay queued for a test to read: an outbox drain that sent one would
                            // delete it from under the test.
                            Nsid.FEED_COMMENT,
                            Nsid.GRAPH_BLOCK,
                            Nsid.GRAPH_MUTE ->
                                return@MockEngine respond("""{"error":"Unavailable"}""", HttpStatusCode.ServiceUnavailable, json)
                        }
                        respond("""{"uri":"at://did:plc:alice/x/y","cid":"bafyrec"}""", headers = json)
                    }

                    "/xrpc/${Nsid.ACTOR_GET_PROFILE}" -> {
                        respond("""{"did":"did:plc:alice","handle":"alice.test","displayName":"Alice"}""", headers = json)
                    }

                    else -> {
                        respond("", HttpStatusCode.NotFound)
                    }
                }
            }
        ) {
            expectSuccess = false
        }

    protected val localLikes = LocalLikes()

    /** The labeler reports go to, so Report is offered. */
    protected val labeler = Did("did:plc:labeler")

    @Suppress("LongParameterList") // One per repository a detail test reaches for.
    protected class Repos(
        val db: AppDatabase,
        val feed: FeedRepository,
        val activities: ActivityRepository,
        val profiles: ProfileRepository,
        val manager: ActivityManager,
        val exporter: ActivityExporter,
        val units: UnitsRepository,
        val suggestions: CombineSuggestions,
        val blocks: BlockRepository,
        val reports: ReportRepository,
        val segments: SegmentRepository,
        val publisher: SegmentPublisher,
    )

    @Suppress("LongMethod") // Wires every store a test reaches for.
    protected suspend fun repos(scope: String = OAuthClientConfig.DEFAULT_SCOPE, units: UnitSystem = UnitSystem.Metric): Repos {
        val (db, _, clients) = signedInTestSession(http, did = alice, scope = scope, labelerDid = labeler)
        val feed = FeedRepository(db, clients, localLikes, Logger)
        val outbox = OutboxProcessor(db, clients, Logger)
        val writer = OutboxWriter(db, clients, Clock.System)
        val zones = ActivityZonesStore(db, writer, outbox, clients, Clock.System, Logger)
        val streams = PdsActivityStreamsStore(writer, clients, Clock.System, zones)
        val tracks = PdsActivityTrackStore(db, writer, clients, Clock.System)
        val sources = PdsActivitySourceStore(db, writer, clients, Clock.System)
        val devices = PdsActivityDevicesStore(db, writer, clients, Clock.System)
        val privacy = PrivacyZones(clients, Clock.System, Logger)
        val activities =
            ActivityRepository(
                db,
                clients,
                outbox,
                writer,
                streams,
                tracks,
                sources,
                devices,
                zones,
                ActivityPlaces(clients, Clock.System, Logger),
                privacy,
                RouteTrimmer(db, clients, writer, outbox, privacy, tracks, feed, Clock.System, Logger),
                feed,
                localLikes,
                RecentActivityTypes(db, clients, feed),
                Clock.System,
                testSegments(db, clients, writer, outbox, privacy, tracks).tracks,
                testSegments(db, clients, writer, outbox, privacy, tracks).sweeper,
            )
        val profiles = ProfileRepository(clients, Clock.System, Logger, zones)
        val segments = SegmentRepository(clients)
        return Repos(
            db,
            feed,
            activities,
            profiles,
            ActivityManager(
                db,
                clients,
                outbox,
                writer,
                streams,
                tracks,
                sources,
                devices,
                zones,
                ActivityPlaces(clients, Clock.System, Logger),
                feed,
                privacy,
                Clock.System,
                activities,
                testSegments(db, clients, writer, outbox, privacy, tracks).tracks,
            ),
            ActivityExporter(db, clients, tracks, sources, feed),
            UnitsRepository(profiles, Clock.System, Logger, units),
            CombineSuggestions(db, clients, feed, tracks, Clock.System),
            BlockRepository(clients, writer, outbox, feed, Clock.System),
            ReportRepository(clients),
            segments,
            SegmentPublisher(clients, writer, outbox, segments, Clock.System),
        )
    }

    protected val screen = ActivityDetailScreen(activityUri)

    /** The comments Alice has queued, as (space key or null for the repo, record). */
    protected suspend fun Repos.queuedComments() =
        db.outbox()
            .pending(alice.value)
            .filter { it.collection == Nsid.FEED_COMMENT }
            .map { it.space?.substringAfterLast('/') to it.recordJson }

    /** [count] of carol's comments, `3kc000`…, oldest first, with Alice's own at [own] when set. */
    protected fun manyComments(count: Int, own: Int = -1) =
        (0 until count).map { i ->
            val rkey = "3kc" + i.toString().padStart(3, '0')
            if (i == own) comment(staleAlice, alice.value, rkey) else comment(carol, "did:plc:carol", rkey)
        }

    protected fun ActivityDetailState.loaded() = this as? ActivityDetailState.Loaded

    protected suspend fun detail(r: Repos, block: suspend CircuitReceiveTurbine<ActivityDetailState>.() -> Unit) =
        presenterTestOf(
            {
                ActivityDetailPresenter(
                    screen,
                    FakeNavigator(screen),
                    r.feed,
                    r.activities,
                    r.profiles,
                    localLikes,
                    r.manager,
                    r.exporter,
                    r.units,
                    r.suggestions,
                    r.blocks,
                    r.reports,
                    r.segments,
                )
            },
            block = block,
        )

    protected suspend fun ReceiveTurbine<ActivityDetailState>.awaitLoaded() =
        awaitUntil { it is ActivityDetailState.Loaded } as ActivityDetailState.Loaded

    protected suspend fun ReceiveTurbine<ActivityDetailState>.awaitLikes(done: (LikesList) -> Boolean): ActivityDetailState.Loaded {
        var state = awaitLoaded()
        while (state.likesList?.let { !it.isLoading && done(it) } != true) state = awaitLoaded()
        return state
    }

    protected companion object {
        /** The AppView's page size for `getComments` when none is asked for. */
        const val PAGE = 30
    }
}
