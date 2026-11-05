package io.github.solcott.activitytracker.appview

import io.github.solcott.activitytracker.appview.db.SegmentNameProposals
import io.github.solcott.activitytracker.appview.db.tx
import io.github.solcott.activitytracker.lexicon.Nsid
import io.github.solcott.activitytracker.lexicon.app.AthleteSpace
import io.github.solcott.activitytracker.lexicon.app.SegmentStatus
import io.github.solcott.activitytracker.lexicon.app.SubSport
import io.github.solcott.activitytracker.lexicon.app.Visibility
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.days
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll

/** Indexing dive sites (dive-sites/site-records) and matching dives to them (dive-sites/site-visits). */
class DiveSitesTest : DiveSiteTestBase() {
    // Records

    @Test
    fun aSiteFromTheRepoIsIndexedProposed() {
        site("3ks")
        assertEquals(SegmentStatus.PROPOSED, siteStatus(siteUri("3ks")))
    }

    @Test
    fun aSiteInASpaceIsIgnored() {
        spaceWrite(space(Visibility.FOLLOWERS), Nsid.FEED_DIVE_SITE, "3ks", siteJson())
        spaceWrite(self(alice), Nsid.FEED_DIVE_SITE, "3kt", siteJson())
        assertNull(siteStatus(siteUri("3ks")))
        assertNull(siteStatus(siteUri("3kt")))
    }

    @Test
    fun anInvalidSiteIsNotIndexed() {
        commit(alice, Nsid.FEED_DIVE_SITE, "3ks", siteJson(radius = 2000))
        commit(alice, Nsid.FEED_DIVE_SITE, "3kt", siteJson(name = "   "))
        commit(alice, Nsid.FEED_DIVE_SITE, "3ku", siteJson(source = "rumour"))
        commit(alice, Nsid.FEED_DIVE_SITE, "3kv", siteJson(radius = 10))
        listOf("3ks", "3kt", "3ku", "3kv").forEach { assertNull(siteStatus(siteUri(it)), it) }
    }

    @Test
    fun aSiteWithin50mOfALiveOneIsItsDuplicate() {
        site("3ks")
        site("3kt", east = 30.0, did = bob)
        site("3ku", east = 200.0, did = carol)
        assertEquals(SegmentStatus.DUPLICATE, siteStatus(siteUri("3kt", bob)))
        assertEquals(siteUri("3ks"), duplicateOf(siteUri("3kt", bob)))
        assertEquals(SegmentStatus.PROPOSED, siteStatus(siteUri("3ku", carol)), "a neighbouring reef")
    }

    @Test
    fun aDuplicateIsCheckedAgainWhenItsOriginalGoes() {
        site("3ks")
        site("3kt", east = 30.0, did = bob)
        commit(alice, Nsid.FEED_DIVE_SITE, "3ks", null)
        assertNull(siteStatus(siteUri("3ks")))
        assertEquals(SegmentStatus.PROPOSED, siteStatus(siteUri("3kt", bob)))
        assertNull(duplicateOf(siteUri("3kt", bob)))
    }

    @Test
    fun anEleventhNewSiteInADayIsDropped() {
        repeat(11) { site("3k$it", east = it * 1000.0) }
        assertEquals(SegmentStatus.PROPOSED, siteStatus(siteUri("3k9")))
        assertNull(siteStatus(siteUri("3k10")))
        site("3k0", east = 0.0, name = "Renamed")
        assertEquals(SegmentStatus.PROPOSED, siteStatus(siteUri("3k0")), "rewriting an indexed site doesn't count")
        repeat(11) { site("3k$it", east = it * 1000.0, did = importer) }
        assertEquals(SegmentStatus.PROPOSED, siteStatus(siteUri("3k10", importer)), "the importer isn't limited")
    }

    @Test
    fun deletingASiteRemovesItsVisitsAndNames() {
        site("3ks")
        diveAt("3ka")
        match()
        assertEquals(1, visits().size)
        runBlocking {
            db.tx {
                SegmentNameProposals.insert {
                    it[uri] = "at://$bob/${Nsid.FEED_SEGMENT_NAME_PROPOSAL}/3kp"
                    it[cid] = "bafyp"
                    it[segmentUri] = siteUri("3ks")
                    it[proposerDid] = bob
                    it[name] = "Shark Alley"
                    it[state] = "open"
                    it[openedAt] = 0
                }
            }
        }
        commit(alice, Nsid.FEED_DIVE_SITE, "3ks", null)
        assertEquals(emptyList(), visits())
        assertEquals(0, runBlocking { db.tx { SegmentNameProposals.selectAll().count() } })
    }

    @Test
    fun theThirdDistinctDiverPromotesASiteAndOnlyMeDivesCount() {
        site("3ks")
        diveAt("3ka")
        diveAt("3kb", did = bob)
        match()
        assertEquals(SegmentStatus.PROPOSED, siteStatus(siteUri("3ks")))
        diveAt("3kc", did = carol, tier = Visibility.SELF)
        match()
        assertEquals(SegmentStatus.ACTIVE, siteStatus(siteUri("3ks")))
    }

    @Test
    fun anUnvisitedSiteIsArchivedAfter180Days() {
        site("3ks")
        site("3kt", east = 5000.0)
        diveAt("3ka")
        match()
        runBlocking { matcherAt(athletes = 1).updateStatuses() }
        assertEquals(SegmentStatus.ACTIVE, siteStatus(siteUri("3ks")), "a lowered threshold promotes")
        runBlocking { matcherAt(ahead = 179.days).updateStatuses() }
        assertEquals(SegmentStatus.PROPOSED, siteStatus(siteUri("3kt")))
        runBlocking { matcherAt(ahead = 181.days).updateStatuses() }
        assertEquals(SegmentStatus.ARCHIVED, siteStatus(siteUri("3kt")))
        assertEquals(SegmentStatus.ACTIVE, siteStatus(siteUri("3ks")), "active sites are never archived")
    }

    // Visits

    @Test
    fun aOnePositionTrackVisitsForADiveButNotForARun() {
        site("3ks")
        diveAt("3ka")
        matching(true)
        activity("3kb")
        track("3kb", listOf(listOf(0.0 to 0.0)))
        match()
        assertEquals(listOf(Visit(activityUri("3ka"), siteUri("3ks"), Visibility.PUBLIC, Visibility.PUBLIC)), visits())
    }

    @Test
    fun matchingOffGivesNoVisitAndDropsThem() {
        site("3ks")
        matching(false)
        dive("3ka")
        track("3ka", listOf(listOf(0.0 to 0.0)))
        match()
        assertEquals(emptyList(), visits())
        matching(true)
        match()
        assertEquals(1, visits().size)
        matching(false)
        assertEquals(emptyList(), visits())
    }

    @Test
    fun aPoolSessionGetsNoVisit() {
        site("3ks")
        matching(true)
        dive("3ka", subSport = SubSport.POOL_APNEA)
        track("3ka", listOf(listOf(0.0 to 0.0)))
        match()
        assertEquals(emptyList(), visits())
    }

    @Test
    fun aDiveVisitsTheNearestSiteContainingItsEntry() {
        site("3ks", east = 120.0, radius = 150)
        site("3kt", east = -40.0, radius = 100, did = bob)
        diveAt("3ka")
        match()
        assertEquals(siteUri("3kt", bob), visits().single().site)
    }

    @Test
    fun aDiveOutsideEverySiteHasNoVisit() {
        site("3ks")
        diveAt("3ka", east = 600.0)
        match()
        assertEquals(emptyList(), visits())
    }

    @Test
    fun aVisitIsAtTheNarrowestOfItsActivityAndMap() {
        site("3ks")
        matching(true)
        dive("3ka")
        put(
            alice,
            Nsid.FEED_ACTIVITY_DETAILS,
            "3ka",
            """{"activity":"${activityUri("3ka")}","polyline":"_p~iF~ps|U","createdAt":"2026-10-05T08:00:00Z"}""",
            Visibility.SELF,
        )
        track("3ka", listOf(listOf(0.0 to 0.0)))
        match()
        assertEquals(Visibility.SELF, visits().single().visibility)
    }

    @Test
    fun aNewSiteFindsOldDives() {
        diveAt("3ka")
        match()
        assertEquals(emptyList(), visits())
        site("3ks")
        match()
        assertEquals(siteUri("3ks"), visits().single().site)
    }

    @Test
    fun movingASiteRecomputesItsVisits() {
        site("3ks")
        diveAt("3ka")
        match()
        assertEquals(1, visits().size)
        site("3ks", east = 80.0, radius = 50)
        match()
        assertEquals(emptyList(), visits(), "the entry is now 80 m out, past a 50 m radius")
        site("3ks", east = 80.0, radius = 100)
        match()
        assertEquals(1, visits().size)
    }

    @Test
    fun deletingTheDiveOrItsTrackDropsItsVisit() {
        site("3ks")
        diveAt("3ka")
        diveAt("3kb")
        match()
        assertEquals(2, visits().size)
        remove(alice, Nsid.FEED_ACTIVITY, "3ka")
        runBlocking { indexer.spaceDelete(self(alice), Nsid.FEED_SEGMENT_TRACK, "3kb") }
        assertEquals(emptyList(), visits())
    }

    @Test
    fun deletingTheSelfSpaceDropsVisits() {
        site("3ks")
        diveAt("3ka")
        match()
        runBlocking { indexer.dropSpace(space(AthleteSpace.SELF)) }
        assertEquals(emptyList(), visits())
    }
}
