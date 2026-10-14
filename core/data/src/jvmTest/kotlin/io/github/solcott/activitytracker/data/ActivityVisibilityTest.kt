package io.github.solcott.activitytracker.data

import co.touchlab.kermit.Logger
import io.github.solcott.activitytracker.atproto.xrpc.XrpcException
import io.github.solcott.activitytracker.data.activity.ActivityDefaults
import io.github.solcott.activitytracker.data.activity.ActivityDraft
import io.github.solcott.activitytracker.data.activity.ActivityManager
import io.github.solcott.activitytracker.data.activity.ActivityRepository
import io.github.solcott.activitytracker.data.activity.ActivityStreams
import io.github.solcott.activitytracker.data.activity.OutboxProcessor
import io.github.solcott.activitytracker.data.activity.OutboxWriter
import io.github.solcott.activitytracker.data.activity.OwnActivity
import io.github.solcott.activitytracker.data.activity.PdsActivityStreamsStore
import io.github.solcott.activitytracker.data.activity.VisibilityChoice
import io.github.solcott.activitytracker.data.di.SessionClients
import io.github.solcott.activitytracker.data.di.signedInTestSession
import io.github.solcott.activitytracker.data.feed.FeedRepository
import io.github.solcott.activitytracker.data.feed.LocalKudos
import io.github.solcott.activitytracker.data.sport.RecentActivityTypes
import io.github.solcott.activitytracker.lexicon.AtJson
import io.github.solcott.activitytracker.lexicon.AtUri
import io.github.solcott.activitytracker.lexicon.Cid
import io.github.solcott.activitytracker.lexicon.Did
import io.github.solcott.activitytracker.lexicon.Handle
import io.github.solcott.activitytracker.lexicon.Nsid
import io.github.solcott.activitytracker.lexicon.app.ActivityRecord
import io.github.solcott.activitytracker.lexicon.app.ActivityStreamData
import io.github.solcott.activitytracker.lexicon.app.ActivityType
import io.github.solcott.activitytracker.lexicon.app.ActivityView
import io.github.solcott.activitytracker.lexicon.app.GetActivityStreams
import io.github.solcott.activitytracker.lexicon.app.ProfileViewBasic
import io.github.solcott.activitytracker.lexicon.app.RecordingView
import io.github.solcott.activitytracker.lexicon.app.Sport
import io.github.solcott.activitytracker.lexicon.app.Visibility
import io.github.solcott.activitytracker.lexicon.atproto.SpaceErrors
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import io.ktor.http.headersOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Activity visibility in the data layer (`feed/activity-visibility`): where activities, their charts and the defaults are stored, and how a
 * visibility change moves them. The fake PDS holds Alice's repo and each of her tier spaces as `place → collection/rkey → record`.
 */
class ActivityVisibilityTest {
    private val did = Did("did:plc:alice")
    private val repo = "repo"

    /** Every place's records: [repo] or a tier, then `collection/rkey`. */
    private val stored = mutableMapOf<String, MutableMap<String, JsonObject>>()

    /** Every write in order, as `"put|delete <place> <collection>/<rkey>"`. */
    private val writes = mutableListOf<String>()
    private val announced = mutableListOf<String>()
    private val created = mutableListOf<String>()

    /** Fail space deletes while set; and every request to the PDS while [offline]. */
    private var failSpaceDeletes = false
    private var offline = false

    /** The charts visibility the AppView serves for each activity rkey; absent for none. */
    private val streamsVisibility = mutableMapOf<String, String>()

    private val http =
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
    private fun placesOf(collection: String, rkey: String): Set<String> = stored.filterValues { "$collection/$rkey" in it }.keys

    private class Session(
        val clients: SessionClients,
        val activities: ActivityRepository,
        val manager: ActivityManager,
        val streams: PdsActivityStreamsStore,
        val defaults: ActivityDefaults,
        val feed: FeedRepository,
    )

    private suspend fun session(scope: String? = null): Session {
        val (db, _, clients) =
            if (scope == null) signedInTestSession(http, did = did) else signedInTestSession(http, did = did, scope = scope)
        val outbox = OutboxProcessor(db, clients, Logger)
        val feed = FeedRepository(db, clients, LocalKudos(), Logger)
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
                LocalKudos(),
                RecentActivityTypes(db, clients, feed),
                Clock.System,
            )
        return Session(
            clients,
            activities,
            ActivityManager(db, clients, outbox, writer, streams, feed),
            streams,
            ActivityDefaults(clients),
            feed,
        )
    }

    private val run = ActivityType(Sport.RUN)

    private fun draft(choice: VisibilityChoice = VisibilityChoice.PUBLIC, withStreams: Boolean = true) =
        ActivityDraft(
                type = run,
                title = "Run",
                description = null,
                startedAt = Instant.parse("2026-10-14T07:00:00Z"),
                elapsed = 30.minutes,
                streams = ActivityStreamData(time = listOf(0, 1), heartRate = listOf(120, 121)).takeIf { withStreams },
            )
            .withVisibility(choice)

    private fun view(uri: AtUri, visibility: String, recordings: List<AtUri> = emptyList()) =
        ActivityView(
            uri = uri,
            cid = Cid("bafyrec"),
            author = ProfileViewBasic(did, Handle("alice.test")),
            record =
                ActivityRecord(
                    sport = Sport.RUN,
                    startedAt = Instant.parse("2026-10-14T07:00:00Z"),
                    elapsedTime = 1800,
                    createdAt = Clock.System.now(),
                ),
            kudosCount = 2,
            commentCount = 1,
            indexedAt = Clock.System.now(),
            visibility = visibility,
            recordings = recordings.map { RecordingView(it, Instant.parse("2026-10-14T07:00:00Z")) }.ifEmpty { null },
        )

    @Test
    fun followersActivityIsPublishedToTheFollowersSpace() = runTest {
        val s = session()
        val uri = s.activities.publish(draft(VisibilityChoice(Visibility.FOLLOWERS, Visibility.SELF)))
        val rkey = uri.rkey!!
        assertEquals(setOf(Visibility.FOLLOWERS), placesOf(Nsid.FEED_ACTIVITY, rkey), "never in the repo")
        assertEquals(setOf(Visibility.SELF), placesOf(Nsid.FEED_ACTIVITY_STREAMS, rkey))
        assertEquals(listOf(Visibility.FOLLOWERS, Visibility.SELF), created)
        assertEquals(listOf(Visibility.FOLLOWERS, Visibility.SELF), announced, "the AppView learns of each tier space")
        assertEquals(uri, AtUri.of(did, Nsid.FEED_ACTIVITY, rkey), "one URI wherever it's stored")
    }

    @Test
    fun publicActivityIsPublishedToTheRepoOnly() = runTest {
        val s = session()
        val uri = s.activities.publish(draft(VisibilityChoice(Visibility.PUBLIC, Visibility.MUTUALS)))
        assertEquals(setOf(repo), placesOf(Nsid.FEED_ACTIVITY, uri.rkey!!))
        assertEquals(setOf(Visibility.MUTUALS), placesOf(Nsid.FEED_ACTIVITY_STREAMS, uri.rkey!!), "public run, private heart rate")
        val streams = stored.getValue(Visibility.MUTUALS).getValue("${Nsid.FEED_ACTIVITY_STREAMS}/${uri.rkey}")
        assertEquals(Visibility.MUTUALS, streams.getValue("visibility").jsonPrimitive.content)
    }

    @Test
    fun chartsAreNeverPublishedWiderThanTheActivity() = runTest {
        val s = session()
        val uri = s.activities.publish(draft().copy(visibility = Visibility.MUTUALS, streamsVisibility = Visibility.PUBLIC))
        assertEquals(setOf(Visibility.MUTUALS), placesOf(Nsid.FEED_ACTIVITY_STREAMS, uri.rkey!!))
    }

    @Test
    fun batchImportPublishesEveryActivityAtTheChosenVisibility() = runTest {
        val s = session()
        val choice = VisibilityChoice(Visibility.MUTUALS, Visibility.MUTUALS)
        val uris = s.activities.publishAll(List(3) { draft(choice, withStreams = false).copy(title = "Dive $it") })
        uris.forEach { assertEquals(setOf(Visibility.MUTUALS), placesOf(Nsid.FEED_ACTIVITY, it.rkey!!)) }
        assertEquals(listOf(Visibility.MUTUALS), created, "the space is created once")
    }

    @Test
    fun aSessionWithoutTheSpaceScopePublishesPublicOnly() = runTest {
        val s = session(scope = "atproto")
        assertEquals(false, s.clients.canUseSpaces)
        val e = assertFailsWith<XrpcException> { s.activities.publish(draft(VisibilityChoice(Visibility.FOLLOWERS, Visibility.FOLLOWERS))) }
        assertEquals(SpaceErrors.SCOPE_MISSING, e.error)
        assertEquals(emptyList(), writes, "nothing written, so nothing published wider than chosen")
        s.activities.publish(draft())
        assertTrue(writes.isNotEmpty())
        assertEquals(VisibilityChoice.PUBLIC, s.defaults.load(), "defaults can't be read: Public")
    }

    @Test
    fun narrowingTheActivityMovesItAndDragsItsCharts() = runTest {
        val s = session()
        val uri = s.activities.publish(draft(VisibilityChoice.PUBLIC))
        val rkey = uri.rkey!!
        streamsVisibility[rkey] = Visibility.PUBLIC
        writes.clear()

        s.manager.setVisibility(view(uri, Visibility.PUBLIC), VisibilityChoice(Visibility.FOLLOWERS, Visibility.PUBLIC))
        assertEquals(setOf(Visibility.FOLLOWERS), placesOf(Nsid.FEED_ACTIVITY, rkey))
        assertEquals(setOf(Visibility.FOLLOWERS), placesOf(Nsid.FEED_ACTIVITY_STREAMS, rkey), "charts narrowed to the activity")
        val streams = stored.getValue(Visibility.FOLLOWERS).getValue("${Nsid.FEED_ACTIVITY_STREAMS}/$rkey")
        assertEquals(Visibility.FOLLOWERS, streams.getValue("visibility").jsonPrimitive.content)
        assertEquals("bafyblob", streams.getValue("data").jsonObject.getValue("ref").jsonObject.getValue("\$link").jsonPrimitive.content)
        // Each record is written to its new place before it leaves the old one.
        assertEquals(
            listOf(
                "put followers ${Nsid.FEED_ACTIVITY}/$rkey",
                "delete repo ${Nsid.FEED_ACTIVITY}/$rkey",
                "put followers ${Nsid.FEED_ACTIVITY_STREAMS}/$rkey",
                "delete repo ${Nsid.FEED_ACTIVITY_STREAMS}/$rkey",
            ),
            writes,
        )
    }

    @Test
    fun anInterruptedMoveLeavesTheActivityStoredAndARetryFinishesIt() = runTest {
        val s = session()
        val uri = s.activities.publish(draft(VisibilityChoice(Visibility.MUTUALS, Visibility.MUTUALS), withStreams = false))
        val rkey = uri.rkey!!
        failSpaceDeletes = true
        assertFailsWith<XrpcException> {
            s.manager.setVisibility(view(uri, Visibility.MUTUALS), VisibilityChoice(Visibility.SELF, Visibility.SELF))
        }
        assertEquals(setOf(Visibility.MUTUALS, Visibility.SELF), placesOf(Nsid.FEED_ACTIVITY, rkey), "stored, in both for now")

        failSpaceDeletes = false
        s.manager.setVisibility(view(uri, Visibility.MUTUALS), VisibilityChoice(Visibility.SELF, Visibility.SELF))
        assertEquals(setOf(Visibility.SELF), placesOf(Nsid.FEED_ACTIVITY, rkey))
    }

    @Test
    fun aMoveFromAnOutdatedVisibilityFailsAndChangesNothing() = runTest {
        val s = session()
        // Moved to Mutuals elsewhere; this view still says Followers.
        val uri = s.activities.publish(draft(VisibilityChoice(Visibility.MUTUALS, Visibility.MUTUALS), withStreams = false))
        val rkey = uri.rkey!!
        writes.clear()

        assertFailsWith<XrpcException> {
            s.manager.setVisibility(view(uri, Visibility.FOLLOWERS), VisibilityChoice(Visibility.SELF, Visibility.SELF))
        }
        assertEquals(setOf(Visibility.MUTUALS), placesOf(Nsid.FEED_ACTIVITY, rkey))
        assertEquals(emptyList(), writes)
    }

    @Test
    fun recordingsMoveWithTheirPrimary() = runTest {
        val s = session()
        val primary = s.activities.publish(draft(withStreams = false))
        val recording =
            s.activities.publish(
                draft(withStreams = false)
                    .recordingOf(
                        OwnActivity(
                            primary,
                            null,
                            ActivityRecord(Sport.RUN, startedAt = Clock.System.now(), elapsedTime = 1, createdAt = Clock.System.now()),
                        )
                    )
            )
        s.manager.setVisibility(
            view(primary, Visibility.PUBLIC, recordings = listOf(recording)),
            VisibilityChoice(Visibility.SELF, Visibility.SELF),
        )
        assertEquals(setOf(Visibility.SELF), placesOf(Nsid.FEED_ACTIVITY, primary.rkey!!))
        assertEquals(setOf(Visibility.SELF), placesOf(Nsid.FEED_ACTIVITY, recording.rkey!!))
    }

    @Test
    fun groupingIntoAFollowersActivityMakesTheRecordingFollowers() = runTest {
        val s = session()
        val primary =
            OwnActivity(
                AtUri.of(did, Nsid.FEED_ACTIVITY, "3kprimary"),
                Cid("bafyrec"),
                view(AtUri("at://x/y/z"), Visibility.FOLLOWERS).record,
                Visibility.FOLLOWERS,
            )
        val grouped = draft(VisibilityChoice.PUBLIC).recordingOf(primary)
        assertEquals(Visibility.FOLLOWERS, grouped.visibility)
        assertEquals(Visibility.FOLLOWERS, grouped.publishedStreamsVisibility, "charts capped too")
        val uri = s.activities.publish(grouped)
        assertEquals(setOf(Visibility.FOLLOWERS), placesOf(Nsid.FEED_ACTIVITY, uri.rkey!!))
    }

    @Test
    fun streamsMoveBetweenTheRepoAndASpaceAndBack() = runTest {
        val s = session()
        val uri = AtUri.of(did, Nsid.FEED_ACTIVITY, "3kstreams")
        s.streams.put(uri, ActivityStreams(ActivityStreamData(time = listOf(0)), Visibility.PUBLIC))
        s.activities.sync()
        assertEquals(setOf(repo), placesOf(Nsid.FEED_ACTIVITY_STREAMS, "3kstreams"))
        s.streams.move(uri, Visibility.PUBLIC, Visibility.FOLLOWERS)
        assertEquals(setOf(Visibility.FOLLOWERS), placesOf(Nsid.FEED_ACTIVITY_STREAMS, "3kstreams"))
        s.streams.move(uri, Visibility.FOLLOWERS, Visibility.PUBLIC)
        assertEquals(setOf(repo), placesOf(Nsid.FEED_ACTIVITY_STREAMS, "3kstreams"))
        val record = stored.getValue(repo).getValue("${Nsid.FEED_ACTIVITY_STREAMS}/3kstreams")
        assertEquals(Visibility.PUBLIC, record.getValue("visibility").jsonPrimitive.content)
    }

    @Test
    fun deletingASpaceActivityClearsItAndItsStreamsWhereverTheyAre() = runTest {
        val s = session()
        val uri = s.activities.publish(draft(VisibilityChoice(Visibility.MUTUALS, Visibility.SELF)))
        s.manager.delete(view(uri, Visibility.MUTUALS), withRecordings = false)
        assertEquals(emptySet(), placesOf(Nsid.FEED_ACTIVITY, uri.rkey!!))
        assertEquals(emptySet(), placesOf(Nsid.FEED_ACTIVITY_STREAMS, uri.rkey!!))
        assertEquals(0, s.activities.sync(), "deletes from spaces that never existed count as done")
    }

    @Test
    fun defaultsAreReadOnceSavedCappedAndPublicWhenUnreadable() = runTest {
        val s = session()
        assertEquals(VisibilityChoice.PUBLIC, s.defaults.load(), "never set")
        assertEquals(
            VisibilityChoice(Visibility.MUTUALS, Visibility.MUTUALS),
            s.defaults.save(VisibilityChoice(Visibility.MUTUALS, Visibility.PUBLIC)),
        )
        val record = stored.getValue(Visibility.SELF).getValue("${Nsid.ACTOR_ACTIVITY_DEFAULTS}/self")
        assertEquals(Visibility.MUTUALS, record.getValue("chartsVisibility").jsonPrimitive.content, "charts default capped")

        // Another device signs in: it reads the same defaults.
        assertEquals(VisibilityChoice(Visibility.MUTUALS, Visibility.MUTUALS), session().defaults.load())

        offline = true
        assertEquals(VisibilityChoice.PUBLIC, session().defaults.load(), "unreadable: Public, without blocking")
    }

    @Test
    fun nonPublicActivitiesTakeNoKudosOrComments() = runTest {
        val s = session()
        val bobs =
            view(AtUri.of(Did("did:plc:bob"), Nsid.FEED_ACTIVITY, "3kbob"), Visibility.FOLLOWERS)
                .copy(author = ProfileViewBasic(Did("did:plc:bob"), Handle("bob.test")))
        s.activities.toggleKudos(bobs)
        s.activities.comment(bobs, "Nice")
        s.activities.sync()
        assertEquals(emptyList(), writes)
        s.activities.toggleKudos(bobs.copy(visibility = Visibility.PUBLIC))
        assertEquals(listOf("put repo ${Nsid.FEED_KUDOS}"), writes.map { it.substringBeforeLast('/') })
    }
}
