package io.github.solcott.activitytracker.app

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withCompositionLocals
import app.cash.turbine.Event
import co.touchlab.kermit.Logger
import com.slack.circuit.retained.LocalRetainedStateRegistry
import com.slack.circuit.retained.RetainedStateRegistry
import com.slack.circuit.test.FakeNavigator
import io.github.solcott.activitytracker.app.detail.ActivityDetailScreen
import io.github.solcott.activitytracker.app.segment.SegmentCredit
import io.github.solcott.activitytracker.app.segment.SegmentEvent
import io.github.solcott.activitytracker.app.segment.SegmentLeaderboardScreen
import io.github.solcott.activitytracker.app.segment.SegmentMapEvent
import io.github.solcott.activitytracker.app.segment.SegmentMapPresenter
import io.github.solcott.activitytracker.app.segment.SegmentMapScreen
import io.github.solcott.activitytracker.app.segment.SegmentMayorsScreen
import io.github.solcott.activitytracker.app.segment.SegmentPresenter
import io.github.solcott.activitytracker.app.segment.SegmentScreen
import io.github.solcott.activitytracker.app.segment.SegmentState
import io.github.solcott.activitytracker.atproto.OAuthClientConfig
import io.github.solcott.activitytracker.atproto.segmentStarRkey
import io.github.solcott.activitytracker.data.activity.OutboxProcessor
import io.github.solcott.activitytracker.data.activity.OutboxWriter
import io.github.solcott.activitytracker.data.db.AppDatabase
import io.github.solcott.activitytracker.data.di.signedInTestSession
import io.github.solcott.activitytracker.data.segments.SegmentNames
import io.github.solcott.activitytracker.data.segments.SegmentRepository
import io.github.solcott.activitytracker.data.segments.SegmentStars
import io.github.solcott.activitytracker.lexicon.AtUri
import io.github.solcott.activitytracker.lexicon.Did
import io.github.solcott.activitytracker.lexicon.Nsid
import io.github.solcott.activitytracker.lexicon.app.SegmentNameVoteDirection
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
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlinx.coroutines.CompletableDeferred
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
    /** The Mayors board's entries, as JSON, and the viewer's own entry; null fails the read. */
    private var mayors: String? = """{"rank":1,"athlete":{"did":"did:plc:bob","handle":"bob.test"},"efforts":14,"reachedOn":"2026-10-18"}"""
    private var ownMayor: String? = """{"rank":7,"athlete":{"did":"$alice","handle":"alice.test"},"efforts":6,"reachedOn":"2026-10-20"}"""
    private val mayorsRequested = mutableListOf<Map<String, String>>()
    /** Completed once the Mayors board has been asked for. */
    private val mayorsAsked = CompletableDeferred<Unit>()

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
                                    """"createdAt":"2026-10-01T00:00:00Z"},"distanceMeters":1000,"status":"$status"$duplicateOf$naming}}""",
                                headers = json,
                            )
                    "/xrpc/${Nsid.FEED_GET_SEGMENT_MAYORS}" -> {
                        mayorsRequested += request.url.parameters.entries().associate { it.key to it.value.single() }
                        mayorsAsked.complete(Unit)
                        val entries = mayors ?: return@MockEngine respond("", HttpStatusCode.InternalServerError)
                        respond("""{"entries":[$entries]${ownMayor?.let { ",\"viewer\":$it" }.orEmpty()}}""", headers = json)
                    }
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
    private lateinit var names: SegmentNames
    /** Extra fields of the served segment view, after `status`: its names, proposals and viewer flags. */
    private var naming = ""

    private suspend fun session(scope: String = OAuthClientConfig.DEFAULT_SCOPE): Pair<SegmentRepository, SegmentStars> {
        val (db, _, clients) = signedInTestSession(http, did = alice, scope = scope)
        this.db = db
        val stars = SegmentStars(clients, OutboxWriter(db, clients, Clock.System), OutboxProcessor(db, clients, Logger), Clock.System)
        names = SegmentNames(clients, OutboxWriter(db, clients, Clock.System), OutboxProcessor(db, clients, Logger), Clock.System)
        return SegmentRepository(clients) to stars
    }

    @Test
    fun loadsTheSegmentItsLineAndTheViewersEffortsFastestFirst() = runTest {
        val (segments, stars) = session()
        presenterTestOf({ SegmentPresenter(screen, FakeNavigator(screen), segments, stars, names) }) {
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
        presenterTestOf({ SegmentPresenter(screen, FakeNavigator(screen), segments, stars, names) }) {
            val failed = awaitUntil { it.content.errorOrNull != null }
            assertEquals(DataError.Api(listOf("Segment not found"), code = "NotFound"), failed.content.errorOrNull)
            assertEquals(false, failed.content.isLoading)
            assertFalse(failed.content.hasLoaded)
            assertNull(failed.segment)

            failSegment = false
            failed.eventSink(SegmentEvent.Retry)
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
        presenterTestOf({ SegmentPresenter(screen, FakeNavigator(screen), segments, stars, names) }) {
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
                if (shown) SegmentPresenter(screen, FakeNavigator(screen), segments, stars, names) else SegmentState()
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
        presenterTestOf({ SegmentPresenter(screen, navigator, segments, stars, names) }) {
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
        presenterTestOf({ SegmentPresenter(screen, navigator, segments, stars, names) }) {
            val state = awaitUntil { it.segment != null }
            assertEquals("duplicate", state.segment?.status)
            state.eventSink(SegmentEvent.OpenOriginal)
            assertEquals(SegmentScreen(original), navigator.awaitNextScreen())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun theMapOpensTheEnlargedMap() = runTest {
        val navigator = FakeNavigator(screen)
        val (segments, stars) = session()
        presenterTestOf({ SegmentPresenter(screen, navigator, segments, stars, names) }) {
            awaitUntil { it.segment != null }.eventSink(SegmentEvent.OpenMap)
            assertEquals(SegmentMapScreen(uri), navigator.awaitNextScreen())
            cancelAndIgnoreRemainingEvents()
        }
    }

    // The enlarged map (segments/segment-map-viewer): the segment screen's line, the name and the data credit.

    @Test
    fun theEnlargedMapDrawsTheLineNamedAndCredited() = runTest {
        source = "osm"
        license = "ODbL-1.0"
        val (segments, _) = session()
        val mapScreen = SegmentMapScreen(uri)
        presenterTestOf({ SegmentMapPresenter(mapScreen, FakeNavigator(mapScreen), segments) }) {
            val content = assertNotNull(awaitUntil { it.content.data != null }.content.data)
            assertEquals(2, TrackMath.decodePolyline(assertNotNull<String>(content.polyline)).size)
            assertEquals("Hill", content.name)
            assertEquals(SegmentCredit.OpenStreetMap("ODbL-1.0"), content.credit)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun anAthleteSegmentsEnlargedMapHasNoCredit() = runTest {
        val (segments, _) = session()
        val mapScreen = SegmentMapScreen(uri)
        presenterTestOf({ SegmentMapPresenter(mapScreen, FakeNavigator(mapScreen), segments) }) {
            assertNull(assertNotNull(awaitUntil { it.content.data != null }.content.data).credit)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun theEnlargedMapsFailedLoadIsAnErrorThatRetries() = runTest {
        failSegment = true
        val (segments, _) = session()
        val mapScreen = SegmentMapScreen(uri)
        presenterTestOf({ SegmentMapPresenter(mapScreen, FakeNavigator(mapScreen), segments) }) {
            val failed = awaitUntil { it.content.errorOrNull != null }
            assertEquals(DataError.Api(listOf("Segment not found"), code = "NotFound"), failed.content.errorOrNull)
            assertFalse(failed.content.hasLoaded)

            failSegment = false
            failed.eventSink(SegmentMapEvent.Retry)
            assertEquals("Hill", awaitUntil { it.content.data != null }.content.data?.name)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun backLeavesTheEnlargedMap() = runTest {
        val (segments, _) = session()
        val mapScreen = SegmentMapScreen(uri)
        val navigator = FakeNavigator(mapScreen)
        presenterTestOf({ SegmentMapPresenter(mapScreen, navigator, segments) }) {
            awaitItem().eventSink(SegmentMapEvent.Back)
            navigator.awaitPop()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun anActiveSegmentOpensItsLeaderboard() = runTest {
        status = "active"
        val (segments, stars) = session()
        val navigator = FakeNavigator(screen)
        presenterTestOf({ SegmentPresenter(screen, navigator, segments, stars, names) }) {
            awaitUntil { it.segment != null }.eventSink(SegmentEvent.OpenLeaderboard)
            assertEquals(SegmentLeaderboardScreen(uri, "Hill"), navigator.awaitNextScreen())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun aProposedSegmentHasNoLeaderboard() = runTest {
        val (segments, stars) = session()
        val navigator = FakeNavigator(screen)
        presenterTestOf({ SegmentPresenter(screen, navigator, segments, stars, names) }) {
            awaitUntil { it.segment != null }.eventSink(SegmentEvent.OpenLeaderboard)
            navigator.expectNoGoToEvents()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun anActiveSegmentShowsItsMayorAndOpensTheBoard() = runTest {
        status = "active"
        val (segments, stars) = session()
        val navigator = FakeNavigator(screen)
        presenterTestOf({ SegmentPresenter(screen, navigator, segments, stars, names) }) {
            val state = awaitUntil { it.mayor != null }
            assertEquals("bob.test", state.mayor?.leader?.athlete?.handle?.value)
            assertEquals(14, state.mayor?.leader?.efforts)
            assertEquals(6, state.mayor?.viewer?.efforts)
            assertEquals(
                mapOf("segment" to uri, "period" to "last90Days", "scope" to "everyone", "limit" to "1"),
                mayorsRequested.single(),
            )
            state.eventSink(SegmentEvent.OpenMayors)
            assertEquals(SegmentMayorsScreen(uri, "Hill"), navigator.awaitNextScreen())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun aCarriedTypeReachesTheMayorAndBothBoards() = runTest {
        status = "active"
        val (segments, stars) = session()
        val navigator = FakeNavigator(screen)
        val canoe = SegmentScreen(uri, board = "canoe")
        presenterTestOf({ SegmentPresenter(canoe, navigator, segments, stars, names) }) {
            val state = awaitUntil { it.mayor != null }
            assertEquals("canoe", mayorsRequested.single()["board"])
            state.eventSink(SegmentEvent.OpenMayors)
            assertEquals(SegmentMayorsScreen(uri, "Hill", board = "canoe"), navigator.awaitNextScreen())
            state.eventSink(SegmentEvent.OpenLeaderboard)
            assertEquals(SegmentLeaderboardScreen(uri, "Hill", board = "canoe"), navigator.awaitNextScreen())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun anActiveSegmentWithNoEffortsHasNoMayorYet() = runTest {
        status = "active"
        mayors = ""
        ownMayor = null
        val (segments, stars) = session()
        presenterTestOf({ SegmentPresenter(screen, FakeNavigator(screen), segments, stars, names) }) {
            val state = awaitUntil { it.mayor != null }
            assertNull(state.mayor?.leader)
            assertNull(state.mayor?.viewer)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun aMayorThatFailsToLoadShowsTheSegmentWithoutIt() = runTest {
        status = "active"
        mayors = null
        val (segments, stars) = session()
        presenterTestOf({ SegmentPresenter(screen, FakeNavigator(screen), segments, stars, names) }) {
            val state = awaitUntil { it.segment != null && !it.content.isLoading }
            // A failure changes nothing, so there's no state to wait for: only the request, then no state may carry a Mayor.
            mayorsAsked.await()
            assertNull(state.mayor)
            assertNull(state.content.errorOrNull)
            val rest = cancelAndConsumeRemainingEvents().filterIsInstance<Event.Item<SegmentState>>().map { it.value }
            assertEquals(emptyList(), rest.mapNotNull { it.mayor })
            assertEquals(emptyList(), rest.mapNotNull { it.content.errorOrNull })
        }
    }

    @Test
    fun aProposedSegmentHasNoMayor() = runTest {
        val (segments, stars) = session()
        val navigator = FakeNavigator(screen)
        presenterTestOf({ SegmentPresenter(screen, navigator, segments, stars, names) }) {
            val state = awaitUntil { it.segment != null && it.efforts.isNotEmpty() }
            assertNull(state.mayor)
            assertEquals(emptyList(), mayorsRequested)
            state.eventSink(SegmentEvent.OpenMayors)
            navigator.expectNoGoToEvents()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun theStarShowsWhatSelfHolds() = runTest {
        storedStar = """{"subject":{"uri":"$uri","cid":"bafyseg"},"createdAt":"2026-10-21T12:00:00Z"}"""
        val (segments, stars) = session()
        presenterTestOf({ SegmentPresenter(screen, FakeNavigator(screen), segments, stars, names) }) {
            assertEquals(true, awaitUntil { it.starred != null }.starred)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun anUnreadableStarIsntShownAsUnstarred() = runTest {
        pdsDown = true
        val (segments, stars) = session()
        presenterTestOf({ SegmentPresenter(screen, FakeNavigator(screen), segments, stars, names) }) {
            val state = awaitUntil { it.segment != null && it.efforts.isNotEmpty() }
            assertNull(state.starred)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun aSessionWithoutTheGrantIsntOfferedTheStar() = runTest {
        val (segments, stars) = session(scope = "${OAuthClientConfig.DEFAULT_SCOPE}&collection=${Nsid.FEED_ACTIVITY}")
        presenterTestOf({ SegmentPresenter(screen, FakeNavigator(screen), segments, stars, names) }) {
            val state = awaitUntil { it.segment != null && it.efforts.isNotEmpty() }
            assertNull(state.starred)
            assertEquals("Hill", state.segment?.record?.name, "the segment shows as usual")
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun togglingStarsAtOnceAndQueuesOneWrite() = runTest {
        val (segments, stars) = session()
        presenterTestOf({ SegmentPresenter(screen, FakeNavigator(screen), segments, stars, names) }) {
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
        presenterTestOf({ SegmentPresenter(screen, FakeNavigator(screen), segments, stars, names) }) {
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
        presenterTestOf({ SegmentPresenter(screen, FakeNavigator(screen), segments, stars, names) }) {
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

    // ---------------------------------------------------------------- Community names (segments/segment-names)

    private val bobsProposal = "at://did:plc:bob/${Nsid.FEED_SEGMENT_NAME_PROPOSAL}/3kbob"
    private val alicesProposal = "at://$alice/${Nsid.FEED_SEGMENT_NAME_PROPOSAL}/3kali"

    @Suppress("LongParameterList") // A proposal view's fields, as JSON.
    private fun proposal(uri: String, did: String, name: String, up: Int, down: Int, vote: String? = null) =
        """{"uri":"$uri","cid":"bafy${uri.takeLast(4)}","name":"$name","proposer":{"did":"$did","handle":"x.test"},""" +
            """"openedAt":"2026-10-20T00:00:00Z","up":$up,"down":$down""" +
            vote.orEmpty() +
            "}"

    /** A segment renamed "Wiggly Woods", with Bob's and Alice's open proposals; [canPropose] and [canVote] are Alice's flags. */
    private fun renamed(canPropose: Boolean = false, canVote: Boolean = true, bobVote: String? = null, bobUp: Int = 3) {
        status = "active"
        naming =
            ""","name":"Wiggly Woods",""" +
                """"nameHistory":[{"name":"Wiggly Woods","adoptedAt":"2026-09-01T00:00:00Z","up":6,"down":1,""" +
                """"proposer":{"did":"$alice","handle":"alice.test"}}],""" +
                """"nameProposals":[${proposal(bobsProposal, "did:plc:bob", "Rooty Run", bobUp, 1, bobVote)},""" +
                """${proposal(alicesProposal, alice.value, "Rooty Ridge", 2, 0)}],""" +
                """"viewer":{"canProposeName":$canPropose,"canVoteOnNames":$canVote}"""
    }

    @Test
    fun aRenamedSegmentShowsItsCurrentAndOriginalNames() = runTest {
        renamed()
        val (segments, stars) = session()
        presenterTestOf({ SegmentPresenter(screen, FakeNavigator(screen), segments, stars, names) }) {
            val state = awaitUntil { it.segment != null }
            assertEquals("Wiggly Woods", state.segment?.currentName)
            assertEquals("Hill", state.originalName)
            assertEquals(listOf("Wiggly Woods"), state.segment?.nameHistory?.map { it.name })
            assertEquals(alice.value, state.self)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun anUpVoteShowsAtOnceAndCountsOnceWhenTheAppViewCatchesUp() = runTest {
        renamed()
        val (segments, stars) = session()
        presenterTestOf({ SegmentPresenter(screen, FakeNavigator(screen), segments, stars, names) }) {
            val loaded = awaitUntil { it.segment != null }
            assertTrue(loaded.canVote && loaded.canVoteHere)
            loaded.eventSink(SegmentEvent.Vote(AtUri(bobsProposal), SegmentNameVoteDirection.UP))
            val voted = awaitUntil { s -> s.segment?.nameProposals?.first()?.viewerVote != null }
            val bob = voted.segment!!.nameProposals.first()
            assertEquals(4 to 1, bob.up to bob.down)

            val voteUri = bob.viewerVote!!.uri.value
            renamed(bobVote = ""","viewerVote":{"uri":"$voteUri","direction":"up"}""", bobUp = 4)
            voted.eventSink(SegmentEvent.Retry)
            val caughtUp = awaitUntil { s -> s.segment?.nameProposals?.first()?.let { it.up == 4 && !s.content.isLoading } == true }
            assertEquals(4 to 1, caughtUp.segment!!.nameProposals.first().let { it.up to it.down }, "counted once")
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun aSessionWithoutVotesInItsGrantCantVote() = runTest {
        renamed()
        val (segments, stars) = session(scope = "${OAuthClientConfig.DEFAULT_SCOPE}&collection=${Nsid.FEED_ACTIVITY}")
        presenterTestOf({ SegmentPresenter(screen, FakeNavigator(screen), segments, stars, names) }) {
            val state = awaitUntil { it.segment != null }
            assertTrue(state.canVote)
            assertFalse(state.canVoteHere)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun proposeIsOfferedOnlyWhenTheViewerMayPropose() = runTest {
        renamed(canPropose = false)
        val (segments, stars) = session()
        presenterTestOf({ SegmentPresenter(screen, FakeNavigator(screen), segments, stars, names) }) {
            val state = awaitUntil { it.segment != null }
            assertFalse(state.canPropose)
            state.eventSink(SegmentEvent.ProposeName)
            expectNoEvents()
            cancelAndIgnoreRemainingEvents()
        }
        renamed(canPropose = true)
        presenterTestOf({ SegmentPresenter(screen, FakeNavigator(screen), segments, stars, names) }) {
            val state = awaitUntil { it.segment?.viewer?.canProposeName == true }
            state.eventSink(SegmentEvent.ProposeName)
            val sheet = awaitUntil { it.nameSheet != null }.nameSheet!!
            assertEquals("Wiggly Woods", sheet.currentName)
            assertNull(sheet.proposal)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun deletingOnesOwnProposalAsksFirstThenHidesIt() = runTest {
        renamed()
        val (segments, stars) = session()
        presenterTestOf({ SegmentPresenter(screen, FakeNavigator(screen), segments, stars, names) }) {
            val state = awaitUntil { it.segment != null }
            state.eventSink(SegmentEvent.DeleteProposal(AtUri(alicesProposal)))
            val asking = awaitUntil { it.confirmDelete != null }
            asking.eventSink(SegmentEvent.ConfirmDelete(true))
            val deleted = awaitUntil { s ->
                s.confirmDelete == null && s.segment?.nameProposals?.none { it.uri.value == alicesProposal } == true
            }
            assertEquals(listOf(bobsProposal), deleted.segment!!.nameProposals.map { it.uri.value })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun editingOnesOwnProposalOpensTheSheetWithItsName() = runTest {
        renamed()
        val (segments, stars) = session()
        presenterTestOf({ SegmentPresenter(screen, FakeNavigator(screen), segments, stars, names) }) {
            val state = awaitUntil { it.segment != null }
            state.eventSink(SegmentEvent.EditProposal(AtUri(alicesProposal)))
            val sheet = awaitUntil { it.nameSheet != null }.nameSheet!!
            assertEquals(alicesProposal, sheet.proposal)
            assertEquals("Rooty Ridge", sheet.proposalName)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun someoneElsesProposalOffersNoEditOrDelete() = runTest {
        renamed()
        val (segments, stars) = session()
        presenterTestOf({ SegmentPresenter(screen, FakeNavigator(screen), segments, stars, names) }) {
            val state = awaitUntil { it.segment != null }
            state.eventSink(SegmentEvent.EditProposal(AtUri(bobsProposal)))
            state.eventSink(SegmentEvent.DeleteProposal(AtUri(bobsProposal)))
            expectNoEvents()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun anAdoptedNameOffersNoEdit() = runTest {
        renamed()
        val (segments, stars) = session()
        presenterTestOf({ SegmentPresenter(screen, FakeNavigator(screen), segments, stars, names) }) {
            val state = awaitUntil { it.segment != null }
            // Alice's adopted "Wiggly Woods" is in the history only, not among the open proposals.
            state.eventSink(SegmentEvent.EditProposal(AtUri("at://$alice/${Nsid.FEED_SEGMENT_NAME_PROPOSAL}/3kadopted")))
            expectNoEvents()
            cancelAndIgnoreRemainingEvents()
        }
    }
}
