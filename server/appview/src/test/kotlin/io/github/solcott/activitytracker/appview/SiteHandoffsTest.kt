package io.github.solcott.activitytracker.appview

import io.github.solcott.activitytracker.appview.api.AvatarUrls
import io.github.solcott.activitytracker.appview.api.DiveSiteViews
import io.github.solcott.activitytracker.appview.api.SegmentCandidateViews
import io.github.solcott.activitytracker.appview.api.XrpcError
import io.github.solcott.activitytracker.appview.db.SegmentNameProposals
import io.github.solcott.activitytracker.appview.db.SubjectHandoffs
import io.github.solcott.activitytracker.appview.db.tx
import io.github.solcott.activitytracker.appview.index.JetstreamEvent
import io.github.solcott.activitytracker.appview.index.handoffId
import io.github.solcott.activitytracker.appview.segments.ProposalState
import io.github.solcott.activitytracker.appview.segments.recomputeCurrentName
import io.github.solcott.activitytracker.lexicon.AtUri
import io.github.solcott.activitytracker.lexicon.Did
import io.github.solcott.activitytracker.lexicon.Nsid
import io.github.solcott.activitytracker.lexicon.app.GetDiveSite
import io.github.solcott.activitytracker.lexicon.app.GetHandoffs
import io.github.solcott.activitytracker.lexicon.app.SegmentStatus
import io.github.solcott.activitytracker.lexicon.app.SubjectHandoff
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll

/** Handing off a removed active dive site to the segment importer (dive-sites/site-handoffs). */
class SiteHandoffsTest : DiveSiteTestBase() {
    private val views = DiveSiteViews(db, AvatarUrls("https://appview.test"), segmentImporter = Did(importer))
    private val importerViews = SegmentCandidateViews(db, Did(importer), indexer.segmentJobs)
    private val blueHole = siteUri("3ks")

    private fun aliceActive() {
        site("3ks")
        setSiteStatus(blueHole, SegmentStatus.ACTIVE)
    }

    private fun adopted(site: String, name: String) = runBlocking {
        db.tx {
            SegmentNameProposals.insert {
                it[uri] = "at://$bob/${Nsid.FEED_SEGMENT_NAME_PROPOSAL}/3kp"
                it[cid] = "bafyp"
                it[segmentUri] = site
                it[proposerDid] = bob
                it[SegmentNameProposals.name] = name
                it[state] = ProposalState.ADOPTED
                it[openedAt] = 1
                it[closedAt] = 2
            }
            recomputeCurrentName(site)
        }
    }

    private fun handoffs() = runBlocking { db.tx { SubjectHandoffs.selectAll().toList() } }

    private fun pending() = runBlocking { importerViews.handoffs(Did(importer), GetHandoffs.Params()).handoffs }

    private fun lookUp(uri: String) = runBlocking { views.site(null, GetDiveSite.Params(AtUri(uri))).site }

    @Test
    fun anActiveSiteRemovedWithItsCreatorsAccountIsHandedOff() {
        aliceActive()
        runBlocking {
            indexer.handle(
                JetstreamEvent.Account(alice, "2026-10-15T12:00:00.000000Z", account = JetstreamEvent.Account.Body(alice, false, "deleted"))
            )
        }
        val handoff = handoffs().single()
        assertEquals(SubjectHandoff.SITE, handoff[SubjectHandoffs.kind])
        assertEquals(100, handoff[SubjectHandoffs.radiusM])
        assertEquals(listOf(SubjectHandoff.SITE), pending().map { it.kind })
    }

    @Test
    fun aProposedSiteIsNotHandedOff() {
        site("3ks")
        remove(alice, Nsid.FEED_DIVE_SITE, "3ks")
        assertEquals(emptyList(), handoffs())
    }

    @Test
    fun theRepublishedSiteIsActiveWithItsNameAndVisits() {
        aliceActive()
        adopted(blueHole, "Shark Alley")
        diveAt("3kd", did = bob)
        match()
        assertEquals(listOf(blueHole), visits().map { it.site })

        remove(alice, Nsid.FEED_DIVE_SITE, "3ks")
        assertEquals("NotFound", assertFailsWith<XrpcError> { lookUp(blueHole) }.error)
        val handoff = pending().single()
        commit(
            importer,
            Nsid.FEED_DIVE_SITE,
            handoff.id,
            """{"name":"${handoff.name}","lat":${handoff.lat},"lng":${handoff.lng},"radius":${handoff.radius},""" +
                """"source":"athlete","license":"CC0-1.0","createdAt":"2026-10-20T00:00:00Z"}""",
        )
        match()

        val copy = siteUri(handoffId(blueHole), importer)
        val view = lookUp(blueHole)
        assertEquals(copy, view.uri.value)
        assertEquals(SegmentStatus.ACTIVE, view.status)
        assertEquals("Shark Alley", view.name)
        assertEquals(listOf(copy), visits().map { it.site })
        assertEquals(emptyList(), pending())
    }

    @Test
    fun anAthletesSiteWithinFiftyMetresTakesOver() {
        aliceActive()
        remove(alice, Nsid.FEED_DIVE_SITE, "3ks")
        site("3kc", east = 20.0, did = carol)

        assertEquals(emptyList(), pending())
        assertEquals(siteUri("3kc", carol), lookUp(blueHole).uri.value)
    }
}
