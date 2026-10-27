package io.github.solcott.activitytracker.app

import app.cash.turbine.ReceiveTurbine
import co.touchlab.kermit.Logger
import com.slack.circuit.test.FakeNavigator
import io.github.solcott.activitytracker.app.feed.FeedEvent
import io.github.solcott.activitytracker.app.feed.FeedPresenter
import io.github.solcott.activitytracker.app.feed.FeedScreen
import io.github.solcott.activitytracker.app.feed.FeedState
import io.github.solcott.activitytracker.atproto.OAuthClientConfig
import io.github.solcott.activitytracker.data.activity.ActivityPlaces
import io.github.solcott.activitytracker.data.activity.ActivityRepository
import io.github.solcott.activitytracker.data.activity.PrivacyZones
import io.github.solcott.activitytracker.data.activity.RejectedChanges
import io.github.solcott.activitytracker.data.activity.RouteTrimmer
import io.github.solcott.activitytracker.data.di.signedInTestSession
import io.github.solcott.activitytracker.data.feed.FeedRepository
import io.github.solcott.activitytracker.data.feed.LocalLikes
import io.github.solcott.activitytracker.data.profile.ProfileRepository
import io.github.solcott.activitytracker.data.sport.RecentActivityTypes
import io.github.solcott.activitytracker.lexicon.AtJson
import io.github.solcott.activitytracker.lexicon.Nsid
import io.github.solcott.activitytracker.lexicon.app.GetTimeline
import io.github.solcott.activitytracker.location.UnsupportedLocationTracker
import io.github.solcott.uistate.isLoading
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import io.ktor.http.headersOf
import java.util.Collections
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** The feed's one-time question about segment matching (`segments/segment-track`: the athlete is asked once). */
class SegmentsPromptTest {
    private val did = "did:plc:alice"
    private val json = headersOf("Content-Type", "application/json")

    /** Alice's `actor.segmentPrefs` as JSON; null for none. */
    @Volatile private var prefs: String? = null

    /** Whether reading the setting fails, as offline. */
    @Volatile private var failPrefsReads = false

    /** Whether Alice has an activity with a track in her `self` space. */
    @Volatile private var hasTrack = true

    /** Whether writes to her spaces fail. */
    @Volatile private var failWrites = false

    private val prefsPuts: MutableList<String> = Collections.synchronizedList(mutableListOf())

    private val http =
        HttpClient(
            MockEngine { request ->
                val collection = request.url.parameters["collection"]
                when (request.url.encodedPath) {
                    "/xrpc/com.atproto.server.getServiceAuth" -> respond("""{"token":"service-jwt"}""", headers = json)
                    // The AppView's DID document names its managing-app service, which creating a space needs.
                    "/.well-known/did.json" ->
                        respond(
                            """{"id":"did:web:appview.test","service":[
                               {"id":"#activitytracker_space","type":"AtprotoSpaceService","serviceEndpoint":"http://appview.test"}]}""",
                            headers = json,
                        )
                    "/xrpc/com.atproto.simplespace.createSpace" ->
                        respond("""{"uri":"at://$did/space/${Nsid.SPACE_ATHLETE}/self"}""", headers = json)
                    "/xrpc/com.atproto.space.getRecord" -> {
                        val value = prefs
                        when {
                            collection != Nsid.ACTOR_SEGMENT_PREFS ->
                                respond("""{"error":"RecordNotFound"}""", HttpStatusCode.BadRequest, json)
                            failPrefsReads -> respond("""{"error":"InternalServerError"}""", HttpStatusCode.InternalServerError, json)
                            value == null -> respond("""{"error":"RecordNotFound"}""", HttpStatusCode.BadRequest, json)
                            else -> respond("""{"uri":"at://$did/$collection/self","cid":"bafyrec","value":$value}""", headers = json)
                        }
                    }
                    "/xrpc/com.atproto.space.listRecords" -> {
                        val records =
                            if (collection == Nsid.FEED_ACTIVITY_TRACK && hasTrack) {
                                """[{"collection":"$collection","rkey":"3ktrack","cid":"bafyrec","value":{}}]"""
                            } else {
                                "[]"
                            }
                        respond("""{"records":$records}""", headers = json)
                    }
                    "/xrpc/com.atproto.space.putRecord" -> {
                        if (failWrites) {
                            respond("""{"error":"InternalServerError"}""", HttpStatusCode.InternalServerError, json)
                        } else {
                            val input =
                                AtJson.parseToJsonElement((request.body as OutgoingContent.ByteArrayContent).bytes().decodeToString())
                            val record = input.jsonObject.getValue("record").jsonObject
                            if (input.jsonObject.getValue("collection").jsonPrimitive.content == Nsid.ACTOR_SEGMENT_PREFS) {
                                prefs = record.toString()
                                prefsPuts += record.toString()
                            }
                            respond("""{"uri":"at://$did/x/self","cid":"bafyrec"}""", headers = json)
                        }
                    }
                    "/xrpc/${Nsid.FEED_GET_TIMELINE}" ->
                        respond(AtJson.encodeToString(GetTimeline.Output.serializer(), GetTimeline.Output(emptyList())), headers = json)
                    else -> respond("", HttpStatusCode.NotFound)
                }
            }
        ) {
            expectSuccess = false
        }

    private suspend fun feedTest(scope: String = OAuthClientConfig.DEFAULT_SCOPE, block: suspend ReceiveTurbine<FeedState>.() -> Unit) {
        val (db, _, clients) = signedInTestSession(http, scope = scope)
        val localLikes = LocalLikes()
        val feed = FeedRepository(db, clients, localLikes, Logger)
        val zones = TestZones(db, clients)
        val privacy = PrivacyZones(clients, Clock.System, Logger)
        val segments = testSegments(db, clients, zones.writer, zones.outbox, privacy, zones.tracks)
        val activities =
            ActivityRepository(
                db,
                clients,
                zones.outbox,
                zones.writer,
                zones.streams,
                zones.tracks,
                zones.sources,
                zones.devices,
                zones.store,
                ActivityPlaces(clients, Clock.System, Logger),
                privacy,
                RouteTrimmer(db, clients, zones.writer, zones.outbox, privacy, zones.tracks, feed, Clock.System, Logger),
                feed,
                localLikes,
                RecentActivityTypes(db, clients, feed),
                Clock.System,
                segments.tracks,
                segments.sweeper,
            )
        val profiles = ProfileRepository(clients, Clock.System, Logger, zones.store)
        presenterTestOf(
            {
                FeedPresenter(
                    FakeNavigator(FeedScreen),
                    feed,
                    activities,
                    profiles,
                    UnsupportedLocationTracker(),
                    Clock.System,
                    segments.prefs,
                    RejectedChanges(db, clients, zones.outbox, feed, localLikes),
                )
            },
            timeout = 10.seconds,
            block = block,
        )
    }

    /** Waits out the first load, so the question has had its chance to show. */
    private suspend fun ReceiveTurbine<FeedState>.settled(): FeedState {
        awaitUntil { it.content.isLoading }
        return awaitUntil { !it.content.isLoading }
    }

    @Test
    fun anAthleteWhoNeverChoseAndHasATrackIsAsked() = runTest {
        feedTest {
            awaitUntil { it.askSegments }
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun turningItOnSavesTheSettingAndStopsAsking() = runTest {
        feedTest {
            awaitUntil { it.askSegments }.eventSink(FeedEvent.AnswerSegments(on = true))
            awaitUntil { !it.askSegments }
            cancelAndIgnoreRemainingEvents()
        }
        assertEquals("true", AtJson.parseToJsonElement(prefsPuts.single()).jsonObject.getValue("matching").jsonPrimitive.content)

        feedTest {
            assertFalse(settled().askSegments, "never asked again")
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun notNowSavesItOffAndStopsAsking() = runTest {
        feedTest {
            awaitUntil { it.askSegments }.eventSink(FeedEvent.AnswerSegments(on = false))
            awaitUntil { !it.askSegments }
            cancelAndIgnoreRemainingEvents()
        }
        assertEquals("false", AtJson.parseToJsonElement(prefsPuts.single()).jsonObject.getValue("matching").jsonPrimitive.content)

        feedTest {
            assertFalse(settled().askSegments, "declining is remembered on every device")
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun aFailedSaveKeepsAskingAndShowsAnError() = runTest {
        failWrites = true
        feedTest {
            awaitUntil { it.askSegments }.eventSink(FeedEvent.AnswerSegments(on = true))
            val failed = awaitUntil { it.error != null }
            assertEquals(UiError.Kind.SaveSettings, failed.error?.kind)
            assertTrue(failed.askSegments)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun anUnreadableSettingIsntTakenAsNeverAsked() = runTest {
        failPrefsReads = true
        feedTest {
            assertFalse(settled().askSegments)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun anAthleteWithoutATrackIsntAsked() = runTest {
        hasTrack = false
        feedTest {
            assertFalse(settled().askSegments)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun aSessionWithoutTheScopeIsntAsked() = runTest {
        val scope = "atproto transition:generic space:${Nsid.SPACE_ATHLETE}?collection=${Nsid.FEED_ACTIVITY}"
        feedTest(scope) {
            assertFalse(settled().askSegments)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
