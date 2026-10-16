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
import io.github.solcott.activitytracker.data.activity.ActivityRepository
import io.github.solcott.activitytracker.data.activity.ActivityZonesStore
import io.github.solcott.activitytracker.data.activity.OutboxProcessor
import io.github.solcott.activitytracker.data.activity.OutboxWriter
import io.github.solcott.activitytracker.data.activity.PdsActivityStreamsStore
import io.github.solcott.activitytracker.data.activity.PdsActivityTrackStore
import io.github.solcott.activitytracker.data.activity.PrivacyZones
import io.github.solcott.activitytracker.data.activity.RouteTrimmer
import io.github.solcott.activitytracker.data.db.AppDatabase
import io.github.solcott.activitytracker.data.di.signedInTestSession
import io.github.solcott.activitytracker.data.feed.FeedRepository
import io.github.solcott.activitytracker.data.feed.LocalLikes
import io.github.solcott.activitytracker.data.feed.fakeGetComments
import io.github.solcott.activitytracker.data.profile.ProfileRepository
import io.github.solcott.activitytracker.data.sport.RecentActivityTypes
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

    /** Alice's `actor.privacyZones` record as last saved, as JSON; null for none. */
    protected var privacyZones: String? = null

    /** The activity's athlete, as JSON: Bob's unless a test makes it Alice's own. */
    protected var author = bob

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
                """"record":{"sport":"run","startedAt":"2026-09-30T10:00:00Z","elapsedTime":1800,"createdAt":"2026-09-30T10:31:00Z"},""" +
                """"likeCount":${likesFromAppView.size},"commentCount":${commentsFromAppView.count { "\"hidden\"" !in it }},"indexedAt":"2026-09-30T10:31:01Z","viewer":{}}"""

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
                            Nsid.ACTOR_PRIVACY_ZONES ->
                                privacyZones?.let {
                                    respond("""{"uri":"at://did:plc:alice/x/self","cid":"bafyrec","value":$it}""", headers = json)
                                } ?: notFound
                            else -> notFound
                        }
                    }

                    "/xrpc/com.atproto.space.getBlob" -> {
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
                        if (input.jsonObject["collection"]?.jsonPrimitive?.content == Nsid.ACTOR_PRIVACY_ZONES)
                            privacyZones = record.toString()
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

    protected class Repos(
        val db: AppDatabase,
        val feed: FeedRepository,
        val activities: ActivityRepository,
        val profiles: ProfileRepository,
        val manager: ActivityManager,
        val exporter: ActivityExporter,
    )

    protected suspend fun repos(scope: String = OAuthClientConfig.DEFAULT_SCOPE): Repos {
        val (db, _, clients) = signedInTestSession(http, did = alice, scope = scope)
        val feed = FeedRepository(db, clients, localLikes, Logger)
        val outbox = OutboxProcessor(db, clients, Logger)
        val writer = OutboxWriter(db, clients, Clock.System)
        val zones = ActivityZonesStore(db, writer, outbox, clients, Clock.System, Logger)
        val streams = PdsActivityStreamsStore(writer, clients, Clock.System, zones)
        val tracks = PdsActivityTrackStore(db, writer, clients, Clock.System)
        val privacy = PrivacyZones(clients, Clock.System, Logger)
        val activities =
            ActivityRepository(
                db,
                clients,
                outbox,
                writer,
                streams,
                tracks,
                zones,
                privacy,
                RouteTrimmer(db, clients, writer, outbox, privacy, tracks, feed, Clock.System, Logger),
                feed,
                localLikes,
                RecentActivityTypes(db, clients, feed),
                Clock.System,
            )
        return Repos(
            db,
            feed,
            activities,
            ProfileRepository(clients, Clock.System, Logger, zones),
            ActivityManager(db, clients, outbox, writer, streams, tracks, feed),
            ActivityExporter(db, clients, tracks, feed),
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
            { ActivityDetailPresenter(screen, FakeNavigator(screen), r.feed, r.activities, r.profiles, localLikes, r.manager, r.exporter) },
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
