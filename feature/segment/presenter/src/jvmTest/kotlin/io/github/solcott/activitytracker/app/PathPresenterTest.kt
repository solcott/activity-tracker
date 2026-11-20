package io.github.solcott.activitytracker.app

import com.slack.circuit.test.CircuitReceiveTurbine
import com.slack.circuit.test.FakeNavigator
import io.github.solcott.activitytracker.app.segment.PathEvent
import io.github.solcott.activitytracker.app.segment.PathListItem
import io.github.solcott.activitytracker.app.segment.PathPresenter
import io.github.solcott.activitytracker.app.segment.PathScreen
import io.github.solcott.activitytracker.app.segment.PathState
import io.github.solcott.activitytracker.app.segment.SegmentScreen
import io.github.solcott.activitytracker.app.segment.contentOf
import io.github.solcott.activitytracker.app.text.AppText
import io.github.solcott.activitytracker.data.di.signedInTestSession
import io.github.solcott.activitytracker.data.segments.SegmentRepository
import io.github.solcott.activitytracker.lexicon.AtJson
import io.github.solcott.activitytracker.lexicon.AtUri
import io.github.solcott.activitytracker.lexicon.Cid
import io.github.solcott.activitytracker.lexicon.Did
import io.github.solcott.activitytracker.lexicon.Handle
import io.github.solcott.activitytracker.lexicon.Nsid
import io.github.solcott.activitytracker.lexicon.app.GetPath
import io.github.solcott.activitytracker.lexicon.app.PathKind
import io.github.solcott.activitytracker.lexicon.app.PathView
import io.github.solcott.activitytracker.lexicon.app.ProfileViewBasic
import io.github.solcott.activitytracker.lexicon.app.SegmentRecord
import io.github.solcott.activitytracker.lexicon.app.SegmentView
import io.github.solcott.activitytracker.lexicon.app.TrailAttributes
import io.github.solcott.activitytracker.location.GeoPoint
import io.github.solcott.activitytracker.location.SegmentGeometry
import io.github.solcott.uistate.ContentState
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlinx.coroutines.test.runTest

/** The Trail page (segments/path-page): its segment groups longest first, the sport filter, and a selection shared with the map. */
class PathPresenterTest {
    private val carol = Did("did:plc:carol")
    private val start = GeoPoint(44.4759, -73.2121)

    private fun line(meters: Double, back: Boolean = false): String {
        val points = listOf(start, GeoPoint(start.lat + meters / 111_320.0, start.lng))
        return SegmentGeometry.encodePolyline(if (back) points.reversed() else points)
    }

    @Suppress("LongParameterList") // A segment's every field a test sets.
    private fun segment(rkey: String, name: String, sport: String, meters: Double, back: Boolean = false, group: String? = null) =
        SegmentView(
            AtUri.of(carol, Nsid.FEED_SEGMENT, rkey),
            Cid("bafy$rkey"),
            ProfileViewBasic(carol, Handle("carol.test")),
            SegmentRecord(name, sport, line(meters, back), "osm", "ODbL-1.0", Instant.parse("2026-10-01T00:00:00Z")),
            distanceMeters = meters.toLong(),
            group = group?.let { AtUri.of(carol, Nsid.FEED_SEGMENT, it) },
        )

    private val wholeRide = segment("3kwr", "Ann's (northbound)", "ride", 5_000.0, group = "3kwr")
    private val wholeRun = segment("3kwn", "Ann's (southbound)", "run", 5_000.0, back = true, group = "3kwr")
    private val stretch = segment("3ks", "Ann's: Lot → Ridge", "run", 1_200.0)

    private val anns =
        PathView(
            AtUri.of(Did("did:plc:importer"), Nsid.FEED_PATH, "anns"),
            Cid("bafyanns"),
            PathKind.TRAIL,
            "Ann's",
            listOf(line(5_000.0)),
            5_000,
            "osm",
            "ODbL-1.0",
            sports = listOf("ride", "run"),
            groupCount = 2,
        )

    // Rush: two branches, each with its whole path, a span and its section trail; a span named after the family; a lone section row.
    private val throughTheTrees = segment("ttt", "Rush › Through the Trees Trail", "ride", 4_000.0)
    private val tttSpan = segment("tttspan", "Rush › Through the Trees Trail → Canyon Hollow", "ride", 3_000.0)
    private val tttSection = segment("tttsec", "Rush - Through the Trees Trail", "ride", 1_000.0)
    private val exitStageLeft = segment("esl", "Rush › Exit Stage Left", "ride", 3_500.0)
    private val eslSection = segment("eslsec", "Rush - Exit Stage Left", "run", 900.0)
    private val familySpan = segment("famspan", "Rush → Bonneville Shoreline", "ride", 2_000.0)
    private val limelight = segment("lime", "Rush - Limelight", "ride", 800.0)
    private val rushSections =
        listOf(
            GetPath.SectionView("Through the Trees Trail", listOf(tttSection.uri, throughTheTrees.uri, tttSpan.uri)),
            GetPath.SectionView("Exit Stage Left", listOf(exitStageLeft.uri, eslSection.uri)),
            GetPath.SectionView("Limelight", listOf(limelight.uri)),
        )
    private val rush =
        GetPath.Output(anns, listOf(throughTheTrees, tttSpan, tttSection, exitStageLeft, eslSection, familySpan, limelight), rushSections)

    /** What the AppView answers. */
    private var output = GetPath.Output(anns, listOf(stretch, wholeRide, wholeRun))

    /** How many times the page asked for the path. */
    private val pathLoads = AtomicInteger()

    private val http =
        HttpClient(
            MockEngine { request ->
                val json = headersOf("Content-Type", "application/json")
                when (request.url.encodedPath.removePrefix("/xrpc/")) {
                    "com.atproto.server.getServiceAuth" -> respond("""{"token":"service-jwt"}""", headers = json)
                    Nsid.FEED_GET_PATH -> {
                        pathLoads.incrementAndGet()
                        respond(
                            AtJson.encodeToString(GetPath.Output.serializer(), output),
                            headers = json,
                        )
                    }
                    else -> respond("", HttpStatusCode.NotFound)
                }
            },
        ) {
            expectSuccess = false
        }

    private suspend fun test(screen: PathScreen, navigator: FakeNavigator, block: suspend CircuitReceiveTurbine<PathState>.() -> Unit) {
        val (_, _, clients) = signedInTestSession(http, did = Did("did:plc:alice"))
        presenterTestOf({ PathPresenter(screen, navigator, SegmentRepository(clients)) }, block = block)
    }

    @Test
    fun listsTheTrailsGroupsLongestFirstAndFramesIt() = runTest {
        val screen = PathScreen(anns.uri.value)
        test(screen, FakeNavigator(screen)) {
            val loaded = awaitUntil { it.path != null && it.camera != null }
            assertEquals(listOf(wholeRide.uri, stretch.uri), loaded.groups.map { it.shown.uri }, "the whole line, then the stretch")
            assertTrue(loaded.groups.first().bothWays)
            assertEquals("OpenStreetMap", loaded.credit?.let { it::class.simpleName })
            assertEquals(3, loaded.mapLines.size, "the trail's line and each group's")
            assertTrue(loaded.mapLines.first().lift, "the trail's line drawn muted under them")
            cancelAndIgnoreRemainingEvents()
        }
    }

    /** segments/trail-attributes: the Trail page shows every scale its trail holds, whatever sport it was opened with. */
    @Test
    fun theTrailPageShowsEveryScale() = runTest {
        output = GetPath.Output(anns.copy(trail = TrailAttributes(sacScale = "hiking", mtbScale = 1, lit = "yes")), listOf(stretch))
        val screen = PathScreen(anns.uri.value, sport = "ride")
        test(screen, FakeNavigator(screen)) {
            val rows = awaitUntil { it.path != null }.trailDetails()
            assertEquals(
                listOf("trail_detail_hiking", "trail_detail_mtb", "trail_detail_lit"),
                rows.map { (it.label as AppText.Named).name },
            )
            cancelAndIgnoreRemainingEvents()
        }
    }

    /** segments/trail-attributes: a path served with no `trail` has no Trail details. */
    @Test
    fun aPathWithoutTrailAttributesHasNoTrailDetails() = runTest {
        val screen = PathScreen(anns.uri.value)
        test(screen, FakeNavigator(screen)) {
            assertTrue(awaitUntil { it.path != null }.trailDetails().isEmpty())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun aTapSelectsThenOpensAndAClickOpens() = runTest {
        val screen = PathScreen(anns.uri.value)
        val navigator = FakeNavigator(screen)
        test(screen, navigator) {
            val loaded = awaitUntil { it.path != null }
            val stretchRow = loaded.groups.last()
            loaded.eventSink(loaded.tapRowEvent(stretchRow))
            val selected = awaitUntil { it.selected == stretch.uri }
            assertTrue(selected.isSelected(stretchRow))
            selected.eventSink(selected.tapRowEvent(stretchRow))
            assertEquals(SegmentScreen(stretch.uri.value), navigator.awaitNextScreen())
            selected.eventSink(selected.openEvent(selected.groups.first()))
            assertEquals(SegmentScreen(wholeRide.uri.value), navigator.awaitNextScreen())
            selected.eventSink(selected.selectEvent(null))
            awaitUntil { it.selected == null }
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun opensNarrowedToTheBrowseSportUntilShowAll() = runTest {
        val screen = PathScreen(anns.uri.value, sport = "ride")
        test(screen, FakeNavigator(screen)) {
            val ride = awaitUntil { it.path != null }
            assertTrue(ride.offersAllSports)
            assertEquals(listOf(wholeRide.uri), ride.groups.map { it.shown.uri }, "only the ride group")
            ride.eventSink(PathEvent.ShowAllSports)
            val all = awaitUntil { it.sport == null && it.groups.size == 2 }
            assertEquals(listOf(wholeRide.uri, stretch.uri), all.groups.map { it.shown.uri })
            assertEquals(1, pathLoads.get(), "showing every sport regroups what loaded")
            cancelAndIgnoreRemainingEvents()
        }
    }

    private fun names(items: List<PathListItem>): List<String> = items.map { item ->
        when (item) {
            is PathListItem.Group -> item.group.shown.currentName
            is PathListItem.Section -> "[${item.name}: ${item.rows.joinToString { it.shown.currentName }}]"
        }
    }

    @Test
    fun aSectionsRowsFoldUnderItLongestFirstAndALoneOneStaysARow() {
        val content = contentOf(rush, sport = null)
        assertEquals(
            listOf(
                "[Through the Trees Trail: Rush › Through the Trees Trail, Rush › Through the Trees Trail → Canyon Hollow, " +
                    "Rush - Through the Trees Trail]",
                "[Exit Stage Left: Rush › Exit Stage Left, Rush - Exit Stage Left]",
                "Rush → Bonneville Shoreline",
                "Rush - Limelight",
            ),
            names(content.items),
            "by each section's longest row; a section of one row is that row",
        )
        val ttt = content.items.first() as PathListItem.Section
        assertEquals(throughTheTrees.uri, ttt.summary.shown.uri, "it reads as its longest row")
        assertEquals(listOf("ride", "run"), (content.items[1] as PathListItem.Section).summary.sports)
        assertEquals(7, content.groups.size, "the map still holds every row")
        assertEquals(
            names(contentOf(output, null).items),
            contentOf(output, null).groups.map { it.shown.currentName },
            "no sections, plain rows",
        )
    }

    @Test
    fun theSportFilterLeavesASectionOfOneAsARow() {
        assertEquals(
            listOf(
                "[Through the Trees Trail: Rush › Through the Trees Trail, Rush › Through the Trees Trail → Canyon Hollow, " +
                    "Rush - Through the Trees Trail]",
                "Rush › Exit Stage Left",
                "Rush → Bonneville Shoreline",
                "Rush - Limelight",
            ),
            names(contentOf(rush, sport = "ride").items),
        )
    }

    @Test
    fun sectionsOpenExpandedCollapseAndASelectedLineExpandsItsSection() = runTest {
        output = rush
        val screen = PathScreen(anns.uri.value)
        test(screen, FakeNavigator(screen)) {
            val loaded = awaitUntil { it.path != null }
            assertTrue(loaded.sections.all(loaded::isExpanded), "expanded when loaded")
            assertTrue(loaded.offersAllSections && loaded.allSectionsExpanded)
            loaded.eventSink(loaded.allSectionsEvent)
            val folded = awaitUntil { it.sections.none(it::isExpanded) }
            assertTrue(!folded.allSectionsExpanded, "the control now expands them")
            folded.eventSink(folded.toggleEvent(folded.sections.first()))
            val one = awaitUntil { it.isExpanded(it.sections.first()) }
            assertTrue(!one.isExpanded(one.sections.last()) && !one.allSectionsExpanded)
            one.eventSink(one.selectEvent(eslSection.uri.value))
            val revealed = awaitUntil { it.selected == eslSection.uri }
            assertTrue(revealed.isExpanded(revealed.sections.last()), "selecting a line in a collapsed section expands it")
            assertTrue(revealed.allSectionsExpanded)
            revealed.eventSink(PathEvent.SetAllSections(expanded = false))
            awaitUntil { it.sections.none(it::isExpanded) }
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun oneSectionOffersNoAllSectionsControl() {
        val single = rush.copy(sections = rushSections.take(1))
        val state = PathState(ContentState(contentOf(single, null)))
        assertEquals(1, state.sections.size)
        assertTrue(!state.offersAllSections)
    }
}
