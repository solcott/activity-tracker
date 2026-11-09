package io.github.solcott.activitytracker.app

import co.touchlab.kermit.Logger
import com.slack.circuit.test.FakeNavigator
import io.github.solcott.activitytracker.app.detail.ActivityDetailScreen
import io.github.solcott.activitytracker.app.divesite.DiveSiteEvent
import io.github.solcott.activitytracker.app.divesite.DiveSitePresenter
import io.github.solcott.activitytracker.app.divesite.DiveSiteRegularsEvent
import io.github.solcott.activitytracker.app.divesite.DiveSiteRegularsPresenter
import io.github.solcott.activitytracker.app.divesite.DiveSiteRegularsScreen
import io.github.solcott.activitytracker.app.divesite.DiveSiteScreen
import io.github.solcott.activitytracker.app.names.PlaceNameEvent
import io.github.solcott.activitytracker.app.profile.ProfileScreen
import io.github.solcott.activitytracker.data.activity.OutboxProcessor
import io.github.solcott.activitytracker.data.activity.OutboxWriter
import io.github.solcott.activitytracker.data.di.signedInTestSession
import io.github.solcott.activitytracker.data.divesites.DiveSiteRepository
import io.github.solcott.activitytracker.data.segments.SegmentNames
import io.github.solcott.activitytracker.lexicon.Did
import io.github.solcott.activitytracker.lexicon.Nsid
import io.github.solcott.activitytracker.lexicon.app.LeaderboardScope
import io.github.solcott.activitytracker.lexicon.app.RegularsPeriod
import io.github.solcott.activitytracker.location.TrackMath
import io.github.solcott.dataresult.DataError
import io.github.solcott.uistate.LoadStatus
import io.github.solcott.uistate.errorOrNull
import io.github.solcott.uistate.hasLoaded
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlinx.coroutines.test.runTest

/** The dive site screen (dive-sites) and its Regulars board (dive-sites/site-regulars). */
class DiveSitePresenterTest {
    private val alice = Did("did:plc:alice")
    private val uri = "at://did:plc:carol/${Nsid.FEED_DIVE_SITE}/3ksite"
    private val screen = DiveSiteScreen(uri)
    private var failSite = false
    private var status = "active"
    private var naming = ""
    /** The conditions the AppView serves, as JSON; null fails the read. */
    private var conditions: String? = """{"dives":12,"medianTemperature":265,"months":[]}"""
    private var failDives = false
    private val regularsRequested = mutableListOf<Map<String, String>>()

    private fun dive(rkey: String) =
        """{"uri":"at://did:plc:bob/${Nsid.FEED_ACTIVITY}/$rkey","cid":"bafy$rkey","author":{"did":"did:plc:bob","handle":"bob.test"},""" +
            """"record":{"sport":"dive","startedAt":"2026-09-30T10:00:00Z","elapsedTime":2400,"createdAt":"2026-09-30T11:00:00Z"},""" +
            """"likeCount":0,"commentCount":0,"indexedAt":"2026-09-30T11:00:01Z","visibility":"public"}"""

    private val http =
        HttpClient(
            MockEngine { request ->
                val json = headersOf("Content-Type", "application/json")
                val params = request.url.parameters
                when (request.url.encodedPath) {
                    "/xrpc/com.atproto.server.getServiceAuth" -> respond("""{"token":"service-jwt"}""", headers = json)
                    "/xrpc/${Nsid.FEED_GET_DIVE_SITE}" ->
                        if (failSite) respond("""{"error":"NotFound","message":"Dive site not found"}""", HttpStatusCode.BadRequest, json)
                        else
                            respond(
                                """{"site":{"uri":"$uri","cid":"bafysite","creator":{"did":"did:plc:carol","handle":"carol.test"},""" +
                                    """"record":{"name":"Blue Hole","lat":274920000,"lng":343850000,"radius":120,"source":"athlete",""" +
                                    """"license":"CC0-1.0","createdAt":"2026-10-01T00:00:00Z"},"status":"$status"$naming}}""",
                                headers = json,
                            )
                    "/xrpc/${Nsid.FEED_GET_DIVE_SITE_CONDITIONS}" ->
                        conditions?.let { respond("""{"conditions":$it}""", headers = json) }
                            ?: respond("", HttpStatusCode.InternalServerError)
                    "/xrpc/${Nsid.FEED_GET_DIVE_SITE_DIVES}" ->
                        if (failDives) respond("", HttpStatusCode.InternalServerError)
                        else if (params["cursor"] == null) respond("""{"dives":[${dive("3kd2")}],"cursor":"3kd2"}""", headers = json)
                        else respond("""{"dives":[${dive("3kd1")}]}""", headers = json)
                    "/xrpc/${Nsid.FEED_GET_DIVE_SITE_REGULARS}" -> {
                        regularsRequested += params.entries().associate { it.key to it.value.single() }
                        val bob = """{"rank":1,"athlete":{"did":"did:plc:bob","handle":"bob.test"},"visits":31,"reachedOn":"2026-10-18"}"""
                        val own = """{"rank":9,"athlete":{"did":"$alice","handle":"alice.test"},"visits":3,"reachedOn":"2026-10-20"}"""
                        respond("""{"entries":[$bob],"viewer":$own}""", headers = json)
                    }
                    else -> respond("", HttpStatusCode.NotFound)
                }
            },
        ) {
            expectSuccess = false
        }

    private lateinit var names: SegmentNames

    private suspend fun sites(): DiveSiteRepository {
        val (db, _, clients) = signedInTestSession(http, did = alice)
        names = SegmentNames(clients, OutboxWriter(db, clients, Clock.System), OutboxProcessor(db, clients, Logger), Clock.System)
        return DiveSiteRepository(clients)
    }

    @Test
    fun loadsTheSiteItsOutlineConditionsTopRegularAndDives() = runTest {
        val sites = sites()
        presenterTestOf({ DiveSitePresenter(screen, FakeNavigator(screen), sites, names) }) {
            val state = awaitUntil { it.site != null && it.conditions != null && it.regulars != null && it.dives.items.isNotEmpty() }
            assertEquals("Blue Hole", state.site?.currentName)
            val outline = TrackMath.decodePolyline(assertNotNull<String>(state.outline))
            assertTrue(outline.size > 40, "a circle")
            assertEquals(outline.first(), outline.last(), "closed")
            assertEquals(12, state.conditions?.dives)
            assertEquals("bob.test", state.regulars?.leader?.athlete?.handle?.value)
            assertEquals(3, state.regulars?.viewer?.visits)
            assertEquals(
                mapOf("site" to uri, "period" to RegularsPeriod.ALL_TIME, "scope" to LeaderboardScope.EVERYONE, "limit" to "1"),
                regularsRequested.single(),
            )
            assertEquals(listOf("3kd2"), state.dives.items.map { it.uri.rkey })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun aProposedSiteHasNoRegularsAndFailedConditionsShowNone() = runTest {
        status = "proposed"
        conditions = null
        val sites = sites()
        presenterTestOf({ DiveSitePresenter(screen, FakeNavigator(screen), sites, names) }) {
            val state = awaitUntil { it.site != null && it.dives.items.isNotEmpty() }
            assertNull(state.regulars)
            assertNull(state.conditions)
            assertNull(state.content.errorOrNull, "the extras fail quietly")
            assertEquals(emptyList(), regularsRequested)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun aFailedLoadIsAnErrorThatRetries() = runTest {
        failSite = true
        val sites = sites()
        presenterTestOf({ DiveSitePresenter(screen, FakeNavigator(screen), sites, names) }) {
            val failed = awaitUntil { it.content.errorOrNull != null }
            assertEquals(DataError.Api(listOf("Dive site not found"), code = "NotFound"), failed.content.errorOrNull)
            failSite = false
            failed.eventSink(DiveSiteEvent.Retry)
            assertTrue(awaitUntil { it.site != null }.content.hasLoaded)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun divesPageAndOpen() = runTest {
        val sites = sites()
        val navigator = FakeNavigator(screen)
        presenterTestOf({ DiveSitePresenter(screen, navigator, sites, names) }) {
            val first = awaitUntil { it.dives.items.isNotEmpty() }
            first.eventSink(DiveSiteEvent.LoadMoreDives)
            val both = awaitUntil { it.dives.items.size == 2 }
            assertEquals(listOf("3kd2", "3kd1"), both.dives.items.map { it.uri.rkey })
            both.eventSink(DiveSiteEvent.OpenDive(both.dives.items.last().uri))
            assertEquals(ActivityDetailScreen("at://did:plc:bob/${Nsid.FEED_ACTIVITY}/3kd1"), navigator.awaitNextScreen())
            both.eventSink(DiveSiteEvent.OpenRegulars)
            assertEquals(DiveSiteRegularsScreen(uri, "Blue Hole"), navigator.awaitNextScreen())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun aFailedDivesPageLeavesTheSite() = runTest {
        failDives = true
        val sites = sites()
        presenterTestOf({ DiveSitePresenter(screen, FakeNavigator(screen), sites, names) }) {
            val state = awaitUntil { it.site != null && it.dives.refresh is LoadStatus.Failed }
            assertNull(state.content.errorOrNull)
            failDives = false
            state.eventSink(DiveSiteEvent.RetryDives)
            awaitUntil { it.dives.items.isNotEmpty() }
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun aProposalIsOfferedAndOpensTheSharedSheet() = runTest {
        naming = ""","viewer":{"canProposeName":true,"canVoteOnNames":true}"""
        val sites = sites()
        presenterTestOf({ DiveSitePresenter(screen, FakeNavigator(screen), sites, names) }) {
            val loaded = awaitUntil { it.site != null }
            assertTrue(loaded.canPropose)
            assertTrue(loaded.canVote)
            loaded.names.eventSink(PlaceNameEvent.ProposeName)
            val sheet = awaitUntil { it.names.sheet != null }.names.sheet
            assertEquals(uri, sheet?.place)
            assertEquals("Blue Hole", sheet?.currentName)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun regularsOpenOnAllTimeEveryoneAndFollowChoices() = runTest {
        val sites = sites()
        val regulars = DiveSiteRegularsScreen(uri, "Blue Hole")
        val navigator = FakeNavigator(regulars)
        presenterTestOf({ DiveSiteRegularsPresenter(regulars, navigator, sites) }) {
            val first = awaitUntil { it.entries.items.isNotEmpty() }
            assertEquals(RegularsPeriod.ALL_TIME, first.period)
            assertEquals(LeaderboardScope.EVERYONE, first.scope)
            assertEquals(3, first.viewerEntry?.visits, "the viewer's own place, off the list")

            first.eventSink(DiveSiteRegularsEvent.PeriodChanged(RegularsPeriod.THIS_YEAR))
            awaitUntil { it.period == RegularsPeriod.THIS_YEAR && it.entries.items.isNotEmpty() }
            first.eventSink(DiveSiteRegularsEvent.ScopeChanged(LeaderboardScope.FOLLOWING))
            awaitUntil { it.scope == LeaderboardScope.FOLLOWING && it.entries.items.isNotEmpty() }
            assertEquals(
                listOf(
                    RegularsPeriod.ALL_TIME to LeaderboardScope.EVERYONE,
                    RegularsPeriod.THIS_YEAR to LeaderboardScope.EVERYONE,
                    RegularsPeriod.THIS_YEAR to LeaderboardScope.FOLLOWING,
                ),
                regularsRequested.map { it["period"] to it["scope"] },
            )

            // An unknown scope or period is ignored, as on the segment boards: the choice stays and nothing more is asked for.
            first.eventSink(DiveSiteRegularsEvent.ScopeChanged("nearby"))
            first.eventSink(DiveSiteRegularsEvent.PeriodChanged("forever"))
            testScheduler.runCurrent()
            expectNoEvents()
            assertEquals(3, regularsRequested.size)

            first.eventSink(DiveSiteRegularsEvent.OpenAthlete(Did("did:plc:bob")))
            assertEquals(ProfileScreen("did:plc:bob"), navigator.awaitNextScreen())
            cancelAndIgnoreRemainingEvents()
        }
    }
}
