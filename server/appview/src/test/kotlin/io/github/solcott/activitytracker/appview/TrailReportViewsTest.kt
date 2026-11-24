package io.github.solcott.activitytracker.appview

import io.github.solcott.activitytracker.appview.api.AvatarUrls
import io.github.solcott.activitytracker.appview.api.LabelValues
import io.github.solcott.activitytracker.appview.api.MediaUrls
import io.github.solcott.activitytracker.appview.api.SegmentViews
import io.github.solcott.activitytracker.appview.db.PathSegments
import io.github.solcott.activitytracker.appview.db.Paths
import io.github.solcott.activitytracker.appview.db.tx
import io.github.solcott.activitytracker.lexicon.AtJson
import io.github.solcott.activitytracker.lexicon.AtUri
import io.github.solcott.activitytracker.lexicon.Did
import io.github.solcott.activitytracker.lexicon.Nsid
import io.github.solcott.activitytracker.lexicon.app.AthleteSpace
import io.github.solcott.activitytracker.lexicon.app.GetPathConditions
import io.github.solcott.activitytracker.lexicon.app.GetSegment
import io.github.solcott.activitytracker.lexicon.app.GetTrailPhotos
import io.github.solcott.activitytracker.lexicon.app.GetTrailReports
import io.github.solcott.activitytracker.lexicon.app.TrailConditionTag
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.jsonObject
import org.jetbrains.exposed.v1.jdbc.insert

/**
 * Trail reports' views (segments/trail-reports): the segment's conditions, the reports list, photos, a trail's roll-up, blocks and labels.
 */
class TrailReportViewsTest : TrailReportTestBase() {
    private val views =
        SegmentViews(
            db,
            AvatarUrls("https://appview.test"),
            clock,
            segmentImporter = Did(importer),
            media = MediaUrls("https://appview.test"),
        )

    @BeforeTest fun setUp() = activeSegment()

    private fun segmentView(viewer: String? = null) = runBlocking {
        views.segment(viewer?.let(::Did), GetSegment.Params(AtUri(seg))).segment
    }

    private fun reports(viewer: String? = null, cursor: String? = null, limit: Int? = null) = runBlocking {
        views.trailReports(viewer?.let(::Did), GetTrailReports.Params(AtUri(seg), limit, cursor))
    }

    private fun interactions(did: String, collection: String, rkey: String, json: String) = runBlocking {
        idx.spaceWrite(space(AthleteSpace.INTERACTIONS, did), collection, rkey, "bafy$rkey", AtJson.parseToJsonElement(json).jsonObject)
    }

    private fun blocks(author: String, subject: String) =
        interactions(author, Nsid.GRAPH_BLOCK, subject, """{"subject":"$subject","createdAt":"2026-11-20T00:00:00Z"}""")

    private fun mutes(author: String, subject: String) =
        interactions(author, Nsid.GRAPH_MUTE, subject, """{"subject":"$subject","createdAt":"2026-11-20T00:00:00Z"}""")

    /** Three riders on Muddy, one also Downed tree, the newest with a note and a photo. */
    private fun muddyWeek() {
        listOf(alice, bob, carol).forEach { effort(it) }
        report("3kc", did = carol, tags = listOf("muddy"))
        later(1.days)
        report("3kb", did = bob, tags = listOf("muddy", "wet"))
        later(1.days)
        report("3ka", did = alice, tags = listOf("muddy", "downedTree"), note = "Tree past the bridge", photos = listOf(photoCid(1)))
    }

    @Test
    fun segmentViewCarriesTheSummaryAndPhotos() {
        muddyWeek()
        val conditions = assertNotNull(segmentView().conditions)
        assertEquals("muddy", conditions.lead)
        assertEquals(3, conditions.leadRiders)
        assertEquals(
            listOf(TrailConditionTag("wet", 1), TrailConditionTag("downedTree", 1)),
            conditions.tags,
            "ties in the lexicon's tag order",
        )
        assertEquals("Tree past the bridge", conditions.latestNote?.note)
        assertEquals(1, conditions.photoCount)
        assertEquals("https://appview.test/img/media/$alice/${photoCid(1)}", conditions.photos.single().image.fullsize)
        assertEquals(1, conditions.photos.single().position)
    }

    @Test
    fun recentRiderMayReport() {
        effort(alice, 3.days)
        val viewer = assertNotNull(segmentView(alice).viewer)
        assertTrue(viewer.canReportConditions)
        assertEquals(clock.now - 3.days, viewer.lastEffortAt)
        assertFalse(assertNotNull(segmentView(bob).viewer).canReportConditions)
    }

    @Test
    fun signedOutSeesConditionsButMayNotReport() {
        muddyWeek()
        val view = segmentView()
        assertNotNull(view.conditions)
        assertNull(view.viewer)
    }

    @Test
    fun noReportsMeansNoConditions() {
        assertNull(segmentView().conditions)
    }

    @Test
    fun reportsListNewestFirstAndPage() {
        muddyWeek()
        val first = reports(limit = 2)
        assertEquals(listOf(reportUri("3ka"), reportUri("3kb", bob)), first.reports.map { it.uri.value })
        assertTrue(first.reports.all { it.current })
        val rest = reports(cursor = first.cursor, limit = 2)
        assertEquals(listOf(reportUri("3kc", carol)), rest.reports.map { it.uri.value })
        assertNull(rest.cursor)
    }

    @Test
    fun olderReportsAreNotCurrent() {
        muddyWeek()
        later(15.days)
        assertTrue(reports().reports.none { it.current })
        val conditions = assertNotNull(segmentView().conditions)
        assertNull(conditions.lead)
        assertEquals(listOf("muddy", "downedTree"), conditions.lastReportTags)
    }

    @Test
    fun blockedRiderIsGoneBothWays() {
        muddyWeek()
        blocks(bob, alice)
        assertFalse(reports(bob).reports.any { it.author.did.value == alice })
        assertEquals(0, assertNotNull(segmentView(bob).conditions).photoCount)
        assertEquals(2, assertNotNull(segmentView(bob).conditions).riders)
        assertFalse(reports(alice).reports.any { it.author.did.value == bob })
    }

    @Test
    fun mutedRiderIsUnlistedButCounted() {
        muddyWeek()
        mutes(carol, alice)
        assertFalse(reports(carol).reports.any { it.author.did.value == alice })
        val conditions = assertNotNull(segmentView(carol).conditions)
        assertEquals(3, conditions.leadRiders)
        assertNull(conditions.latestNote)
    }

    @Test
    fun hiddenReportStopsCountingButItsAuthorSeesIt() {
        muddyWeek()
        label(reportUri("3ka"), LabelValues.HIDE)
        assertEquals(2, assertNotNull(segmentView(bob).conditions).riders)
        assertFalse(reports(bob).reports.any { it.uri.value == reportUri("3ka") })
        assertTrue(reports(alice).reports.any { it.uri.value == reportUri("3ka") })
        label(reportUri("3ka"), LabelValues.HIDE, neg = true, cts = "2026-11-30T10:00:00Z")
        assertEquals(3, assertNotNull(segmentView(bob).conditions).riders)
    }

    @Test
    fun deletedReportLeavesTheSummaryAndPhotos() {
        muddyWeek()
        commitAs(alice, Nsid.FEED_TRAIL_REPORT, "3ka", null)
        val conditions = assertNotNull(segmentView().conditions)
        assertEquals(2, conditions.riders)
        assertEquals(0, conditions.photoCount)
    }

    @Test
    fun photosOfASegmentPage() = runBlocking {
        effort(alice)
        report("3k1", photos = listOf(photoCid(1), photoCid(2)))
        later(1.hours)
        report("3k2", tags = emptyList(), photos = listOf(photoCid(3)))
        val first = views.trailPhotos(null, GetTrailPhotos.Params(segment = AtUri(seg), limit = 2))
        assertEquals(listOf(photoCid(3), photoCid(1)), first.photos.map { it.image.fullsize.substringAfterLast('/') })
        val rest = views.trailPhotos(null, GetTrailPhotos.Params(segment = AtUri(seg), limit = 2, cursor = first.cursor))
        assertEquals(listOf(photoCid(2)), rest.photos.map { it.image.fullsize.substringAfterLast('/') })
        assertEquals(2, rest.photos.single().position)
    }

    @Test
    fun pathRollUpListsClosedFirst() = runBlocking {
        activeSegment("3kt")
        activeSegment("3ku")
        val ridge = segmentUri("3kt", importer)
        val lower = segmentUri("3ku", importer)
        val path = "at://$importer/${Nsid.FEED_PATH}/kingdom"
        db.tx {
            Paths.insert {
                it[uri] = path
                it[cid] = "bafypath"
                it[authorDid] = importer
                it[kind] = "trail"
                it[name] = "Kingdom Loop"
                it[lines] = "[]"
                it[lengthM] = 3000
                it[indexedAt] = 0
                it[minLatE7] = 0
                it[maxLatE7] = 0
                it[minLngE7] = 0
                it[maxLngE7] = 0
                it[dataSource] = "osm"
                it[license] = "ODbL-1.0"
            }
            listOf(seg, ridge, lower).forEach { s ->
                PathSegments.insert {
                    it[pathUri] = path
                    it[segmentUri] = s
                }
            }
        }
        listOf(alice, bob, carol).forEach { effort(it) }
        effort(bob, segment = ridge)
        effort(carol, segment = lower)
        report("3ka", tags = listOf("muddy"), photos = listOf(photoCid(1)))
        later(1.hours)
        report("3kc", did = carol, segment = lower, tags = listOf("dry"))
        later(1.hours)
        report("3kb", did = bob, segment = ridge, tags = listOf("closed"))
        val rollUp = views.pathConditions(null, GetPathConditions.Params(AtUri(path)))
        assertEquals(listOf(ridge, lower, seg), rollUp.segments.map { it.segment.value })
        assertEquals("closed", rollUp.segments.first().conditions.lead)
        assertEquals(3, rollUp.segmentCount)
        assertEquals(1, rollUp.photoCount)
        assertEquals(photoCid(1), rollUp.photos.single().image.fullsize.substringAfterLast('/'))
        val photos = views.trailPhotos(null, GetTrailPhotos.Params(path = AtUri(path)))
        assertEquals(1, photos.photos.size)
    }
}
