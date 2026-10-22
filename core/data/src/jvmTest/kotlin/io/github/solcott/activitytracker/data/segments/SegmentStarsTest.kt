package io.github.solcott.activitytracker.data.segments

import co.touchlab.kermit.Logger
import io.github.solcott.activitytracker.atproto.OAuthClientConfig
import io.github.solcott.activitytracker.atproto.segmentStarRkey
import io.github.solcott.activitytracker.atproto.xrpc.XrpcException
import io.github.solcott.activitytracker.data.FakeAthletePds
import io.github.solcott.activitytracker.data.activity.OutboxProcessor
import io.github.solcott.activitytracker.data.activity.OutboxWriter
import io.github.solcott.activitytracker.lexicon.AtJson
import io.github.solcott.activitytracker.lexicon.AtUri
import io.github.solcott.activitytracker.lexicon.Cid
import io.github.solcott.activitytracker.lexicon.Did
import io.github.solcott.activitytracker.lexicon.Nsid
import io.github.solcott.activitytracker.lexicon.app.AthleteSpace
import io.github.solcott.activitytracker.lexicon.app.SegmentStarRecord
import io.github.solcott.activitytracker.lexicon.atproto.SpaceErrors
import io.github.solcott.activitytracker.lexicon.atproto.StrongRef
import io.github.solcott.dataresult.DataError
import io.github.solcott.dataresult.Origin
import io.github.solcott.dataresult.Outcome
import io.github.solcott.dataresult.dataOrNull
import io.github.solcott.dataresult.errorOrNull
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** Stars on segments in the data layer (segments/segment-favorites): where they go, one per segment, and what shows before they're sent. */
class SegmentStarsTest {
    private val pds = FakeAthletePds()
    private val bob = Did("did:plc:bob")
    private var now = Instant.parse("2026-10-21T12:00:00Z")
    private val clock =
        object : Clock {
            override fun now() = now
        }

    private fun segment(rkey: String) = StrongRef(AtUri.of(bob, Nsid.FEED_SEGMENT, rkey), Cid("bafy$rkey"))

    private suspend fun stars(scope: String? = null): SegmentStars {
        val s = pds.session(scope)
        return SegmentStars(s.clients, OutboxWriter(s.db, s.clients, clock), OutboxProcessor(s.db, s.clients, Logger), clock)
    }

    private suspend fun key(segment: StrongRef) = "${Nsid.FEED_SEGMENT_STAR}/${segmentStarRkey(segment.uri)}"

    @Test
    fun aStarGoesOnlyInSelfKeyedBySegment() = runTest {
        val stars = stars()
        val hill = segment("3kh")
        stars.setStarred(hill, starred = true)

        val rkey = segmentStarRkey(hill.uri)
        assertEquals(listOf("put ${AthleteSpace.SELF} ${Nsid.FEED_SEGMENT_STAR}/$rkey"), pds.writes)
        assertEquals(setOf(AthleteSpace.SELF), pds.placesOf(Nsid.FEED_SEGMENT_STAR, rkey), "nothing public records it")
        val stored = pds.stored.getValue(AthleteSpace.SELF).getValue(key(hill))
        assertEquals(hill.uri.value, stored["subject"]!!.jsonObject["uri"]?.jsonPrimitive?.content)
        assertEquals(true, stars.isStarred(hill.uri))
    }

    @Test
    fun starringAgainOnAnotherDeviceIsTheSameRecord() = runTest {
        val hill = segment("3kh")
        stars().setStarred(hill, starred = true)
        val other = stars()
        other.setStarred(hill, starred = true)
        assertEquals(1, pds.stored.getValue(AthleteSpace.SELF).keys.count { it.startsWith(Nsid.FEED_SEGMENT_STAR) })

        stars().setStarred(hill, starred = false)
        assertEquals(emptySet(), pds.placesOf(Nsid.FEED_SEGMENT_STAR, segmentStarRkey(hill.uri)))
        assertEquals(false, stars().isStarred(hill.uri))
    }

    @Test
    fun anOfflineStarShowsAtOnceAndIsSentLater() = runTest {
        val stars = stars()
        val hill = segment("3kh")
        pds.offline = true
        stars.setStarred(hill, starred = true)
        stars.setStarred(hill, starred = false)
        stars.setStarred(hill, starred = true)
        assertEquals(true, stars.isStarred(hill.uri))
        assertTrue(pds.stored[AthleteSpace.SELF].orEmpty().isEmpty())

        pds.offline = false
        assertEquals(listOf(hill), stars.starredOutcome().dataOrNull()?.map { it.segment }, "listed before it's sent")
        stars.setStarred(segment("3ko"), starred = false) // The next write drains the outbox.
        assertEquals(setOf(AthleteSpace.SELF), pds.placesOf(Nsid.FEED_SEGMENT_STAR, segmentStarRkey(hill.uri)))
    }

    @Test
    fun theListIsNewestFirstWithThisSessionsChanges() = runTest {
        val stars = stars()
        val (a, b, c) = listOf(segment("3ka"), segment("3kb"), segment("3kc"))
        stars.setStarred(a, starred = true)
        now += 1.minutes
        stars.setStarred(b, starred = true)
        now += 1.minutes
        stars.setStarred(c, starred = true)
        assertEquals(listOf(c, b, a), stars().starredOutcome().dataOrNull()?.map { it.segment }, "read back from the PDS")

        pds.offline = true
        stars.setStarred(b, starred = false)
        pds.offline = false
        assertEquals(listOf(c, a), stars.starredOutcome().dataOrNull()?.map { it.segment }, "an unsent unstar is left out")
    }

    @Test
    fun strayRecordsAreLeftOut() = runTest {
        val hill = segment("3kh")
        val record = SegmentStarRecord(hill, now)
        val self = pds.stored.getOrPut(AthleteSpace.SELF) { mutableMapOf() }
        self["${Nsid.FEED_SEGMENT_STAR}/elsewhere"] = AtJson.encodeToJsonElement(SegmentStarRecord.serializer(), record).jsonObject
        self["${Nsid.FEED_SEGMENT_STAR}/${segmentStarRkey(segment("3kx").uri)}"] = AtJson.parseToJsonElement("""{"subject":1}""").jsonObject
        assertEquals(Outcome.Data(emptyList(), Origin.Network), stars().starredOutcome())
    }

    @Test
    fun starsThatCantBeReadAreUnknownNotNone() = runTest {
        val stars = stars()
        pds.offline = true
        assertNull(stars.isStarred(segment("3kh").uri))
        assertEquals("InternalServerError", (stars.starredOutcome().errorOrNull as? DataError.Api)?.code, "why, not none")
    }

    @Test
    fun unreadableStarsSayWhyEvenWithThisSessionsChanges() = runTest {
        val stars = stars()
        stars.setStarred(segment("3kh"), starred = true)
        pds.offline = true
        assertTrue(stars.starredOutcome() is Outcome.Error, "a star made here doesn't stand in for the rest")
    }

    @Test
    fun anUnsentStarShowsOverNoStarsStored() = runTest {
        val stars = stars()
        pds.offline = true
        stars.setStarred(segment("3kh"), starred = true)
        pds.offline = false
        assertEquals(listOf(segment("3kh")), stars.starredOutcome().dataOrNull()?.map { it.segment })
    }

    @Test
    fun noStarsYetIsAnEmptyList() = runTest {
        val stars = stars()
        assertEquals(Outcome.Data(emptyList(), Origin.Network), stars.starredOutcome())
        assertEquals(false, stars.isStarred(segment("3kh").uri))
    }

    @Test
    fun aSessionWithoutTheScopeIsRefusedBeforeAnythingChanges() = runTest {
        val stars = stars(scope = "${OAuthClientConfig.DEFAULT_SCOPE}&collection=${Nsid.FEED_ACTIVITY}")
        assertFalse(stars.canUse)
        val e = assertFailsWith<XrpcException> { stars.setStarred(segment("3kh"), starred = true) }
        assertEquals(SpaceErrors.SCOPE_MISSING, e.error)
        assertEquals(emptyList(), pds.writes)
        assertEquals(emptyMap(), stars.overrides.value)
        assertNull(stars.isStarred(segment("3kh").uri))
        assertTrue(stars.starredOutcome() is Outcome.Error)
    }
}
