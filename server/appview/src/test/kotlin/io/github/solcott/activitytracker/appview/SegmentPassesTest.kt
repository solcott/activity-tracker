package io.github.solcott.activitytracker.appview

import io.github.solcott.activitytracker.appview.db.Segments
import io.github.solcott.activitytracker.appview.segments.ProposalState
import io.github.solcott.activitytracker.appview.segments.SegmentJobs
import io.github.solcott.activitytracker.appview.segments.StatusChange
import io.github.solcott.activitytracker.lexicon.Did
import io.github.solcott.activitytracker.lexicon.app.SegmentStatus
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.v1.jdbc.Database

/** The one-segment status and naming passes (admin/segment-tools), which run the hourly passes' code narrowed to the segments named. */
class SegmentPassesTest {
    private val h2 = TestH2()
    private val db: Database = h2.db

    @AfterTest fun dropDatabase() = h2.close()

    private val clock = MutableClock(Instant.parse("2026-10-28T12:00:00Z"))
    private val f = SegmentAdminFixtures(db, clock)
    private val operator = Did("did:plc:operator")

    private fun riders(segment: String, count: Int) = repeat(count) { f.efforts("did:plc:rider$it", segment) }

    @Test
    fun promotesOnlyTheSegmentNamed() {
        val a = f.segment("3ka")
        val b = f.segment("3kb", east = 5000.0)
        riders(a, 3)
        riders(b, 3)
        runBlocking { f.passes.updateStatuses(listOf(a)) }
        assertEquals(SegmentStatus.ACTIVE, f.status(a))
        assertEquals(SegmentStatus.PROPOSED, f.status(b), "equally ridden, but not named")
    }

    @Test
    fun archivesTheSegmentNamedAndRechecksItsDuplicates() {
        val a = f.segment("3ka")
        val copy = f.segment("3kc", did = "did:plc:bob")
        val b = f.segment("3kb", east = 5000.0)
        assertEquals(SegmentStatus.DUPLICATE, f.status(copy))
        clock.now += 200.days
        assertEquals(StatusChange(SegmentStatus.PROPOSED, SegmentStatus.ARCHIVED), runBlocking { f.passes.updateStatusOf(a) })
        assertEquals(SegmentStatus.PROPOSED, f.status(copy), "no longer the duplicate of an archived segment")
        assertNull(f.row(copy)[Segments.duplicateOf])
        assertEquals(SegmentStatus.PROPOSED, f.status(b), "stale too, but not named")
    }

    @Test
    fun leavesAPinnedStatusAlone() {
        val a = f.segment("3ka")
        runBlocking { f.indexer.segmentPins.pinStatus(a, SegmentStatus.ARCHIVED, null, operator) }
        riders(a, 3)
        assertEquals(StatusChange(SegmentStatus.ARCHIVED, SegmentStatus.ARCHIVED), runBlocking { f.passes.updateStatusOf(a) })
    }

    @Test
    fun aSegmentNotIndexedHasNoChange() {
        assertNull(runBlocking { f.passes.updateStatusOf(f.segmentUri("3kx")) })
    }

    @Test
    fun namingAdoptsOnlyOnTheSegmentNamed() {
        val a = f.segment("3ka")
        val b = f.segment("3kb", east = 5000.0)
        val opened = clock.now.toEpochMilliseconds()
        val onA = f.proposal("3kpa", a, "did:plc:proposer", "Wiggly Woods", opened)
        val onB = f.proposal("3kpb", b, "did:plc:proposer", "Other Woods", opened)
        for (segment in listOf(a, b)) f.setStatus(segment, SegmentStatus.ACTIVE)
        repeat(6) {
            f.efforts("did:plc:up$it", a)
            f.efforts("did:plc:up$it", b)
            f.vote(onA, "did:plc:up$it")
            f.vote(onB, "did:plc:up$it")
        }
        clock.now += 8.days

        val change = runBlocking { f.passes.updateNameOf(a) }
        assertEquals(mapOf(onA to ProposalState.ADOPTED), change.changed)
        assertEquals("Wiggly Woods", f.row(a)[Segments.currentName])
        assertEquals(ProposalState.OPEN, f.proposalState(onB), "qualifies too, but not named")
        assertEquals("Hill", f.row(b)[Segments.currentName])
    }

    @Test
    fun terrainConfiguredSaysWhetherElevationIsQueued() {
        assertTrue(SegmentJobs(terrain = true).terrainConfigured)
        assertFalse(SegmentJobs().terrainConfigured)
    }
}
