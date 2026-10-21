package io.github.solcott.activitytracker.app

import androidx.compose.runtime.Composable
import app.cash.turbine.ReceiveTurbine
import co.touchlab.kermit.Logger
import com.slack.circuit.test.FakeNavigator
import com.slack.circuit.test.presenterTestOf
import io.github.solcott.activitytracker.app.detail.ActivityDetailEvent
import io.github.solcott.activitytracker.app.detail.ActivityDetailPresenter
import io.github.solcott.activitytracker.app.detail.ActivityDetailScreen
import io.github.solcott.activitytracker.app.detail.ActivityDetailState
import io.github.solcott.activitytracker.data.activity.ActivityExporter
import io.github.solcott.activitytracker.data.activity.ActivityManager
import io.github.solcott.activitytracker.data.activity.ActivityPlaces
import io.github.solcott.activitytracker.data.activity.ActivityRepository
import io.github.solcott.activitytracker.data.activity.CombineSuggestions
import io.github.solcott.activitytracker.data.activity.PrivacyZones
import io.github.solcott.activitytracker.data.activity.RouteTrimmer
import io.github.solcott.activitytracker.data.di.signedInTestSession
import io.github.solcott.activitytracker.data.feed.FeedRepository
import io.github.solcott.activitytracker.data.feed.LocalLikes
import io.github.solcott.activitytracker.data.profile.ProfileRepository
import io.github.solcott.activitytracker.data.sport.RecentActivityTypes
import io.github.solcott.activitytracker.data.units.UnitSystem
import io.github.solcott.activitytracker.data.units.UnitsRepository
import io.github.solcott.activitytracker.lexicon.AtJson
import io.github.solcott.activitytracker.lexicon.Did
import io.github.solcott.activitytracker.lexicon.Nsid
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import io.ktor.http.headersOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** The owner deleting and ungrouping an activity from its page. */
class ActivityDetailManageTest {
    private val alice = Did("did:plc:alice")
    private val aliceProfile = """{"did":"did:plc:alice","handle":"alice.test"}"""
    private val bobProfile = """{"did":"did:plc:bob","handle":"bob.test"}"""

    private fun uri(did: Did, rkey: String) = "at://${did.value}/${Nsid.FEED_ACTIVITY}/$rkey"

    private val main = uri(alice, "3kmain")

    /** The rkeys of [main]'s recordings, each a recording of it. */
    private var recordings = listOf("3krec1")

    /** Holds each `deleteRecord` until completed, so the test sees the delete in flight. */
    private var gate = CompletableDeferred(Unit)
    private var failGetRecord = false

    /** Every write the PDS took, in order, as "put|delete collection/rkey". */
    private val writes = mutableListOf<String>()

    private fun record(recordingOf: String?) =
        """{"sport":"dive","startedAt":"2026-09-30T10:00:00Z","elapsedTime":1800,"createdAt":"2026-09-30T10:31:00Z"""" +
            recordingOf?.let { ""","recordingOf":"$it"""" }.orEmpty() +
            "}"

    private fun view(uri: String): String {
        val rkey = uri.substringAfterLast('/')
        val author = if (uri.startsWith("at://${alice.value}")) aliceProfile else bobProfile
        val isMain = rkey == "3kmain" || rkey == "3kbob"
        val list =
            if (isMain && recordings.isNotEmpty()) {
                ""","recordings":[""" +
                    recordings.joinToString(",") { """{"uri":"${uri(alice, it)}","startedAt":"2026-09-30T10:00:00Z"}""" } +
                    "]"
            } else {
                ""
            }
        return """{"uri":"$uri","cid":"bafy$rkey","author":$author,"record":${record(if (isMain) null else main)},""" +
            """"likeCount":0,"commentCount":0,"indexedAt":"2026-09-30T10:31:01Z","viewer":{}$list}"""
    }

    private fun String.target(): String {
        val body = AtJson.parseToJsonElement(this).jsonObject
        return "${body["collection"]?.jsonPrimitive?.content}/${body["rkey"]?.jsonPrimitive?.content}"
    }

    private val http =
        HttpClient(
            MockEngine { request ->
                val json = headersOf("Content-Type", "application/json")
                when (request.url.encodedPath) {
                    "/xrpc/com.atproto.server.getServiceAuth" -> {
                        respond("""{"token":"service-jwt"}""", headers = json)
                    }

                    "/xrpc/${Nsid.FEED_GET_ACTIVITY}" -> {
                        respond("""{"activity":${view(request.url.parameters["uri"]!!)},"comments":[]}""", headers = json)
                    }

                    "/xrpc/com.atproto.repo.getRecord" -> {
                        if (failGetRecord) {
                            respond("""{"error":"InternalServerError"}""", HttpStatusCode.InternalServerError, json)
                        } else {
                            val rkey = request.url.parameters["rkey"]!!
                            respond("""{"uri":"${uri(alice, rkey)}","cid":"bafyold","value":${record(main)}}""", headers = json)
                        }
                    }

                    "/xrpc/com.atproto.repo.putRecord" -> {
                        writes += "put " + (request.body as OutgoingContent.ByteArrayContent).bytes().decodeToString().target()
                        respond("""{"uri":"at://$alice/x/y","cid":"bafyrec"}""", headers = json)
                    }

                    "/xrpc/com.atproto.repo.deleteRecord" -> {
                        gate.await()
                        writes += "delete " + (request.body as OutgoingContent.ByteArrayContent).bytes().decodeToString().target()
                        respond("{}", headers = json)
                    }

                    else -> {
                        respond("""{"error":"NotFound"}""", HttpStatusCode.NotFound, json)
                    }
                }
            }
        ) {
            expectSuccess = false
        }

    private val localLikes = LocalLikes()

    @Suppress("LongMethod") // Wires every store a test reaches for.
    private suspend fun presenter(
        uri: String,
        navigator: FakeNavigator = FakeNavigator(ActivityDetailScreen(uri)),
    ): @Composable () -> ActivityDetailState {
        val (db, _, clients) = signedInTestSession(http, did = alice)
        val feed = FeedRepository(db, clients, localLikes, Logger)
        val zones = TestZones(db, clients)
        val (outbox, writer, streams) = Triple(zones.outbox, zones.writer, zones.streams)
        val tracks = zones.tracks
        val privacy = PrivacyZones(clients, Clock.System, Logger)
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
        val profiles = ProfileRepository(clients, Clock.System, Logger, zones.store)
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
                ActivityPlaces(clients, Clock.System, Logger),
                feed,
                privacy,
                Clock.System,
                activities,
                testSegments(db, clients, writer, outbox, privacy, tracks).tracks,
            )
        return {
            ActivityDetailPresenter(
                ActivityDetailScreen(uri),
                navigator,
                feed,
                activities,
                profiles,
                localLikes,
                manager,
                ActivityExporter(db, clients, tracks, zones.sources, feed),
                UnitsRepository(profiles, Clock.System, Logger, UnitSystem.Metric),
                CombineSuggestions(db, clients, feed, tracks, Clock.System),
            )
        }
    }

    @Test
    fun someoneElsesActivityCantBeManaged() = runTest {
        presenterTestOf(presenter(uri(Did("did:plc:bob"), "3kbob"))) {
            assertNull(awaitLoaded().manage)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun onlyAGroupOfTwoCanBeUngrouped() = runTest {
        presenterTestOf(presenter(main)) {
            val manage = assertNotNull(awaitLoaded().manage)
            assertTrue(manage.canUngroup)
            assertEquals(1, manage.otherRecordings)
            cancelAndIgnoreRemainingEvents()
        }
        presenterTestOf(presenter(uri(alice, "3krec1"))) {
            assertNotNull(awaitUntil { (it as? ActivityDetailState.Loaded)?.manage?.canUngroup == true })
            cancelAndIgnoreRemainingEvents()
        }

        recordings = listOf("3krec1", "3krec2")
        presenterTestOf(presenter(main)) {
            val manage = assertNotNull(awaitLoaded().manage)
            assertFalse(manage.canUngroup)
            assertEquals(2, manage.otherRecordings)
            cancelAndIgnoreRemainingEvents()
        }
        presenterTestOf(presenter(uri(alice, "3krec1"))) {
            assertFalse(assertNotNull(awaitLoaded().manage).canUngroup)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun deletingLeavesThePageAndATapWhileItRunsDoesNothing() = runTest {
        recordings = emptyList()
        gate = CompletableDeferred()
        val navigator = FakeNavigator(ActivityDetailScreen(main))
        presenterTestOf(presenter(main, navigator)) {
            val loaded = awaitLoaded()
            assertEquals(0, loaded.manage?.otherRecordings)
            loaded.eventSink(ActivityDetailEvent.Delete(withRecordings = false))
            awaitUntil { (it as? ActivityDetailState.Loaded)?.manage?.busy == true }
            loaded.eventSink(ActivityDetailEvent.Delete(withRecordings = false))

            gate.complete(Unit)
            navigator.awaitPop()
            assertEquals(listOf("delete ${Nsid.FEED_ACTIVITY}/3kmain", "delete ${Nsid.FEED_ACTIVITY_STREAMS}/3kmain"), writes)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun deletingOnlyTheMainActivityKeepsItsRecordings() = runTest {
        val navigator = FakeNavigator(ActivityDetailScreen(main))
        presenterTestOf(presenter(main, navigator)) {
            awaitLoaded().eventSink(ActivityDetailEvent.Delete(withRecordings = false))
            navigator.awaitPop()
            assertEquals(
                listOf(
                    "put ${Nsid.FEED_ACTIVITY}/3krec1",
                    "delete ${Nsid.FEED_ACTIVITY}/3kmain",
                    "delete ${Nsid.FEED_ACTIVITY_STREAMS}/3kmain",
                ),
                writes,
            )
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun ungroupingStaysOnThePageWithTheRecordingGone() = runTest {
        val navigator = FakeNavigator(ActivityDetailScreen(main))
        presenterTestOf(presenter(main, navigator)) {
            awaitLoaded().eventSink(ActivityDetailEvent.Ungroup)
            val ungrouped = awaitUntil { (it as? ActivityDetailState.Loaded)?.activity?.recordings == null } as ActivityDetailState.Loaded
            assertFalse(assertNotNull(ungrouped.manage).canUngroup)
            assertEquals(listOf("put ${Nsid.FEED_ACTIVITY}/3krec1"), writes)
            navigator.assertPopIsEmpty()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun aFailedDeleteShowsAnErrorAndStays() = runTest {
        failGetRecord = true
        val navigator = FakeNavigator(ActivityDetailScreen(main))
        presenterTestOf(presenter(main, navigator)) {
            awaitLoaded().eventSink(ActivityDetailEvent.Delete(withRecordings = false))
            val failed = awaitUntil { (it as? ActivityDetailState.Loaded)?.manage?.error != null } as ActivityDetailState.Loaded
            assertEquals(UiError.Kind.DeleteActivity, failed.manage?.error?.kind)
            assertTrue(writes.isEmpty())
            navigator.assertPopIsEmpty()
            cancelAndIgnoreRemainingEvents()
        }
    }

    private suspend fun ReceiveTurbine<ActivityDetailState>.awaitLoaded() =
        awaitUntil { it is ActivityDetailState.Loaded } as ActivityDetailState.Loaded
}
