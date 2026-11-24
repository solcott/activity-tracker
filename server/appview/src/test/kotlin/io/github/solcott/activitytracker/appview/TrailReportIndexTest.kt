package io.github.solcott.activitytracker.appview

import io.github.solcott.activitytracker.appview.db.SegmentEfforts
import io.github.solcott.activitytracker.appview.db.TrailReportBlobs
import io.github.solcott.activitytracker.appview.db.TrailReportTags
import io.github.solcott.activitytracker.appview.db.TrailReports
import io.github.solcott.activitytracker.appview.db.tx
import io.github.solcott.activitytracker.appview.index.JetstreamEvent
import io.github.solcott.activitytracker.lexicon.AtJson
import io.github.solcott.activitytracker.lexicon.Nsid
import io.github.solcott.activitytracker.lexicon.app.AthleteSpace
import io.github.solcott.activitytracker.lexicon.app.SegmentKind
import io.github.solcott.activitytracker.lexicon.app.Visibility
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.jsonObject
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.selectAll

/** Indexing of `feed.trailReport` (segments/trail-reports): who may report, what's kept, rewrites, deletes and the daily limit. */
class TrailReportIndexTest : TrailReportTestBase() {
    @BeforeTest fun segment() = activeSegment()

    private fun stored(rkey: String, did: String = alice) = runBlocking {
        db.tx { TrailReports.selectAll().where { TrailReports.uri eq reportUri(rkey, did) }.firstOrNull() }
    }

    private fun tagsOf(rkey: String, did: String = alice) = runBlocking {
        db.tx {
            TrailReportTags.selectAll().where { TrailReportTags.reportUri eq reportUri(rkey, did) }.map { it[TrailReportTags.tag] }.toSet()
        }
    }

    private fun photosOf(rkey: String, did: String = alice) = runBlocking {
        db.tx {
            TrailReportBlobs.selectAll()
                .where { TrailReportBlobs.reportUri eq reportUri(rkey, did) }
                .orderBy(TrailReportBlobs.sortIndex)
                .map { it[TrailReportBlobs.cid] }
        }
    }

    @Test
    fun reportFromARecentRiderIsKept() {
        effort(alice, 6.days, visibility = Visibility.SELF)
        report("3kr", tags = listOf("muddy", "downedTree"), note = "Tree past the bridge", photos = listOf(photoCid(1)))
        val row = stored("3kr")!!
        assertEquals("Tree past the bridge", row[TrailReports.note])
        assertEquals(setOf("muddy", "downedTree"), tagsOf("3kr"))
        assertEquals(listOf(photoCid(1)), photosOf("3kr"))
    }

    @Test
    fun photosOnlyIsAReport() {
        effort(alice)
        report("3kr", tags = emptyList(), photos = listOf(photoCid(1), photoCid(2)))
        assertEquals(false, stored("3kr")!![TrailReports.hasConditions])
        assertEquals(listOf(photoCid(1), photoCid(2)), photosOf("3kr"))
    }

    @Test
    fun nothingToReportIsIgnored() {
        effort(alice)
        report("3kr", tags = listOf("sunny"))
        assertNull(stored("3kr"))
    }

    @Test
    fun unknownTagBesideAKnownOneKeepsTheKnownOne() {
        effort(alice)
        report("3kr", tags = listOf("muddy", "foggy"))
        assertEquals(setOf("muddy"), tagsOf("3kr"))
    }

    @Test
    fun reportInASpaceIsIgnored() = runBlocking {
        effort(alice)
        idx.spaceWrite(
            space(AthleteSpace.INTERACTIONS, alice),
            Nsid.FEED_TRAIL_REPORT,
            "3kr",
            "bafyreport",
            AtJson.parseToJsonElement(reportJson(listOf("muddy"), null, emptyList())).jsonObject,
        )
        assertNull(stored("3kr"))
    }

    @Test
    fun lastRideTwoMonthsAgoIsIgnored() {
        effort(bob, 60.days)
        report("3kr", did = bob)
        assertNull(stored("3kr", bob))
    }

    @Test
    fun liftIsIgnored() {
        activeSegment("3kl", kind = SegmentKind.LIFT)
        val lift = segmentUri("3kl", importer)
        effort(alice, segment = lift)
        report("3kr", segment = lift)
        assertNull(stored("3kr"))
    }

    @Test
    fun unknownSegmentIsIgnored() {
        effort(alice)
        report("3kr", segment = segmentUri("3kx", importer))
        assertNull(stored("3kr"))
    }

    @Test
    fun reportOutlivesTheEffortThatAllowedIt() = runBlocking {
        effort(alice)
        report("3kr")
        db.tx { SegmentEfforts.deleteWhere { authorDid eq alice } }
        assertTrue(stored("3kr") != null)
    }

    @Test
    fun sixthReportInADayIsIgnored() {
        effort(alice)
        (1..6).forEach { report("3kr$it") }
        assertTrue(stored("3kr5") != null)
        assertNull(stored("3kr6"))
        later(1.days)
        report("3kr7")
        assertTrue(stored("3kr7") != null)
    }

    @Test
    fun backdatedRecordCountsFromIndexing() {
        effort(alice)
        report("3kr")
        assertEquals(clock.now.toEpochMilliseconds(), stored("3kr")!![TrailReports.indexedAt])
    }

    @Test
    fun rewriteKeepsItsDateAndReplacesTagsAndPhotos() {
        effort(alice)
        report("3kr", tags = listOf("muddy"), photos = listOf(photoCid(1), photoCid(2)))
        val first = stored("3kr")!![TrailReports.indexedAt]
        later(3.hours)
        report("3kr", tags = listOf("dry"), note = "Fixed spelling", photos = listOf(photoCid(2)))
        assertEquals(first, stored("3kr")!![TrailReports.indexedAt])
        assertEquals(setOf("dry"), tagsOf("3kr"))
        assertEquals(listOf(photoCid(2)), photosOf("3kr"))
    }

    @Test
    fun rewriteNamingAnotherSegmentIsIgnored() {
        activeSegment("3kt")
        effort(alice)
        report("3kr", tags = listOf("muddy"))
        report("3kr", tags = listOf("closed"), segment = segmentUri("3kt", importer))
        assertEquals(seg, stored("3kr")!![TrailReports.segmentUri])
        assertEquals(setOf("muddy"), tagsOf("3kr"))
    }

    @Test
    fun rewriteAfterTheRideWindowIsStillAccepted() {
        effort(alice, 29.days)
        report("3kr", tags = listOf("muddy"))
        later(10.days)
        report("3kr", tags = listOf("muddy"), note = "Still a tree")
        assertEquals("Still a tree", stored("3kr")!![TrailReports.note])
    }

    @Test
    fun rewriteToNothingDeletesIt() {
        effort(alice)
        report("3kr", tags = emptyList(), photos = listOf(photoCid(1)))
        report("3kr", tags = emptyList(), photos = emptyList())
        assertNull(stored("3kr"))
        assertEquals(emptyList(), photosOf("3kr"))
    }

    @Test
    fun deleteDropsTagsAndPhotos() {
        effort(alice)
        report("3kr", photos = listOf(photoCid(1)))
        commitAs(alice, Nsid.FEED_TRAIL_REPORT, "3kr", null)
        assertNull(stored("3kr"))
        assertEquals(emptySet(), tagsOf("3kr"))
        assertEquals(emptyList(), photosOf("3kr"))
    }

    @Test
    fun deletedAccountTakesItsReports() = runBlocking {
        effort(alice)
        report("3kr", photos = listOf(photoCid(1)))
        idx.handle(
            JetstreamEvent.Account(alice, "2026-11-20T12:00:00.000000Z", account = JetstreamEvent.Account.Body(alice, false, "deleted")),
        )
        assertNull(stored("3kr"))
        assertEquals(emptyList(), photosOf("3kr"))
    }
}
