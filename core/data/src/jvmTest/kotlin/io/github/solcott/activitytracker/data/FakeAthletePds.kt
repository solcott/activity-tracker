package io.github.solcott.activitytracker.data

import co.touchlab.kermit.Logger
import io.github.solcott.activitytracker.data.activity.ActivityDefaults
import io.github.solcott.activitytracker.data.activity.ActivityManager
import io.github.solcott.activitytracker.data.activity.ActivityRepository
import io.github.solcott.activitytracker.data.activity.OutboxProcessor
import io.github.solcott.activitytracker.data.activity.OutboxWriter
import io.github.solcott.activitytracker.data.activity.PdsActivityStreamsStore
import io.github.solcott.activitytracker.data.db.AppDatabase
import io.github.solcott.activitytracker.data.db.FeedItemEntity
import io.github.solcott.activitytracker.data.di.SessionClients
import io.github.solcott.activitytracker.data.di.signedInTestSession
import io.github.solcott.activitytracker.data.feed.FeedRepository
import io.github.solcott.activitytracker.data.feed.LocalLikes
import io.github.solcott.activitytracker.data.feed.fakeGetComments
import io.github.solcott.activitytracker.data.sport.RecentActivityTypes
import io.github.solcott.activitytracker.lexicon.AtJson
import io.github.solcott.activitytracker.lexicon.AtUri
import io.github.solcott.activitytracker.lexicon.Did
import io.github.solcott.activitytracker.lexicon.Nsid
import io.github.solcott.activitytracker.lexicon.app.ActivityStreamData
import io.github.solcott.activitytracker.lexicon.app.ActivityView
import io.github.solcott.activitytracker.lexicon.app.GetActivity
import io.github.solcott.activitytracker.lexicon.app.GetActivityStreams
import io.github.solcott.activitytracker.lexicon.app.GetComments
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
    val announced = mutableListOf<String>()
    val created = mutableListOf<String>()

    /** Fail space deletes while set; and every request to the PDS while [offline]. */
    var failSpaceDeletes = false
    var offline = false

    /**
     * What the AppView serves: `getActivity` the activity, `getComments` its comments, oldest first, as threads (in pages of the asked
     * limit of threads, each with its first three replies; a `root`'s replies a page at a time; or those of the asked `uris`); NotFound
     * while null. A comment naming a root is a reply in that thread, and a thread with no top-level comment listed is a placeholder.
     */
    var served: GetActivity.Output? = null

    /** The `uris` of each `getComments` lookup, in order. */
    val lookups = mutableListOf<List<String>>()

    /** The charts visibility the AppView serves for each activity rkey; absent for none. */
    val streamsVisibility = mutableMapOf<String, String>()

    val http =
        HttpClient(
            MockEngine { request ->
                if (offline && request.url.host == "pds.test")
                    return@MockEngine error("InternalServerError", HttpStatusCode.InternalServerError)
                val path = request.url.encodedPath
                when {
                    path == "/xrpc/com.atproto.server.getServiceAuth" -> json("""{"token":"service-jwt"}""")
                    path == "/.well-known/did.json" ->
                        json(
                            """{"id":"did:web:appview.test","service":[
                               {"id":"#activitytracker_space","type":"AtprotoSpaceService","serviceEndpoint":"http://appview.test"}]}"""
                        )
                    path == "/xrpc/com.atproto.repo.uploadBlob" ->
                        json($$"""{"blob":{"$type":"blob","ref":{"$link":"bafyblob"},"mimeType":"application/json","size":10}}""")
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
                        val key = "${input.getValue("collection").jsonPrimitive.content}/${input.getValue("rkey").jsonPrimitive.content}"
                        stored.getOrPut(place) { mutableMapOf() }[key] = input.getValue("record").jsonObject
                        writes += "put $place $key"
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
                        val value = stored[place]?.get(key) ?: return@MockEngine error("RecordNotFound")
                        json("""{"uri":"at://$did/$key","cid":"bafyrec","value":$value}""")
                    }
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
                        val uri = AtUri(request.url.parameters["uri"]!!)
                        val visibility = streamsVisibility[uri.rkey] ?: return@MockEngine error("NotFound")
                        json(
                            AtJson.encodeToString(
                                GetActivityStreams.Output.serializer(),
                                GetActivityStreams.Output(uri, visibility, emptyList(), ActivityStreamData(time = listOf(0))),
                            )
                        )
                    }
                    else -> respond("", HttpStatusCode.NotFound)
                }
            }
        ) {
            expectSuccess = false
        }

    private fun placeOf(input: JsonObject) = input["space"]?.jsonPrimitive?.content?.substringAfterLast('/') ?: repo

    private fun body(request: HttpRequestData): JsonObject =
        AtJson.parseToJsonElement((request.body as OutgoingContent.ByteArrayContent).bytes().decodeToString()).jsonObject

    private fun MockRequestHandleScope.json(body: String) = respond(body, headers = headersOf("Content-Type", "application/json"))

    private fun MockRequestHandleScope.error(name: String, status: HttpStatusCode = HttpStatusCode.BadRequest) =
        respond("""{"error":"$name"}""", status, headersOf("Content-Type", "application/json"))

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
        val defaults: ActivityDefaults,
    )

    suspend fun session(scope: String? = null): Session {
        val (db, _, clients) =
            if (scope == null) signedInTestSession(http, did = did) else signedInTestSession(http, did = did, scope = scope)
        val outbox = OutboxProcessor(db, clients, Logger)
        val feed = FeedRepository(db, clients, LocalLikes(), Logger)
        val writer = OutboxWriter(db, clients, Clock.System)
        val streams = PdsActivityStreamsStore(writer, clients, Clock.System)
        val activities =
            ActivityRepository(
                db,
                clients,
                outbox,
                writer,
                streams,
                feed,
                LocalLikes(),
                RecentActivityTypes(db, clients, feed),
                Clock.System,
            )
        return Session(
            db,
            clients,
            feed,
            activities,
            ActivityManager(db, clients, outbox, writer, streams, feed),
            streams,
            ActivityDefaults(clients),
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
