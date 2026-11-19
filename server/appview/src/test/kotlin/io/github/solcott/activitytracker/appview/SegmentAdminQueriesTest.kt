package io.github.solcott.activitytracker.appview

import io.github.solcott.activitytracker.appview.admin.ImporterRun
import io.github.solcott.activitytracker.appview.admin.SegmentAdminQueries
import io.github.solcott.activitytracker.appview.admin.SegmentSearch
import io.github.solcott.activitytracker.appview.db.DatabaseFactory
import io.github.solcott.activitytracker.appview.db.Segments
import io.github.solcott.activitytracker.appview.db.tx
import io.github.solcott.activitytracker.appview.segments.ProposalState
import io.github.solcott.activitytracker.lexicon.Did
import io.github.solcott.activitytracker.lexicon.Nsid
import io.github.solcott.activitytracker.lexicon.app.SegmentStatus
import io.github.solcott.activitytracker.lexicon.app.Visibility
import java.util.UUID
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlinx.coroutines.runBlocking
import org.h2.jdbcx.JdbcDataSource
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.update

/** The Segments admin page's reads (admin/segment-tools): search, names under the serving rules, counts, elevation, proposals, runs. */
class SegmentAdminQueriesTest {
    private val db: Database =
        JdbcDataSource().run {
            setURL("jdbc:h2:mem:${UUID.randomUUID()};MODE=PostgreSQL;DB_CLOSE_DELAY=-1")
            DatabaseFactory.migrate(this)
            Database.connect(this)
        }
    private val clock = MutableClock(Instant.parse("2026-10-28T12:00:00Z"))
    private val f = SegmentAdminFixtures(db, clock)
    private val q = f.queries

    @AfterTest fun forget() = f.forgetLabels()

    private fun search(search: SegmentSearch, cursor: String? = null) = runBlocking { q.search(search, cursor) }

    private fun uris(search: SegmentSearch) = search(search).rows.map { it.uri }

    private fun detail(uri: String) = assertNotNull(runBlocking { q.detail(uri) })

    // ---------------------------------------------------------------- Admins search segments

    @Test
    fun byUriOrRkey() {
        val a = f.segment("3ka")
        f.segment("3kb", east = 5000.0)
        assertEquals(listOf(a), uris(SegmentSearch(uri = a)))
        assertEquals(listOf(a), uris(SegmentSearch(uri = "3ka")))
        val row = search(SegmentSearch(uri = a)).rows.single()
        assertEquals(
            listOf("did:plc:alice", "run", "run", SegmentStatus.PROPOSED, "Hill"),
            listOf(row.authorDid, row.sport, row.kind, row.status, row.name),
        )
    }

    @Test
    fun byNameAndStatusIgnoringCase() {
        val proposed = f.segment("3ka", name = "The WIGGLY Path")
        val active = f.segment("3kb", east = 5000.0, name = "Wiggly too")
        val renamed = f.segment("3kc", east = 10000.0, name = "Old wiggly name")
        f.setStatus(active, SegmentStatus.ACTIVE)
        runBlocking { db.tx { Segments.update({ Segments.uri eq renamed }) { it[currentName] = "New" } } }
        f.segment("3kd", east = 15000.0, name = "Straight")
        assertEquals(listOf(proposed, renamed), uris(SegmentSearch(name = "wiggly", status = SegmentStatus.PROPOSED)))
        assertEquals(listOf(active), uris(SegmentSearch(name = "WIGGLY", status = SegmentStatus.ACTIVE)))
    }

    @Test
    fun nameSearchEscapesLikeCharacters() {
        f.segment("3ka", name = "Hill")
        assertEquals(emptyList(), uris(SegmentSearch(name = "%")))
    }

    @Test
    fun bySportAndKind() {
        val run = f.segment("3ka")
        f.segment("3kb", east = 5000.0, sport = "ride")
        assertEquals(listOf(run), uris(SegmentSearch(sport = "run", kind = "run")))
        assertEquals(emptyList(), uris(SegmentSearch(sport = "run", kind = "lift")))
    }

    @Test
    fun byRegion() {
        f.stage("us-vermont", "r1", "vt-1", "vt-2")
        f.stage("us-utah", "r1", "ut-1", east = 50000.0)
        val vt1 = f.segment("vt-1", did = f.importer, east = 5000.0)
        f.segment("ut-1", did = f.importer, east = 50000.0)
        f.segment("3ka")
        assertEquals(listOf(vt1), uris(SegmentSearch(region = "us-vermont")))
    }

    @Test
    fun nothingFound() {
        f.segment("3ka")
        assertEquals(emptyList(), uris(SegmentSearch(uri = f.segmentUri("3kz"))))
    }

    @Test
    fun pagesOfFifty() {
        val all = (0 until 51).map { i -> f.segment("seg-${i.toString().padStart(2, '0')}", did = f.importer, east = i * 3000.0) }
        val first = search(SegmentSearch(sport = "run"))
        assertEquals(SegmentAdminQueries.PAGE_SIZE, first.rows.size)
        val second = search(SegmentSearch(sport = "run"), first.cursor)
        assertNull(second.cursor)
        assertEquals(all.sorted(), (first.rows + second.rows).map { it.uri })
    }

    // ---------------------------------------------------------------- Names shown only where the AppView serves them

    @Test
    fun aTakenDownSegmentsNameIsWithheldAndUnsearchable() {
        val a = f.segment("3ka", name = "Secretname")
        f.label(a, "!takedown")
        val row = search(SegmentSearch(uri = a)).rows.single()
        assertNull(row.name)
        assertEquals(SegmentStatus.PROPOSED, row.status)
        with(detail(a)) {
            assertNull(originalName)
            assertNull(currentName)
            assertEquals(SegmentStatus.PROPOSED, status)
        }
        assertEquals(emptyList(), uris(SegmentSearch(name = "secretname")))
    }

    @Test
    fun aTakenDownAuthorsNamesAreWithheld() {
        val a = f.segment("3ka", name = "Secretname")
        f.label("did:plc:alice", "!takedown")
        assertNull(detail(a).currentName)
    }

    // ---------------------------------------------------------------- The segment page counts efforts only

    @Test
    fun privateEffortsAreCounted() {
        val a = f.segment("3ka")
        f.efforts("did:plc:bob", a)
        f.efforts("did:plc:carol", a, visibility = Visibility.SELF)
        with(detail(a)) {
            assertEquals(2, efforts)
            assertEquals(2, athletes)
            assertEquals(3, activeAthletes)
        }
    }

    // ---------------------------------------------------------------- The segment page shows derived and pinned state

    @Test
    fun showsIdentifiersAndPins() {
        val a = f.segment("3ka")
        runBlocking { f.indexer.segmentPins.pinStatus(a, SegmentStatus.ARCHIVED, null, Did("did:plc:operator"), "not a trail") }
        with(detail(a)) {
            assertEquals(SegmentStatus.ARCHIVED, status)
            assertEquals(a, groupUri)
            assertEquals(clock.now.toEpochMilliseconds(), firstIndexedAt)
            val pin = pins.single()
            assertEquals(listOf("did:plc:operator", "not a trail"), listOf(pin.pinnedBy, pin.note))
            assertTrue(pin.inEffect)
        }
    }

    @Test
    fun aDuplicatePinOnAGoneTargetIsDormant() {
        val a = f.segment("3ka")
        val b = f.segment("3kb", did = "did:plc:bob", east = 5000.0)
        val operator = Did("did:plc:operator")
        runBlocking { f.indexer.segmentPins.pinStatus(a, SegmentStatus.DUPLICATE, b, operator) }
        f.commit("did:plc:bob", Nsid.FEED_SEGMENT, "3kb", null)
        with(detail(a)) {
            assertFalse(pins.single().inEffect)
            assertEquals(SegmentStatus.PROPOSED, status)
        }
    }

    @Test
    fun queuedJobKinds() {
        val a = f.segment("3ka")
        assertEquals(listOf("segment"), detail(a).queuedJobs)
    }

    // ---------------------------------------------------------------- The segment page shows elevation as served

    @Test
    fun oneAthletesClimbIsNotShown() {
        val a = f.segment("3ka")
        runBlocking {
            db.tx {
                Segments.update({ Segments.uri eq a }) {
                    it[effortGainM] = 42
                    it[effortLossM] = 3
                    it[effortAthletes] = 1
                }
            }
        }
        with(detail(a).elevation) {
            assertEquals(1, effortAthletes)
            assertNull(effortGainM)
            assertNull(effortLossM)
            assertNull(served)
            assertNull(terrainGainM)
        }
    }

    @Test
    fun effortElevationAtTheThresholdIsShown() {
        val a = f.segment("3ka")
        runBlocking {
            db.tx {
                Segments.update({ Segments.uri eq a }) {
                    it[effortGainM] = 42
                    it[effortLossM] = 3
                    it[effortAthletes] = 3
                }
            }
        }
        with(detail(a).elevation) {
            assertEquals(42L, effortGainM)
            assertEquals(42L, served?.gain)
        }
    }

    // ---------------------------------------------------------------- Admins see a segment's name proposals with totals

    @Test
    fun anOpenProposalHasLiveTotals() {
        val a = f.segment("3ka")
        val p = f.proposal("3kp", a, "did:plc:proposer", "Wiggly", clock.now.toEpochMilliseconds())
        repeat(4) {
            f.efforts("did:plc:up$it", a)
            f.vote(p, "did:plc:up$it")
        }
        f.efforts("did:plc:down", a)
        f.vote(p, "did:plc:down", up = false)
        f.vote(p, "did:plc:stranger") // no effort, so not counted
        val row = detail(a).proposals.single()
        assertEquals(listOf(p, "did:plc:proposer", ProposalState.OPEN, "Wiggly"), listOf(row.uri, row.proposerDid, row.state, row.name))
        assertEquals(4 to 1, row.up to row.down)
    }

    @Test
    fun aHiddenProposalsNameIsWithheld() {
        val a = f.segment("3ka")
        val p = f.proposal("3kp", a, "did:plc:proposer", "Rude", clock.now.toEpochMilliseconds())
        f.label(p, "!hide")
        val row = detail(a).proposals.single()
        assertNull(row.name)
        assertEquals(ProposalState.OPEN, row.state)
    }

    // ---------------------------------------------------------------- Admins see importer runs by region

    @Test
    fun aRunInProgressListsBothRevisions() {
        f.stage("us-vermont", "r1", "vt-1", "vt-2", "vt-3")
        f.stage("us-vermont", "r2", "vt-4", east = 30000.0)
        f.ride("vt-1", "did:plc:bob", ridden = true)
        f.ride("vt-2", "did:plc:bob", ridden = true)
        f.ride("vt-3", "did:plc:carol")
        f.segment("vt-2", did = f.importer, east = 8000.0)
        val runs = assertNotNull(runBlocking { q.importerRuns() })
        assertEquals(listOf("r1", "r2"), runs.map { it.revision })
        val (r1, r2) = runs
        assertEquals(ImporterRun("us-vermont", "r1", 3, 1, 1, r1.lastStagedAt), r1)
        assertEquals(ImporterRun("us-vermont", "r2", 1, 0, 0, r2.lastStagedAt), r2)
    }

    @Test
    fun noImporterConfigured() {
        assertNull(runBlocking { SegmentAdminQueries(db, f.indexer.segmentPins, 3, null).importerRuns() })
    }
}
