package io.github.solcott.activitytracker.app

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withCompositionLocals
import co.touchlab.kermit.Logger
import com.slack.circuit.retained.LocalRetainedStateRegistry
import com.slack.circuit.retained.RetainedStateRegistry
import com.slack.circuit.test.FakeNavigator
import com.slack.circuit.test.presenterTestOf
import io.github.solcott.activitytracker.app.detail.ActivityDetailScreen
import io.github.solcott.activitytracker.app.segment.SegmentCredit
import io.github.solcott.activitytracker.app.segment.SegmentEvent
import io.github.solcott.activitytracker.app.segment.SegmentLeaderboardScreen
import io.github.solcott.activitytracker.app.segment.SegmentPresenter
import io.github.solcott.activitytracker.app.segment.SegmentScreen
import io.github.solcott.activitytracker.app.segment.SegmentState
import io.github.solcott.activitytracker.atproto.OAuthClientConfig
import io.github.solcott.activitytracker.atproto.segmentStarRkey
import io.github.solcott.activitytracker.data.activity.OutboxProcessor
import io.github.solcott.activitytracker.data.activity.OutboxWriter
import io.github.solcott.activitytracker.data.db.AppDatabase
import io.github.solcott.activitytracker.data.di.signedInTestSession
import io.github.solcott.activitytracker.data.segments.SegmentRepository
import io.github.solcott.activitytracker.data.segments.SegmentStars
import io.github.solcott.activitytracker.lexicon.AtUri
import io.github.solcott.activitytracker.lexicon.Did
import io.github.solcott.activitytracker.lexicon.Nsid
import io.github.solcott.activitytracker.location.GeoPoint
import io.github.solcott.activitytracker.location.SegmentGeometry
import io.github.solcott.activitytracker.location.TrackMath
import io.github.solcott.dataresult.DataError
import io.github.solcott.uistate.errorOrNull
import io.github.solcott.uistate.hasLoaded
import io.github.solcott.uistate.isLoading
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.time.Clock
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest

/** The segment screen (segments/segment-efforts): the segment, its line for the map, and the viewer's own efforts fastest first. */
class SegmentPresenterTest {
    private val alice = Did("did:plc:alice")
    private val uri = "at://did:plc:carol/${Nsid.FEED_SEGMENT}/3kshill"
    private val screen = SegmentScreen(uri)
    private val line = SegmentGeometry.encodePolyline(listOf(GeoPoint(47.0, 8.0), GeoPoint(47.009, 8.0)))
    private var failSegment = false
    private var status = "proposed"
    private var duplicateOf = ""
    private var source = "athlete"
    private var license = "CC0-1.0"
    private var attribution: String? = null
    /** The star in alice's `self` space, as the PDS holds it; null for none. */
    private var storedStar: String? = null
    private var pdsDown = false
    private var failEfforts = false
    private val effortsRequested = mutableListOf<String?>()

    private fun effort(rkey: String, elapsed: Int, best: Boolean = false) =
        """{"segment":{"uri":"$uri","name":"Hill","sport":"run","distanceMeters":1000},""" +
            """"activity":"at://$alice/${Nsid.FEED_ACTIVITY}/$rkey",""" +
            """"startOffset":20,"elapsed":$elapsed,"startedOn":"2026-10-0${rkey.last()}"${if (best) ""","best":true""" else ""}}"""

    private val http =
        HttpClient(
            MockEngine { request ->
                val json = headersOf("Content-Type", "application/json")
                when (request.url.encodedPath) {
                    "/xrpc/com.atproto.space.getRecord" ->
                        when {
                            pdsDown -> respond("", HttpStatusCode.InternalServerError)
                            request.url.parameters["collection"] == Nsid.FEED_SEGMENT_STAR && storedStar != null ->
                                respond("""{"uri":"at://$alice/x/y","cid":"bafystar","value":$storedStar}""", headers = json)
                            else -> respond("""{"error":"RecordNotFound"}""", HttpStatusCode.BadRequest, json)
                        }
                    "/xrpc/com.atproto.server.getServiceAuth" -> respond("""{"token":"service-jwt"}""", headers = json)
                    "/xrpc/${Nsid.FEED_GET_SEGMENT}" ->
                        if (failSegment) respond("""{"error":"NotFound","message":"Segment not found"}""", HttpStatusCode.BadRequest, json)
                        else
                            respond(
                                """{"segment":{"uri":"$uri","cid":"bafyseg","creator":{"did":"did:plc:carol","handle":"carol.test"},""" +
                                    """"record":{"name":"Hill","sport":"run","line":"$line","source":"$source","license":"$license",""" +
                                    attribution?.let { """"attribution":"$it",""" }.orEmpty() +
                                    """"createdAt":"2026-10-01T00:00:00Z"},"distanceMeters":1000,"status":"$status"$duplicateOf}}""",
                                headers = json,
                            )
                    "/xrpc/${Nsid.FEED_GET_SEGMENT_EFFORTS}" -> {
                        effortsRequested += request.url.parameters["actor"]
                        if (failEfforts) return@MockEngine respond("", HttpStatusCode.InternalServerError)
                        // Out of order, as a client must not rely on.
                        respond(
                            """{"efforts":[${effort("3ka2", 250)},${effort("3ka1", 200, best = true)},${effort("3ka3", 300)}]}""",
                            headers = json,
                        )
                    }
                    else -> respond("", HttpStatusCode.NotFound)
                }
            }
        ) {
            expectSuccess = false
        }

    private lateinit var db: AppDatabase

    private suspend fun session(scope: String = OAuthClientConfig.DEFAULT_SCOPE): Pair<SegmentRepository, SegmentStars> {
        val (db, _, clients) = signedInTestSession(http, did = alice, scope = scope)
        this.db = db
        val stars = SegmentStars(clients, OutboxWriter(db, clients, Clock.System), OutboxProcessor(db, clients, Logger), Clock.System)
        return SegmentRepository(clients) to stars
    }

    @Test
    fun loadsTheSegmentItsLineAndTheViewersEffortsFastestFirst() = runTest {
        val (segments, stars) = session()
        presenterTestOf({ SegmentPresenter(screen, FakeNavigator(screen), segments, stars) }) {
            assertEquals(true, awaitItem().content.isLoading)
            val state = awaitUntil { it.segment != null && it.efforts.isNotEmpty() }
            assertEquals("Hill", state.segment?.record?.name)
            assertEquals(2, TrackMath.decodePolyline(assertNotNull<String>(state.polyline)).size)
            assertEquals(listOf(200L, 250L, 300L), state.efforts.map { it.elapsed })
            assertEquals<List<String?>>(listOf(alice.value), effortsRequested, "the viewer's own")
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun aFailedLoadIsAnErrorThatRetries() = runTest {
        failSegment = true
        val (segments, stars) = session()
        presenterTestOf({ SegmentPresenter(screen, FakeNavigator(screen), segments, stars) }) {
            val failed = awaitUntil { it.content.errorOrNull != null }
            assertEquals(DataError.Api(listOf("Segment not found"), code = "NotFound"), failed.content.errorOrNull)
            assertEquals(false, failed.content.isLoading)
            assertFalse(failed.content.hasLoaded)
            assertNull(failed.segment)

            failSegment = false
            failed.eventSink(SegmentEvent.Retry)
            awaitUntil { it.content.isLoading }
            val loaded = awaitUntil { it.segment != null }
            assertEquals("Hill", loaded.segment?.record?.name)
            assertNull(loaded.content.errorOrNull)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun effortsThatFailShowTheSegmentWithoutThemOrAnError() = runTest {
        failEfforts = true
        val (segments, stars) = session()
        presenterTestOf({ SegmentPresenter(screen, FakeNavigator(screen), segments, stars) }) {
            val state = awaitUntil { it.segment != null && !it.content.isLoading }
            assertEquals("Hill", state.segment?.record?.name)
            assertEquals(emptyList(), state.efforts)
            assertNull(state.content.errorOrNull)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun comingBackShowsTheSegmentAtOnceAndKeepsItThroughAFailedReload() = runTest {
        val (segments, stars) = session()
        val registry = RetainedStateRegistry()
        var shown by mutableStateOf(true)
        presenterTestOf({
            withCompositionLocals(LocalRetainedStateRegistry provides registry) {
                // A stand-in for the screen it opened while it's on the back stack.
                if (shown) SegmentPresenter(screen, FakeNavigator(screen), segments, stars) else SegmentState()
            }
        }) {
            awaitUntil { it.segment != null && !it.content.isLoading }
            // What the back stack does to a record it leaves.
            registry.saveAll()
            shown = false
            awaitUntil { it.segment == null }
            failSegment = true
            shown = true
            val back = awaitItem()
            assertEquals("Hill", back.segment?.record?.name, "no loading state in between")
            val failed = awaitUntil { it.content.errorOrNull != null }
            assertEquals("Hill", failed.segment?.record?.name)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun anEffortOpensItsActivity() = runTest {
        val navigator = FakeNavigator(screen)
        val (segments, stars) = session()
        presenterTestOf({ SegmentPresenter(screen, navigator, segments, stars) }) {
            val state = awaitUntil { it.efforts.isNotEmpty() }
            state.eventSink(SegmentEvent.OpenActivity(state.efforts.first().activity))
            assertEquals(ActivityDetailScreen("at://$alice/${Nsid.FEED_ACTIVITY}/3ka1"), navigator.awaitNextScreen())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun aDuplicateOpensItsOriginal() = runTest {
        val original = "at://did:plc:bob/${Nsid.FEED_SEGMENT}/3ksfirst"
        status = "duplicate"
        duplicateOf = ""","duplicateOf":"$original""""
        val navigator = FakeNavigator(screen)
        val (segments, stars) = session()
        presenterTestOf({ SegmentPresenter(screen, navigator, segments, stars) }) {
            val state = awaitUntil { it.segment != null }
            assertEquals("duplicate", state.segment?.status)
            state.eventSink(SegmentEvent.OpenOriginal)
            assertEquals(SegmentScreen(original), navigator.awaitNextScreen())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun anActiveSegmentOpensItsLeaderboard() = runTest {
        status = "active"
        val (segments, stars) = session()
        val navigator = FakeNavigator(screen)
        presenterTestOf({ SegmentPresenter(screen, navigator, segments, stars) }) {
            awaitUntil { it.segment != null }.eventSink(SegmentEvent.OpenLeaderboard)
            assertEquals(SegmentLeaderboardScreen(uri, "Hill", "run"), navigator.awaitNextScreen())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun aProposedSegmentHasNoLeaderboard() = runTest {
        val (segments, stars) = session()
        val navigator = FakeNavigator(screen)
        presenterTestOf({ SegmentPresenter(screen, navigator, segments, stars) }) {
            awaitUntil { it.segment != null }.eventSink(SegmentEvent.OpenLeaderboard)
            navigator.expectNoGoToEvents()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun theStarShowsWhatSelfHolds() = runTest {
        storedStar = """{"subject":{"uri":"$uri","cid":"bafyseg"},"createdAt":"2026-10-21T12:00:00Z"}"""
        val (segments, stars) = session()
        presenterTestOf({ SegmentPresenter(screen, FakeNavigator(screen), segments, stars) }) {
            assertEquals(true, awaitUntil { it.starred != null }.starred)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun anUnreadableStarIsntShownAsUnstarred() = runTest {
        pdsDown = true
        val (segments, stars) = session()
        presenterTestOf({ SegmentPresenter(screen, FakeNavigator(screen), segments, stars) }) {
            val state = awaitUntil { it.segment != null && it.efforts.isNotEmpty() }
            assertNull(state.starred)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun aSessionWithoutTheGrantIsntOfferedTheStar() = runTest {
        val (segments, stars) = session(scope = "${OAuthClientConfig.DEFAULT_SCOPE}&collection=${Nsid.FEED_ACTIVITY}")
        presenterTestOf({ SegmentPresenter(screen, FakeNavigator(screen), segments, stars) }) {
            val state = awaitUntil { it.segment != null && it.efforts.isNotEmpty() }
            assertNull(state.starred)
            assertEquals("Hill", state.segment?.record?.name, "the segment shows as usual")
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun togglingStarsAtOnceAndQueuesOneWrite() = runTest {
        val (segments, stars) = session()
        presenterTestOf({ SegmentPresenter(screen, FakeNavigator(screen), segments, stars) }) {
            val unstarred = awaitUntil { it.segment != null && it.starred == false }
            unstarred.eventSink(SegmentEvent.ToggleStar)
            unstarred.eventSink(SegmentEvent.ToggleStar)
            assertEquals(true, awaitUntil { it.starred == true }.starred)
            // Shown at once, queued just after.
            db.outbox().count(alice.value).first { it > 0 }
            val queued = db.outbox().pending(alice.value)
            assertEquals(
                listOf("put ${Nsid.FEED_SEGMENT_STAR}"),
                queued.map { "${it.operation} ${it.collection}" },
                "a double tap stars once",
            )
            assertEquals(segmentStarRkey(AtUri(uri)), queued.single().rkey)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun unstarringQueuesTheDelete() = runTest {
        storedStar = """{"subject":{"uri":"$uri","cid":"bafyseg"},"createdAt":"2026-10-21T12:00:00Z"}"""
        val (segments, stars) = session()
        presenterTestOf({ SegmentPresenter(screen, FakeNavigator(screen), segments, stars) }) {
            awaitUntil { it.segment != null && it.starred == true }.eventSink(SegmentEvent.ToggleStar)
            assertEquals(false, awaitUntil { it.starred == false }.starred)
            db.outbox().count(alice.value).first { it > 0 }
            assertEquals(
                listOf("delete ${Nsid.FEED_SEGMENT_STAR}"),
                db.outbox().pending(alice.value).map { "${it.operation} ${it.collection}" },
            )
            cancelAndIgnoreRemainingEvents()
        }
    }

    /** The credit for the segment's data (segments/segment-records), once loaded; [source] and [license] set the record's. */
    private suspend fun creditFor(source: String, license: String, attribution: String? = null): SegmentCredit? {
        this.source = source
        this.license = license
        this.attribution = attribution
        val (segments, stars) = session()
        var credit: SegmentCredit? = null
        presenterTestOf({ SegmentPresenter(screen, FakeNavigator(screen), segments, stars) }) {
            credit = awaitUntil { it.segment != null }.credit
            cancelAndIgnoreRemainingEvents()
        }
        return credit
    }

    @Test fun anAthleteSegmentHasNoDataCredit() = runTest { assertNull(creditFor("athlete", "CC0-1.0")) }

    @Test
    fun anOsmSegmentCreditsOpenStreetMap() = runTest { assertEquals(SegmentCredit.OpenStreetMap("ODbL-1.0"), creditFor("osm", "ODbL-1.0")) }

    @Test fun anAgencySegmentCreditsTheAgency() = runTest { assertEquals(SegmentCredit.Agency("CC0-1.0"), creditFor("agency", "CC0-1.0")) }

    @Test
    fun anAgencySegmentNamesItsAttribution() = runTest {
        assertEquals(
            SegmentCredit.Agency("CC0-1.0", "USDA Forest Service"),
            creditFor("agency", "CC0-1.0", attribution = "USDA Forest Service"),
        )
        assertEquals(SegmentCredit.Agency("CC0-1.0"), creditFor("agency", "CC0-1.0", attribution = "  "))
    }

    @Test
    fun anAthleteSegmentsAttributionIsIgnored() = runTest {
        assertNull(creditFor("athlete", "CC0-1.0", attribution = "USDA Forest Service"))
    }

    @Test
    fun anUnknownSourceShowsOnlyItsLicence() = runTest {
        assertEquals(SegmentCredit.License("CC-BY-4.0"), creditFor("survey", "CC-BY-4.0"))
    }

    @Test fun anUnknownSourceWithABlankLicenceShowsNothing() = runTest { assertNull(creditFor("survey", " ")) }
}
