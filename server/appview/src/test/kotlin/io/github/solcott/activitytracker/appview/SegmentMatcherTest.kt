package io.github.solcott.activitytracker.appview

import io.github.solcott.activitytracker.appview.api.SpaceBlobs
import io.github.solcott.activitytracker.appview.db.SegmentMatchJobs
import io.github.solcott.activitytracker.appview.db.SegmentTrackCells
import io.github.solcott.activitytracker.appview.db.Segments
import io.github.solcott.activitytracker.appview.db.tx
import io.github.solcott.activitytracker.appview.index.JetstreamEvent
import io.github.solcott.activitytracker.appview.segments.SegmentJobs
import io.github.solcott.activitytracker.appview.segments.SegmentMatcher
import io.github.solcott.activitytracker.atproto.xrpc.XrpcBytes
import io.github.solcott.activitytracker.lexicon.Nsid
import io.github.solcott.activitytracker.lexicon.app.SegmentStatus
import io.github.solcott.activitytracker.lexicon.app.Visibility
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.deleteAll
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update

/** The segment matcher (segments/segment-efforts): efforts from usable segment tracks only, kept current as their sources change. */
class SegmentMatcherTest : SegmentTestBase() {
    @Test
    fun aPassCreatesAnEffortWithItsInterpolatedTimes() {
        matching(true)
        segment("3ks")
        activity("3ka")
        track("3ka")
        match()

        // 100 m before the start at 5 m/s, and 1 km of segment.
        assertEquals(
            listOf(Effort(segmentUri("3ks"), activityUri("3ka"), 20, 200, Visibility.PUBLIC, eBike = false, startedOn = "2026-10-06")),
            efforts(),
        )
        assertEquals(emptyList(), jobs())
    }

    @Test
    fun matchingOffCreatesNothingAndReadsNothing() {
        segment("3ks")
        activity("3ka")
        track("3ka")
        match()
        assertEquals(emptyList(), efforts())
        assertEquals(emptyList(), fetched, "no blob is read while matching is off")

        matching(true)
        match()
        assertEquals(1, efforts().size, "matched once it's on")

        matching(false)
        assertEquals(emptyList(), efforts(), "dropped as soon as the setting is indexed")
    }

    @Test
    fun matchingTurnedOffWhileFetchingWritesNothing() {
        matching(true)
        segment("3ks")
        activity("3ka")
        track("3ka")
        // The fake fetch turns matching off before it answers, as a setting indexed mid-fetch would.
        val slow =
            SegmentMatcher(
                db,
                indexer.segmentJobs,
                SpaceBlobs { _, _, cid ->
                    matching(false)
                    XrpcBytes(blobs.getValue(cid), "application/json")
                },
            )
        runBlocking { slow.runDue() }
        assertEquals(emptyList(), efforts())
    }

    @Test
    fun theSportGroupsMustMatch() {
        matching(true)
        segment("3ks", sport = "ride")
        activity("3ka", sport = "run")
        track("3ka")
        activity("3kb", sport = "ride", subSport = "indoor")
        track("3kb")
        activity("3kc", sport = "ride", subSport = "eMountain")
        track("3kc")
        match()
        assertEquals(listOf(activityUri("3kc") to true), efforts().map { it.activity to it.eBike })
    }

    @Test
    fun aMemberOfACombinedActivityIsSkipped() {
        matching(true)
        segment("3ks")
        activity("3ka")
        track("3ka")
        activity("3kb")
        track("3kb")
        match()
        assertEquals(2, efforts().size)

        val members = listOf(activityUri("3ka"), activityUri("3kb")).joinToString(",") { "\"$it\"" }
        activity("3kc", extra = """"combined":{"members":[$members],"sources":[]},""")
        track("3kc")
        match()
        assertEquals(listOf(activityUri("3kc")), efforts().map { it.activity }, "the outing counts once")
    }

    @Test
    fun aNewSegmentIsMatchedAgainstPastTracks() {
        matching(true)
        activity("3ka")
        track("3ka")
        activity("3kb", did = bob)
        matching(true, did = bob)
        track("3kb", did = bob)
        match()
        assertEquals(emptyList(), efforts())
        assertTrue(runBlocking { db.tx { SegmentTrackCells.selectAll().count() } } > 0, "cells are kept for later segments")

        segment("3ks", did = carol)
        match()
        assertEquals(listOf(activityUri("3ka"), activityUri("3kb", bob)).sorted(), efforts().map { it.activity }.sorted())
    }

    @Test
    fun aNarrowedMapCategoryReTiersTheEfforts() {
        matching(true)
        segment("3ks")
        activity("3ka")
        track("3ka")
        match()
        assertEquals(Visibility.PUBLIC, efforts().single().visibility)

        put(
            alice,
            Nsid.FEED_ACTIVITY_DETAILS,
            "3ka",
            """{"activity":"${activityUri("3ka")}","polyline":"_p~iF~ps|U","createdAt":"2026-10-05T08:00:00Z"}""",
            Visibility.FOLLOWERS,
        )
        assertEquals(listOf(SegmentJobs.TIERS to activityUri("3ka")), jobs())
        val fetches = fetched.size
        match()
        assertEquals(Visibility.FOLLOWERS, efforts().single().visibility)
        assertEquals(fetches, fetched.size, "re-tiering reads no blob")

        // Pace/Speed narrower still, through a streams record.
        put(
            alice,
            Nsid.FEED_ACTIVITY_STREAMS,
            "3ka",
            """{"activity":"${activityUri("3ka")}","channels":["speed"],"data":{"${'$'}type":"blob","ref":{"${'$'}link":"bafkspeed"},""" +
                """"mimeType":"application/json","size":100},"createdAt":"2026-10-05T08:00:00Z"}""",
            Visibility.SELF,
        )
        match()
        assertEquals(Visibility.SELF, efforts().single().visibility)
    }

    @Test
    fun anActivityMovedOrDeletedTakesItsEffortsAlong() {
        matching(true)
        segment("3ks")
        activity("3ka")
        track("3ka")
        match()

        activity("3ka", tier = Visibility.MUTUALS)
        remove(alice, Nsid.FEED_ACTIVITY, "3ka")
        match()
        assertEquals(Visibility.MUTUALS, efforts().single().visibility)

        remove(alice, Nsid.FEED_ACTIVITY, "3ka", Visibility.MUTUALS)
        assertEquals(emptyList(), efforts())
    }

    @Test
    fun aReTrimmedTrackLosesItsEffort() {
        matching(true)
        segment("3ks")
        activity("3ka")
        track("3ka")
        match()
        assertEquals(1, efforts().size)

        // A new privacy zone cuts the middle of the segment out of the track.
        track("3ka", pieces = listOf(listOf(0.0 to -100.0, 0.0 to 400.0), listOf(0.0 to 600.0, 0.0 to 1100.0)))
        match()
        assertEquals(emptyList(), efforts())
    }

    @Test
    fun aDeletedTrackLosesItsEffort() {
        matching(true)
        segment("3ks")
        activity("3ka")
        track("3ka")
        match()
        runBlocking { indexer.spaceDelete(self(alice), Nsid.FEED_SEGMENT_TRACK, "3ka") }
        assertEquals(emptyList(), efforts())
    }

    @Test
    fun aTrackBeforeItsActivityIsMatchedWhenTheActivityArrives() {
        matching(true)
        segment("3ks")
        track("3ka")
        match()
        assertEquals(emptyList(), efforts())

        activity("3ka")
        match()
        assertEquals(1, efforts().size)
    }

    @Test
    fun jobsAreIdempotent() {
        matching(true)
        segment("3ks")
        activity("3ka")
        track("3ka")
        track("3ka")
        assertEquals(listOf(SegmentJobs.SEGMENT to segmentUri("3ks"), SegmentJobs.TRACK to trackUri("3ka")), jobs().sortedBy { it.first })
        match()
        val first = efforts()
        track("3ka")
        segment("3ks", name = "Renamed")
        match()
        assertEquals(first, efforts())
    }

    @Test
    fun aFailedFetchBacksOff() {
        matching(true)
        segment("3ks")
        activity("3ka")
        track("3ka")
        blobsDown = true
        match()
        assertEquals(emptyList(), efforts())
        val attempts = runBlocking { db.tx { SegmentMatchJobs.selectAll().map { it[SegmentMatchJobs.attempts] } } }
        assertEquals(listOf(1), attempts, "kept for a later retry")
    }

    @Test
    fun startingBackfillsUsableTracksNeverMatched() {
        matching(true)
        activity("3ka")
        track("3ka")
        runBlocking { db.tx { SegmentMatchJobs.deleteAll() } }
        segment("3ks")
        match()
        assertEquals(emptyList(), efforts(), "the track has no cells, so the segment can't find it")

        runBlocking { matcher.backfill() }
        match()
        assertEquals(1, efforts().size)
        runBlocking { matcher.backfill() }
        assertEquals(emptyList(), jobs(), "once")
    }

    @Test
    fun aSportChangeMatchesAgainAndOtherChangesReTier() {
        matching(true)
        segment("3ks")
        activity("3ka")
        track("3ka")
        match()

        activity("3ka", extra = """"title":"Morning",""")
        assertEquals(listOf(SegmentJobs.TIERS to activityUri("3ka")), jobs())
        match()
        activity("3ka", sport = "ride")
        assertEquals(listOf(SegmentJobs.TRACK to trackUri("3ka")), jobs())
        match()
        assertEquals(emptyList(), efforts(), "a ride doesn't match a run segment")
    }

    @Test
    fun droppingTheSelfSpaceOrTheAccountDropsTheEfforts() {
        matching(true)
        segment("3ks", did = bob)
        activity("3ka")
        track("3ka")
        match()
        runBlocking { indexer.dropSpace(self(alice)) }
        assertEquals(emptyList(), efforts())

        matching(true)
        track("3ka")
        match()
        assertEquals(1, efforts().size)
        runBlocking {
            indexer.handle(
                JetstreamEvent.Account(
                    did = alice,
                    time = "2026-10-15T12:00:00.000000Z",
                    account = JetstreamEvent.Account.Body(alice, active = false, status = "deleted"),
                )
            )
        }
        assertEquals(emptyList(), efforts())
    }

    private fun status(uri: String): String = runBlocking {
        db.tx { Segments.selectAll().where { Segments.uri eq uri }.single()[Segments.status] }
    }

    private fun setStatus(uri: String, status: String) = runBlocking {
        db.tx { Segments.update({ Segments.uri eq uri }) { it[Segments.status] = status } }
    }

    /** A matcher whose clock runs [ahead] of now, needing [athletes] for a segment to become active. */
    private fun matcherAt(ahead: Duration = Duration.ZERO, athletes: Int = SegmentMatcher.DEFAULT_ACTIVE_ATHLETES) =
        SegmentMatcher(
            db,
            indexer.segmentJobs,
            SpaceBlobs { _, _, cid -> blobs[cid]?.let { XrpcBytes(it, "application/json") } },
            clock =
                object : Clock {
                    override fun now() = Clock.System.now() + ahead
                },
            activeAthletes = athletes,
        )

    /** A Public run along the segment by [did], with matching on. */
    private fun ride(did: String, rkey: String = "3ka", tier: String = Visibility.PUBLIC) {
        matching(true, did = did)
        activity(rkey, tier = tier, did = did)
        track(rkey, did = did)
    }

    @Test
    fun duplicateAndArchivedSegmentsGetNoNewEfforts() {
        segment("3ks")
        ride(alice)
        match()
        assertEquals(listOf(segmentUri("3ks")), efforts().map { it.segment })

        setStatus(segmentUri("3ks"), SegmentStatus.ARCHIVED)
        ride(bob)
        match()
        assertEquals(listOf(activityUri("3ka")), efforts().map { it.activity }, "bob's run doesn't match, alice's effort stays")

        // A recompute of alice's run drops its effort on the archived segment.
        track("3ka", speed = 4.0)
        match()
        assertEquals(emptyList(), efforts())

        segment("3kt", path = listOf(5000.0 to 0.0, 5000.0 to 1000.0))
        setStatus(segmentUri("3kt"), SegmentStatus.DUPLICATE)
        runBlocking { indexer.segmentJobs.let { jobs -> db.tx { jobs.enqueueSegment(segmentUri("3kt"), 0) } } }
        match()
        assertEquals(emptyList(), efforts())
    }

    @Test
    fun aSegmentBecomesActiveOnItsThirdAthlete() {
        segment("3ks")
        ride(alice)
        ride(bob)
        match()
        assertEquals(SegmentStatus.PROPOSED, status(segmentUri("3ks")))

        // Carol's run is Only me, and still counts.
        ride(carol, tier = Visibility.SELF)
        match()
        assertEquals(SegmentStatus.ACTIVE, status(segmentUri("3ks")))

        matching(false, did = bob)
        matching(false, did = carol)
        assertEquals(1, efforts().size)
        runBlocking { matcherAt().updateStatuses() }
        assertEquals(SegmentStatus.ACTIVE, status(segmentUri("3ks")), "stays active")
    }

    @Test
    fun theStatusPassPromotesAndArchives() {
        segment("3ks")
        segment("3kt", path = listOf(5000.0 to 0.0, 5000.0 to 1000.0))
        ride(alice)
        match()
        runBlocking { matcherAt(athletes = 1).updateStatuses() }
        assertEquals(SegmentStatus.ACTIVE, status(segmentUri("3ks")), "a lowered threshold promotes")
        assertEquals(SegmentStatus.PROPOSED, status(segmentUri("3kt")))

        runBlocking { matcherAt(ahead = 179.days).updateStatuses() }
        assertEquals(SegmentStatus.PROPOSED, status(segmentUri("3kt")))
        runBlocking { matcherAt(ahead = 181.days).updateStatuses() }
        assertEquals(SegmentStatus.ARCHIVED, status(segmentUri("3kt")))
        assertEquals(SegmentStatus.ACTIVE, status(segmentUri("3ks")), "active segments are never archived")
    }
}
