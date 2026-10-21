package io.github.solcott.activitytracker.data

import co.touchlab.kermit.Logger
import io.github.solcott.activitytracker.data.activity.ActivityDefaults
import io.github.solcott.activitytracker.data.activity.ActivityExporter
import io.github.solcott.activitytracker.data.activity.ActivityManager
import io.github.solcott.activitytracker.data.activity.ActivityPlaces
import io.github.solcott.activitytracker.data.activity.ActivityRepository
import io.github.solcott.activitytracker.data.activity.ActivityZonesStore
import io.github.solcott.activitytracker.data.activity.OutboxProcessor
import io.github.solcott.activitytracker.data.activity.OutboxWriter
import io.github.solcott.activitytracker.data.activity.PdsActivityDevicesStore
import io.github.solcott.activitytracker.data.activity.PdsActivitySourceStore
import io.github.solcott.activitytracker.data.activity.PdsActivityStreamsStore
import io.github.solcott.activitytracker.data.activity.PdsActivityTrackStore
import io.github.solcott.activitytracker.data.activity.PrivacyZones
import io.github.solcott.activitytracker.data.activity.RouteTrimmer
import io.github.solcott.activitytracker.data.db.AppDatabase
import io.github.solcott.activitytracker.data.db.FeedItemEntity
import io.github.solcott.activitytracker.data.di.SessionClients
import io.github.solcott.activitytracker.data.di.signedInTestSession
import io.github.solcott.activitytracker.data.feed.FeedRepository
import io.github.solcott.activitytracker.data.feed.LocalLikes
import io.github.solcott.activitytracker.data.feed.fakeGetComments
import io.github.solcott.activitytracker.data.profile.BlockRepository
import io.github.solcott.activitytracker.data.sport.RecentActivityTypes
import io.github.solcott.activitytracker.lexicon.AtJson
import io.github.solcott.activitytracker.lexicon.AtUri
import io.github.solcott.activitytracker.lexicon.Did
import io.github.solcott.activitytracker.lexicon.Nsid
import io.github.solcott.activitytracker.lexicon.app.ActivityStreamData
import io.github.solcott.activitytracker.lexicon.app.ActivityView
import io.github.solcott.activitytracker.lexicon.app.GetActivity
import io.github.solcott.activitytracker.lexicon.app.GetActivityStreams
import io.github.solcott.activitytracker.lexicon.app.GetAuthorActivities
import io.github.solcott.activitytracker.lexicon.app.GetBlocks
import io.github.solcott.activitytracker.lexicon.app.GetComments
import io.github.solcott.activitytracker.lexicon.app.GetMutes
import io.github.solcott.activitytracker.lexicon.app.ProfileView
import io.github.solcott.activitytracker.lexicon.app.ProfileViewBasic
import io.github.solcott.activitytracker.lexicon.app.TrainingLoad
import io.github.solcott.activitytracker.lexicon.app.Visibility
import io.github.solcott.activitytracker.lexicon.app.mergeStreams
import io.github.solcott.activitytracker.lexicon.atproto.SpaceErrors
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import io.ktor.http.headersOf
import kotlin.time.Clock
import kotlinx.io.IOException
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * A fake PDS and AppView for [did]'s session: her repo and each of her spaces as `place → collection/rkey → record`, every write in order,
 * and the AppView's answers. Shared by the data-layer tests of where records are stored.
 */
class FakeAthletePds(val did: Did = Did("did:plc:alice")) {
    val repo = "repo"

    /** Every place's records: [repo] or a tier, then `collection/rkey`. */
    val stored = mutableMapOf<String, MutableMap<String, JsonObject>>()

    /** Every write in order, as `"put|delete <place> <collection>/<rkey>"`. */
    val writes = mutableListOf<String>()

    /** Every request's method and, for a record read, `collection/rkey`: `"<path> <collection>/<rkey>"`. */
    val requests = mutableListOf<String>()
    val announced = mutableListOf<String>()
    val created = mutableListOf<String>()

    /** Fail space deletes, or space puts, while set; and every request to the PDS while [offline]. */
    var failSpaceDeletes = false
    var failSpacePuts = false

    /** Fail every put of this collection, wherever it goes, while set. */
    var failPutsOf: String? = null
    var offline = false

    /** Every request to the AppView fails as a dropped connection would, while set. */
    var appViewOffline = false

    /**
     * What the AppView serves: `getActivity` the activity, `getComments` its comments, oldest first, as threads (in pages of the asked
     * limit of threads, each with its first three replies; a `root`'s replies a page at a time; or those of the asked `uris`); NotFound
     * while null. A comment naming a root is a reply in that thread, and a thread with no top-level comment listed is a placeholder.
     */
    var served: GetActivity.Output? = null

    /** What the AppView's `getProfile` serves, by DID; `ProfileNotFound` for any other. */
    val profiles = mutableMapOf<String, ProfileView>()

    /** What the AppView's `getBlocks` and `getMutes` serve, in one page. */
    var blocks: List<GetBlocks.BlockView> = emptyList()
    var mutes: List<ProfileViewBasic> = emptyList()

    /** What the AppView's `getAuthorActivities` serves, in one page; an error while null. */
    var authorActivities: List<ActivityView>? = emptyList()

    /** Fail every read of this `collection/rkey`, while set. */
    var failReadsOf: String? = null

    /** Called with each record put, as `collection/rkey`, after it's stored. */
    var onPut: ((key: String, record: JsonObject) -> Unit)? = null

    /** The `uris` of each `getComments` lookup, in order. */
    val lookups = mutableListOf<List<String>>()

    /** The charts visibility the AppView serves for each activity rkey; absent for none. */
    val streamsVisibility = mutableMapOf<String, String>()

    /** The streams the AppView serves for each activity rkey in [streamsVisibility]; a single sample at 0 when absent. */
    val streamsData = mutableMapOf<String, ActivityStreamData>()

    /** The training load the AppView serves with each activity rkey's streams; absent for none. */
    val streamsLoad = mutableMapOf<String, TrainingLoad>()

    /** Fail every `getActivityStreams` while set. */
    var failStreams = false

    /** Uploaded blobs by CID. */
    val blobs = mutableMapOf<String, ByteArray>()

    /** How many `uploadBlob` calls there were. */
    var uploads = 0

    /** Refuse uploads larger than this many bytes with `413 PayloadTooLarge`, while set. */
    var blobUploadLimit: Int? = null

    val http =
        HttpClient(
            MockEngine { request ->
                if (offline && request.url.host == "pds.test")
                    return@MockEngine error("InternalServerError", HttpStatusCode.InternalServerError)
                if (appViewOffline && request.url.host != "pds.test") throw IOException("Connection refused")
                val path = request.url.encodedPath
                requests += "$path ${request.url.parameters["collection"]}/${request.url.parameters["rkey"]}"
                when {
                    path == "/xrpc/com.atproto.server.getServiceAuth" -> json("""{"token":"service-jwt"}""")
                    path == "/.well-known/did.json" ->
                        json(
                            """{"id":"did:web:appview.test","service":[
                               {"id":"#activitytracker_space","type":"AtprotoSpaceService","serviceEndpoint":"http://appview.test"}]}"""
                        )
                    path == "/xrpc/com.atproto.repo.uploadBlob" -> {
                        val bytes = (request.body as OutgoingContent.ByteArrayContent).bytes()
                        uploads++
                        if (bytes.size > (blobUploadLimit ?: Int.MAX_VALUE))
                            return@MockEngine error("PayloadTooLarge", HttpStatusCode.PayloadTooLarge)
                        // Every blob gets a CID from its content, so each reads back ([blobs]); all start with "bafyblob".
                        val cid =
                            when {
                                bytes.decodeToString().startsWith("<?xml") -> "bafyblobgpx${bytes.contentHashCode().toUInt()}"
                                bytes.size >= 2 && bytes[0] == 0x1f.toByte() && bytes[1] == 0x8b.toByte() ->
                                    "bafyblobgz${bytes.contentHashCode().toUInt()}"
                                else -> "bafyblob${bytes.contentHashCode().toUInt()}"
                            }
                        blobs[cid] = bytes
                        json($$"""{"blob":{"$type":"blob","ref":{"$link":"$$cid"},"mimeType":"application/json","size":10}}""")
                    }
                    path == "/xrpc/com.atproto.sync.getBlob" ->
                        blobs[request.url.parameters["cid"]]?.let { respond(it, headers = headersOf("Content-Type", "application/json")) }
                            ?: error("BlobNotFound")
                    path == "/xrpc/com.atproto.space.getBlob" ->
                        blobs[request.url.parameters["cid"]]?.let {
                            respond(it, headers = headersOf("Content-Type", "application/gpx+xml"))
                        } ?: error("BlobNotFound")
                    path == "/xrpc/com.atproto.simplespace.createSpace" -> {
                        val tier = body(request).getValue("skey").jsonPrimitive.content
                        created += tier
                        json("""{"uri":"at://$did/space/${Nsid.SPACE_ATHLETE}/$tier"}""")
                    }
                    path == "/xrpc/${Nsid.SPACE_NOTIFY_SPACE}" -> {
                        announced += body(request).getValue("space").jsonPrimitive.content.substringAfterLast('/')
                        json("{}")
                    }
                    path == "/xrpc/com.atproto.repo.putRecord" || path == "/xrpc/com.atproto.space.putRecord" -> {
                        val input = body(request)
                        val place = placeOf(input)
                        if (place != repo && failSpacePuts) return@MockEngine error("RateLimitExceeded", HttpStatusCode.TooManyRequests)
                        if (input.getValue("collection").jsonPrimitive.content == failPutsOf) return@MockEngine error("InvalidRecord")
                        // An original names its blob by CID; the PDS refuses one it doesn't hold (never uploaded, or reclaimed).
                        val source =
                            input.getValue("record").jsonObject.takeIf {
                                input["collection"]?.jsonPrimitive?.content == Nsid.FEED_ACTIVITY_SOURCE
                            }
                        val sourceCid = source?.get("file")?.jsonObject?.get("ref")?.jsonObject?.get("\$link")?.jsonPrimitive?.content
                        if (source != null && sourceCid !in blobs) return@MockEngine error("BlobNotFound")
                        val key = "${input.getValue("collection").jsonPrimitive.content}/${input.getValue("rkey").jsonPrimitive.content}"
                        stored.getOrPut(place) { mutableMapOf() }[key] = input.getValue("record").jsonObject
                        writes += "put $place $key"
                        onPut?.invoke(key, input.getValue("record").jsonObject)
                        json("""{"uri":"at://$did/x/y","cid":"bafyrec"}""")
                    }
                    path == "/xrpc/com.atproto.repo.deleteRecord" || path == "/xrpc/com.atproto.space.deleteRecord" -> {
                        val input = body(request)
                        val place = placeOf(input)
                        if (place != repo && failSpaceDeletes)
                            return@MockEngine error("InternalServerError", HttpStatusCode.InternalServerError)
                        val key = "${input.getValue("collection").jsonPrimitive.content}/${input.getValue("rkey").jsonPrimitive.content}"
                        if (place != repo && place !in stored && place !in created) return@MockEngine error(SpaceErrors.SPACE_NOT_FOUND)
                        stored[place]?.remove(key)
                        writes += "delete $place $key"
                        json("{}")
                    }
                    path == "/xrpc/com.atproto.repo.getRecord" || path == "/xrpc/com.atproto.space.getRecord" -> {
                        val params = request.url.parameters
                        val place = params["space"]?.substringAfterLast('/') ?: repo
                        val key = "${params["collection"]}/${params["rkey"]}"
                        if (key == failReadsOf) return@MockEngine error("InternalServerError", HttpStatusCode.InternalServerError)
                        val value = stored[place]?.get(key) ?: return@MockEngine error("RecordNotFound")
                        json("""{"uri":"at://$did/$key","cid":"bafyrec","value":$value}""")
                    }
                    path == "/xrpc/com.atproto.repo.listRecords" || path == "/xrpc/com.atproto.space.listRecords" -> {
                        val params = request.url.parameters
                        val place = params["space"]?.substringAfterLast('/') ?: repo
                        val collection = params["collection"]!!
                        val records =
                            stored[place]
                                .orEmpty()
                                .filterKeys { it.startsWith("$collection/") }
                                .map { (key, value) ->
                                    val rkey = key.substringAfter('/')
                                    if (place == repo) """{"uri":"at://$did/$key","cid":"bafyrec","value":$value}"""
                                    else """{"collection":"$collection","rkey":"$rkey","cid":"bafyrec","value":$value}"""
                                }
                        json("""{"records":[${records.joinToString(",")}]}""")
                    }
                    path == "/xrpc/${Nsid.FEED_GET_AUTHOR_ACTIVITIES}" ->
                        authorActivities?.let {
                            json(AtJson.encodeToString(GetAuthorActivities.Output.serializer(), GetAuthorActivities.Output(it)))
                        } ?: error("InternalServerError", HttpStatusCode.InternalServerError)
                    path == "/xrpc/${Nsid.ACTOR_GET_PROFILE}" ->
                        profiles[request.url.parameters["actor"]]?.let { json(AtJson.encodeToString(ProfileView.serializer(), it)) }
                            ?: error("ProfileNotFound")
                    path == "/xrpc/${Nsid.GRAPH_GET_BLOCKS}" ->
                        json(AtJson.encodeToString(GetBlocks.Output.serializer(), GetBlocks.Output(blocks)))
                    path == "/xrpc/${Nsid.GRAPH_GET_MUTES}" ->
                        json(AtJson.encodeToString(GetMutes.Output.serializer(), GetMutes.Output(mutes)))
                    path == "/xrpc/${Nsid.FEED_GET_ACTIVITY}" ->
                        served?.let { json(AtJson.encodeToString(GetActivity.Output.serializer(), GetActivity.Output(it.activity))) }
                            ?: error("NotFound")
                    path == "/xrpc/${Nsid.FEED_GET_COMMENTS}" -> {
                        val output = served ?: return@MockEngine error("NotFound")
                        val params = request.url.parameters
                        val uris = params.getAll("uris")
                        if (uris != null) lookups += uris
                        val page =
                            fakeGetComments(
                                output.activity.uri,
                                output.comments,
                                uris,
                                params["root"],
                                params["cursor"],
                                params["limit"]?.toInt(),
                            )
                        json(AtJson.encodeToString(GetComments.Output.serializer(), page))
                    }
                    path == "/xrpc/${Nsid.FEED_GET_ACTIVITY_STREAMS}" -> {
                        if (failStreams) return@MockEngine error("InternalServerError", HttpStatusCode.InternalServerError)
                        val uri = AtUri(request.url.parameters["uri"]!!)
                        val visibility =
                            streamsVisibility[uri.rkey]
                                ?: return@MockEngine servedFromStored(uri)?.let {
                                    json(AtJson.encodeToString(GetActivityStreams.Output.serializer(), it))
                                } ?: error("NotFound")
                        json(
                            AtJson.encodeToString(
                                GetActivityStreams.Output.serializer(),
                                GetActivityStreams.Output(
                                    uri,
                                    visibility,
                                    emptyList(),
                                    streamsData[uri.rkey] ?: ActivityStreamData(time = listOf(0)),
                                    trainingLoad = streamsLoad[uri.rkey],
                                ),
                            )
                        )
                    }
                    else -> respond("", HttpStatusCode.NotFound)
                }
            }
        ) {
            expectSuccess = false
        }

    /**
     * What the AppView serves the athlete of [uri]'s streams when the test set none ([streamsVisibility]): every place's streams record,
     * merged widest first, as the real one does for their owner.
     */
    private fun servedFromStored(uri: AtUri): GetActivityStreams.Output? {
        val parts = placesWidestFirst.mapNotNull { tier ->
            val record = stored[tier]?.get("${Nsid.FEED_ACTIVITY_STREAMS}/${uri.rkey}") ?: return@mapNotNull null
            val cid = record["data"]?.jsonObject?.get("ref")?.jsonObject?.get("\$link")?.jsonPrimitive?.content
            val bytes = blobs[cid] ?: return@mapNotNull null
            tier to AtJson.decodeFromString(ActivityStreamData.serializer(), bytes.decodeToString())
        }
        val data = mergeStreams(parts.map { it.second }) ?: return null
        val tier = { t: String -> if (t == repo) Visibility.PUBLIC else t }
        return GetActivityStreams.Output(
            uri,
            tier(parts.first().first),
            data.channels,
            data,
            trainingLoad = streamsLoad[uri.rkey],
            parts = parts.map { (t, d) -> GetActivityStreams.StreamsPart(tier(t), d.channels) },
        )
    }

    private fun placeOf(input: JsonObject) = input["space"]?.jsonPrimitive?.content?.substringAfterLast('/') ?: repo

    private fun body(request: HttpRequestData): JsonObject =
        AtJson.parseToJsonElement((request.body as OutgoingContent.ByteArrayContent).bytes().decodeToString()).jsonObject

    private fun MockRequestHandleScope.json(body: String) = respond(body, headers = headersOf("Content-Type", "application/json"))

    private fun MockRequestHandleScope.error(name: String, status: HttpStatusCode = HttpStatusCode.BadRequest) =
        respond("""{"error":"$name"}""", status, headersOf("Content-Type", "application/json"))

    /** Every place, widest first, as [stored] names them. */
    private val placesWidestFirst = listOf(repo, Visibility.FOLLOWERS, Visibility.MUTUALS, Visibility.SELF)

    /** The places holding [collection]/[rkey] now. */
    fun placesOf(collection: String, rkey: String): Set<String> = stored.filterValues { "$collection/$rkey" in it }.keys

    @Suppress("LongParameterList") // What each test reaches for.
    class Session(
        val db: AppDatabase,
        val clients: SessionClients,
        val feed: FeedRepository,
        val activities: ActivityRepository,
        val manager: ActivityManager,
        val streams: PdsActivityStreamsStore,
        val tracks: PdsActivityTrackStore,
        val sources: PdsActivitySourceStore,
        val devices: PdsActivityDevicesStore,
        val defaults: ActivityDefaults,
        val zones: ActivityZonesStore,
        val privacy: PrivacyZones,
        val routes: RouteTrimmer,
        val exporter: ActivityExporter,
        val blocks: BlockRepository,
    )

    @Suppress("LongMethod") // Builds every store a test reaches for.
    suspend fun session(scope: String? = null): Session {
        val (db, _, clients) =
            if (scope == null) signedInTestSession(http, did = did) else signedInTestSession(http, did = did, scope = scope)
        val outbox = OutboxProcessor(db, clients, Logger)
        val feed = FeedRepository(db, clients, LocalLikes(), Logger)
        val writer = OutboxWriter(db, clients, Clock.System)
        val zones = ActivityZonesStore(db, writer, outbox, clients, Clock.System, Logger)
        val places = ActivityPlaces(clients, Clock.System, Logger)
        val streams = PdsActivityStreamsStore(writer, clients, Clock.System, zones)
        val tracks = PdsActivityTrackStore(db, writer, clients, Clock.System)
        val sources = PdsActivitySourceStore(db, writer, clients, Clock.System)
        val devices = PdsActivityDevicesStore(db, writer, clients, Clock.System)
        val privacy = PrivacyZones(clients, Clock.System, Logger)
        val routes = RouteTrimmer(db, clients, writer, outbox, privacy, tracks, feed, Clock.System, Logger)
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
                places,
                privacy,
                routes,
                feed,
                LocalLikes(),
                RecentActivityTypes(db, clients, feed),
                Clock.System,
                testSegments(db, clients, writer, outbox, privacy, tracks).tracks,
                testSegments(db, clients, writer, outbox, privacy, tracks).sweeper,
            )
        return Session(
            db,
            clients,
            feed,
            activities,
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
                places,
                feed,
                privacy,
                Clock.System,
                activities,
                testSegments(db, clients, writer, outbox, privacy, tracks).tracks,
            ),
            streams,
            tracks,
            sources,
            devices,
            ActivityDefaults(clients),
            zones,
            privacy,
            routes,
            ActivityExporter(db, clients, tracks, sources, feed),
            BlockRepository(clients, writer, outbox, feed, Clock.System),
        )
    }
}

/** [view] cached in the session's feed, as a timeline item. */
suspend fun FakeAthletePds.Session.cache(view: ActivityView): ActivityView {
    db.feed().upsert(listOf(FeedItemEntity("timeline", view.uri.value, 0, AtJson.encodeToString(ActivityView.serializer(), view))))
    return view
}

/** [view] as the session's feed has it cached now. */
suspend fun FakeAthletePds.Session.cached(view: ActivityView): ActivityView =
    AtJson.decodeFromString(ActivityView.serializer(), db.feed().byUri(view.uri.value).single().json)
