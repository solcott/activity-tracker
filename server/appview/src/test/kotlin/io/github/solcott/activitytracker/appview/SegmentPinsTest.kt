package io.github.solcott.activitytracker.appview

import io.github.solcott.activitytracker.appview.api.SpaceBlobs
import io.github.solcott.activitytracker.appview.db.Segments
import io.github.solcott.activitytracker.appview.db.tx
import io.github.solcott.activitytracker.appview.segments.PinResult
import io.github.solcott.activitytracker.appview.segments.SegmentMatcher
import io.github.solcott.activitytracker.appview.segments.SegmentPinKind
import io.github.solcott.activitytracker.atproto.xrpc.XrpcBytes
import io.github.solcott.activitytracker.lexicon.Did
import io.github.solcott.activitytracker.lexicon.Nsid
import io.github.solcott.activitytracker.lexicon.app.GetSegmentLeaderboard
import io.github.solcott.activitytracker.lexicon.app.SearchSegments
import io.github.solcott.activitytracker.lexicon.app.SegmentKind
import io.github.solcott.activitytracker.lexicon.app.SegmentSportGroup
import io.github.solcott.activitytracker.lexicon.app.SegmentStatus
import io.ktor.client.HttpClient
import io.ktor.server.testing.testApplication
import kotlin.math.roundToInt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.selectAll

/** Operators' pins on a segment's status and name (segments/segment-pins), through the service the admin site calls. */
class SegmentPinsTest : SegmentTestBase() {
    private val operator = Did("did:plc:operator")
    private val dave = "did:plc:dave"
    private val pins = indexer.segmentPins
    private val east = listOf(5000.0 to 0.0, 5000.0 to 1000.0)

    private fun row(uri: String) = runBlocking { db.tx { Segments.selectAll().where { Segments.uri eq uri }.single() } }

    private fun status(uri: String): String = row(uri)[Segments.status]

    private fun duplicateOf(uri: String): String? = row(uri)[Segments.duplicateOf]

    private fun currentName(uri: String): String = row(uri)[Segments.currentName]

    private fun pinStatus(uri: String, status: String, duplicateOf: String? = null, note: String? = null) = runBlocking {
        pins.pinStatus(uri, status, duplicateOf, operator, note)
    }

    private fun unpinStatus(uri: String) = runBlocking { pins.unpinStatus(uri) }

    /** A matcher whose clock runs [ahead] of now, needing [athletes] for a segment to become active. */
    private fun statusPass(ahead: Duration = Duration.ZERO, athletes: Int = SegmentMatcher.DEFAULT_ACTIVE_ATHLETES) = runBlocking {
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
            .updateStatuses()
    }

    /** A Public run along [straight] by [did], with matching on. */
    private fun ride(did: String, rkey: String = "3ka") {
        matching(true, did = did)
        activity(rkey, did = did)
        track(rkey, did = did)
    }

    /** The box around [straight]'s middle, as `searchSegments` takes it. */
    private fun box(): Map<String, String> {
        val sw = at(-500.0, 400.0)
        val ne = at(500.0, 600.0)
        return mapOf(
            "minLat" to "${(sw.lat * 1e7).roundToInt()}",
            "minLng" to "${(sw.lng * 1e7).roundToInt()}",
            "maxLat" to "${(ne.lat * 1e7).roundToInt()}",
            "maxLng" to "${(ne.lng * 1e7).roundToInt()}",
        )
    }

    private suspend fun HttpClient.searched(viewer: String? = null) =
        xrpcOk(SearchSegments.method, box(), viewer).segments.map { it.uri.value }

    private suspend fun HttpClient.board(uri: String) =
        xrpcOk(GetSegmentLeaderboard.method, mapOf("segment" to uri)).entries.map { it.athlete.did.value }

    @Test
    fun pinningActiveListsAndRanksASegmentAtOnceAndTheStatusPassLeavesIt() = testApplication {
        application { appView(config, db, verifier) }
        segment("3ks")
        ride(alice)
        match()
        assertEquals(SegmentStatus.PROPOSED, status(segmentUri("3ks")))
        assertEquals(emptyList(), client.searched(viewer = carol), "a proposed segment isn't listed to strangers")

        assertEquals(PinResult.Pinned, pinStatus(segmentUri("3ks"), SegmentStatus.ACTIVE, note = "a real trail"))
        assertEquals(SegmentStatus.ACTIVE, status(segmentUri("3ks")))
        assertEquals(listOf(segmentUri("3ks")), client.searched(viewer = carol))
        assertEquals(listOf(alice), client.board(segmentUri("3ks")), "one rider, ranked")

        statusPass(ahead = 200.days)
        assertEquals(SegmentStatus.ACTIVE, status(segmentUri("3ks")), "never archived while pinned")

        // Unpinned, it's derived again: proposed with one rider, and archived by the next pass.
        assertEquals(PinResult.Unpinned, unpinStatus(segmentUri("3ks")))
        assertEquals(SegmentStatus.PROPOSED, status(segmentUri("3ks")))
        statusPass(ahead = 200.days)
        assertEquals(SegmentStatus.ARCHIVED, status(segmentUri("3ks")))
        assertEquals(PinResult.NoPin, unpinStatus(segmentUri("3ks")))
    }

    @Test
    fun aSegmentPinnedArchivedIsNeverPromoted() {
        segment("3ks")
        ride(alice)
        match()
        pinStatus(segmentUri("3ks"), SegmentStatus.ARCHIVED)
        statusPass(athletes = 1)
        assertEquals(SegmentStatus.ARCHIVED, status(segmentUri("3ks")))
        assertEquals(1, efforts().size, "its effort is kept")

        ride(bob, rkey = "3kb")
        match()
        assertEquals(listOf(activityUri("3ka")), efforts().map { it.activity }, "no new efforts")
    }

    @Test
    fun pinningArchivedEmptiesTheBoardAndUnpinningRestoresIt() = testApplication {
        application { appView(config, db, verifier) }
        segment("3ks")
        listOf(alice, bob, carol).forEach { ride(it, rkey = "3k${it.last()}") }
        match()
        assertEquals(SegmentStatus.ACTIVE, status(segmentUri("3ks")))
        assertEquals(3, client.board(segmentUri("3ks")).size)

        pinStatus(segmentUri("3ks"), SegmentStatus.ARCHIVED)
        assertEquals(3, efforts().size, "efforts kept")
        assertEquals(emptyList(), client.board(segmentUri("3ks")))
        assertEquals(emptyList(), client.searched())

        unpinStatus(segmentUri("3ks"))
        assertEquals(SegmentStatus.ACTIVE, status(segmentUri("3ks")), "active before the pin, its line unchanged")
        assertEquals(3, client.board(segmentUri("3ks")).size)
        assertEquals(listOf(segmentUri("3ks")), client.searched())
    }

    @Test
    fun pinningDuplicateDropsEffortsAndReleasesItsDuplicates() {
        segment("3ks")
        segment("3kc", did = bob)
        segment("3kb", path = east, did = carol)
        assertEquals(SegmentStatus.DUPLICATE, status(segmentUri("3kc", bob)))
        ride(alice)
        match()
        assertEquals(listOf(segmentUri("3ks")), efforts().map { it.segment })

        assertEquals(PinResult.Pinned, pinStatus(segmentUri("3ks"), SegmentStatus.DUPLICATE, segmentUri("3kb", carol)))
        assertEquals(SegmentStatus.DUPLICATE, status(segmentUri("3ks")))
        assertEquals(segmentUri("3kb", carol), duplicateOf(segmentUri("3ks")))
        assertEquals(SegmentStatus.PROPOSED, status(segmentUri("3kc", bob)), "its duplicate is checked again")
        match()
        assertEquals(listOf(segmentUri("3kc", bob)), efforts().map { it.segment }, "efforts gone; bob's copy matched instead")

        unpinStatus(segmentUri("3ks"))
        assertEquals(SegmentStatus.PROPOSED, status(segmentUri("3ks")), "bob's copy came later, so it duplicates nothing earlier")
        assertNull(duplicateOf(segmentUri("3ks")))
        match()
        assertEquals(setOf(segmentUri("3ks"), segmentUri("3kc", bob)), efforts().map { it.segment }.toSet(), "past tracks matched again")
    }

    @Test
    fun aPinnedSegmentReLinedOntoAnotherKeepsItsPinUntilUnpinned() {
        segment("3ke", path = east, did = bob)
        segment("3ks")
        pinStatus(segmentUri("3ks"), SegmentStatus.ACTIVE)
        ride(alice)
        match()
        assertEquals(1, efforts().size)

        segment("3ks", path = east)
        assertEquals(SegmentStatus.ACTIVE, status(segmentUri("3ks")), "the pin holds through a re-line onto an earlier segment")
        assertEquals(emptyList(), efforts(), "its efforts are recomputed against the new line")

        unpinStatus(segmentUri("3ks"))
        assertEquals(SegmentStatus.DUPLICATE, status(segmentUri("3ks")))
        assertEquals(segmentUri("3ke", bob), duplicateOf(segmentUri("3ks")))
    }

    @Test
    fun aDuplicatePinIsDormantWhileItsTargetIsGone() {
        segment("3ka")
        segment("3kb", path = east, did = bob)
        pinStatus(segmentUri("3ka"), SegmentStatus.DUPLICATE, segmentUri("3kb", bob))

        pinStatus(segmentUri("3kb", bob), SegmentStatus.ARCHIVED)
        assertEquals(SegmentStatus.DUPLICATE, status(segmentUri("3ka")), "its target archived, the pin still applies")
        unpinStatus(segmentUri("3kb", bob))

        commit(bob, Nsid.FEED_SEGMENT, "3kb", null)
        assertEquals(SegmentStatus.PROPOSED, status(segmentUri("3ka")), "derived while its target is gone")
        assertTrue(runBlocking { pins.pinsOf(segmentUri("3ka")) }.none { it.inEffect })

        segment("3kb", path = east, did = bob)
        assertEquals(SegmentStatus.DUPLICATE, status(segmentUri("3ka")), "applies again once its target is back")
        assertEquals(segmentUri("3kb", bob), duplicateOf(segmentUri("3ka")))

        segment("3kb", path = east, sport = "ride", did = bob)
        assertEquals(SegmentStatus.PROPOSED, status(segmentUri("3ka")), "derived while their sport groups differ")
    }

    @Test
    fun pinsOutliveTheirRecordAndApplyWhenItReturns() {
        segment("3ks", name = "Trail 214")
        pinStatus(segmentUri("3ks"), SegmentStatus.ARCHIVED)
        runBlocking { pins.pinName(segmentUri("3ks"), "Hogback Climb", null, operator) }

        commit(alice, Nsid.FEED_SEGMENT, "3ks", null)
        val orphaned = runBlocking { pins.orphanedPins(10) }
        assertEquals(listOf(SegmentPinKind.STATUS, SegmentPinKind.NAME), orphaned.map { it.kind })
        assertTrue(orphaned.all { it.segmentUri == segmentUri("3ks") && it.pinnedBy == operator.value })

        segment("3ks", name = "Trail 214")
        assertEquals(SegmentStatus.ARCHIVED, status(segmentUri("3ks")))
        assertEquals("Hogback Climb", currentName(segmentUri("3ks")))
        assertEquals(emptyList(), runBlocking { pins.orphanedPins(10) })
    }

    @Test
    fun aPinnedNameBeatsTheRecordsAndUnpinningRestoresIt() {
        segment("3ks", name = "Trail 214")
        assertEquals(PinResult.Pinned, runBlocking { pins.pinName(segmentUri("3ks"), "  Hogback Climb ", null, operator) })
        assertEquals("Hogback Climb", currentName(segmentUri("3ks")))

        segment("3ks", name = "Trail 214 Connector")
        assertEquals("Hogback Climb", currentName(segmentUri("3ks")), "a rename changes only the original")
        assertEquals("Trail 214 Connector", row(segmentUri("3ks"))[Segments.name])

        assertEquals(PinResult.Unpinned, runBlocking { pins.unpinName(segmentUri("3ks")) })
        assertEquals("Trail 214 Connector", currentName(segmentUri("3ks")))
        assertEquals(PinResult.NoPin, runBlocking { pins.unpinName(segmentUri("3ks")) })
    }

    @Test
    fun invalidPinsAreRefusedAndChangeNothing() {
        segment("3ka")
        segment("3kb", path = east, did = bob)
        segment("3kr", path = listOf(9000.0 to 0.0, 9000.0 to 1000.0), sport = "ride", did = carol)
        segment("3kd", path = east, did = dave)
        assertEquals(SegmentStatus.DUPLICATE, status(segmentUri("3kd", dave)))

        assertIs<PinResult.NotFound>(pinStatus(segmentUri("3kz"), SegmentStatus.ARCHIVED))
        assertIs<PinResult.Invalid>(pinStatus(segmentUri("3ka"), SegmentStatus.PROPOSED))
        assertIs<PinResult.Invalid>(pinStatus(segmentUri("3ka"), SegmentStatus.DUPLICATE))
        assertIs<PinResult.Invalid>(pinStatus(segmentUri("3ka"), SegmentStatus.DUPLICATE, segmentUri("3ka")))
        assertIs<PinResult.Invalid>(pinStatus(segmentUri("3ka"), SegmentStatus.ARCHIVED, segmentUri("3kb", bob)))
        assertIs<PinResult.NotFound>(pinStatus(segmentUri("3ka"), SegmentStatus.DUPLICATE, segmentUri("3kz")))
        assertIs<PinResult.Invalid>(pinStatus(segmentUri("3ka"), SegmentStatus.DUPLICATE, segmentUri("3kr", carol)), "another sport")
        assertIs<PinResult.Invalid>(pinStatus(segmentUri("3ka"), SegmentStatus.DUPLICATE, segmentUri("3kd", dave)), "a duplicate")
        assertIs<PinResult.Invalid>(pinStatus(segmentUri("3ka"), SegmentStatus.ARCHIVED, note = "x".repeat(501)))
        assertIs<PinResult.Invalid>(runBlocking { pins.pinName(segmentUri("3ka"), "   ", null, operator) })
        assertIs<PinResult.Invalid>(runBlocking { pins.pinName(segmentUri("3ka"), null, null, operator) })
        assertIs<PinResult.Invalid>(
            runBlocking { pins.pinName(segmentUri("3ka"), null, "at://$bob/${Nsid.FEED_SEGMENT_NAME_PROPOSAL}/3kp", operator) },
            "not a proposal on this segment",
        )
        assertEquals(emptyList(), runBlocking { pins.pinsOf(segmentUri("3ka")) })
        assertEquals(SegmentStatus.PROPOSED, status(segmentUri("3ka")))

        // A chain: with 3ka pinned a duplicate of 3kb, 3kb can't be pinned a duplicate of anything.
        assertEquals(PinResult.Pinned, pinStatus(segmentUri("3ka"), SegmentStatus.DUPLICATE, segmentUri("3kb", bob)))
        segment("3kc", path = listOf(2000.0 to 0.0, 2000.0 to 1000.0), did = carol)
        val chain = pinStatus(segmentUri("3kb", bob), SegmentStatus.DUPLICATE, segmentUri("3kc", carol))
        assertIs<PinResult.Invalid>(chain)
        assertTrue(segmentUri("3ka") in chain.reason)
        assertEquals(SegmentStatus.PROPOSED, status(segmentUri("3kb", bob)))
    }

    @Test
    fun aLiftPinnedActiveIsMatchedButNeverRankedOrNamed() = testApplication {
        application { appView(config, db, verifier) }
        fun lift(rkey: String, path: List<Pair<Double, Double>> = straight) =
            commit(
                importer,
                Nsid.FEED_SEGMENT,
                rkey,
                """{"name":"Lift 7","sport":"${SegmentSportGroup.ALPINE}","kind":"${SegmentKind.LIFT}","line":"${lineOf(path)}",""" +
                    """"source":"osm","license":"ODbL-1.0","createdAt":"2026-10-01T00:00:00Z"}""",
            )
        lift("quad")
        lift("quad2")
        commit(
            importer,
            Nsid.FEED_SEGMENT,
            "piste",
            segmentJson(path = east, sport = SegmentSportGroup.ALPINE, name = "Summit"),
        )
        val quad2 = segmentUri("quad2", importer)
        assertEquals(SegmentStatus.DUPLICATE, status(quad2))
        assertIs<PinResult.Invalid>(pinStatus(quad2, SegmentStatus.DUPLICATE, segmentUri("piste", importer)), "a lift duplicates lifts")

        pinStatus(quad2, SegmentStatus.ACTIVE)
        assertEquals(SegmentStatus.ACTIVE, status(quad2))
        matching(true, did = alice)
        activity("3kd", sport = "ski", did = alice)
        track("3kd", did = alice)
        match()
        assertTrue(efforts().any { it.segment == quad2 }, "matched")
        assertEquals(emptyList(), client.board(quad2), "never ranked")

        runBlocking { pins.pinName(quad2, "Summit Express", null, operator) }
        assertEquals("Summit Express", currentName(quad2))
    }
}
