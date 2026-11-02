package io.github.solcott.activitytracker.data

import co.touchlab.kermit.Logger
import io.github.solcott.activitytracker.data.activity.ActivityDraft
import io.github.solcott.activitytracker.data.activity.OutboxProcessor
import io.github.solcott.activitytracker.data.activity.PrivacySettings
import io.github.solcott.activitytracker.data.segments.GroupChoice
import io.github.solcott.activitytracker.lexicon.AtJson
import io.github.solcott.activitytracker.lexicon.Nsid
import io.github.solcott.activitytracker.lexicon.app.ActivityType
import io.github.solcott.activitytracker.lexicon.app.GroupPrefsRecord
import io.github.solcott.activitytracker.lexicon.app.PrivacyZone
import io.github.solcott.activitytracker.lexicon.app.PrivacyZonesRecord
import io.github.solcott.activitytracker.lexicon.app.SegmentTrackRecord
import io.github.solcott.activitytracker.lexicon.app.Sport
import io.github.solcott.activitytracker.lexicon.app.Visibility
import io.github.solcott.activitytracker.location.Gpx
import io.github.solcott.activitytracker.location.LocationSample
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.jsonPrimitive

/**
 * The grouping choice (feed/group-activities) and the segment tracks it keeps (segments/segment-track): written while segment matching or
 * grouping is on, deleted only when both are off, and carrying the leave mark across rewrites.
 */
class GroupTrackTest {
    private val pds = FakeAthletePds()
    private val start = Instant.parse("2026-10-14T07:00:00Z")

    /** About 3.3 km east along the equator from (0, 0), a point every 11 m and 4 s. */
    private val route =
        (0..300).map { LocationSample(0.0, it * 0.0001, 12.0, speed = null, accuracy = 5.0, time = start + (it * 4).seconds) }

    private val home = PrivacyZone(latitudeE7 = 0, longitudeE7 = 0, radiusMeters = 200, label = "Home")

    private fun draft() =
        ActivityDraft(
            type = ActivityType(Sport.RIDE),
            title = "Ride",
            description = null,
            startedAt = start,
            elapsed = 20.minutes,
            distanceMeters = 3336.0,
            route = route,
            gpx = Gpx.write("Ride", Sport.RIDE, route).encodeToByteArray(),
        )

    private fun segmentTrack(rkey: String): SegmentTrackRecord? =
        pds.stored[Visibility.SELF]?.get("${Nsid.FEED_SEGMENT_TRACK}/$rkey")?.let {
            AtJson.decodeFromJsonElement(SegmentTrackRecord.serializer(), it)
        }

    private fun zonesRevision(): String? =
        pds.stored[Visibility.SELF]?.get("${Nsid.ACTOR_PRIVACY_ZONES}/self")?.let {
            AtJson.decodeFromJsonElement(PrivacyZonesRecord.serializer(), it).revision
        }

    private suspend fun FakeAthletePds.Session.grouping(choice: GroupChoice) = segmentsOf(clients).groups.save(choice)

    private suspend fun FakeAthletePds.Session.matching(on: Boolean) = segmentsOf(clients).prefs.save(on)

    private suspend fun FakeAthletePds.Session.sweep() = segmentsOf(clients).sweeper.sweep()

    private suspend fun FakeAthletePds.Session.drain() = OutboxProcessor(db, clients, Logger).drain()

    // The choice

    @Test
    fun anAthleteWhoNeverChoseIsNeverAskedAndTheChoiceIsStoredInSelfOnly() = runTest {
        val s = pds.session()
        assertEquals(GroupChoice.NEVER_ASKED, segmentsOf(s.clients).groups.load())

        s.grouping(GroupChoice.CONNECTED)
        assertEquals(setOf(Visibility.SELF), pds.placesOf(Nsid.ACTOR_GROUP_PREFS, GroupPrefsRecord.RKEY))
        val record = pds.stored.getValue(Visibility.SELF).getValue("${Nsid.ACTOR_GROUP_PREFS}/self")
        assertEquals("connected", record.getValue("mode").jsonPrimitive.content)
        assertEquals(GroupChoice.CONNECTED, segmentsOf(pds.session().clients).groups.load(), "another device reads it")
    }

    @Test
    fun declinedIsntNeverAsked() = runTest {
        val s = pds.session()
        s.activities.publish(draft())
        val groups = segmentsOf(s.clients).groups
        assertTrue(groups.shouldAsk())

        s.grouping(GroupChoice.OFF)
        assertFalse(groups.shouldAsk())
        assertFalse(segmentsOf(pds.session().clients).groups.shouldAsk(), "no device asks again")
    }

    @Test
    fun aSaveOfflineFailsAndKeepsTheEarlierChoice() = runTest {
        val s = pds.session()
        s.grouping(GroupChoice.OFF)
        pds.offline = true

        assertTrue(runCatching { s.grouping(GroupChoice.ANYONE) }.isFailure)
        assertEquals(GroupChoice.OFF, segmentsOf(s.clients).groups.current.value)
        pds.offline = false
        assertEquals(GroupChoice.OFF, segmentsOf(pds.session().clients).groups.load())
    }

    @Test
    fun aGrantWithoutTheChoiceCantUseIt() = runTest {
        val scope =
            "atproto transition:generic space:${Nsid.SPACE_ATHLETE}?collection=${Nsid.FEED_ACTIVITY}" +
                "&collection=${Nsid.FEED_ACTIVITY_TRACK}&collection=${Nsid.ACTOR_SEGMENT_PREFS}&collection=${Nsid.FEED_SEGMENT_TRACK}"
        val groups = segmentsOf(pds.session(scope = scope).clients).groups

        assertFalse(groups.canUse)
        assertNull(groups.load())
        assertTrue(runCatching { groups.save(GroupChoice.ANYONE) }.isFailure)
        assertEquals(emptySet(), pds.placesOf(Nsid.ACTOR_GROUP_PREFS, GroupPrefsRecord.RKEY))
    }

    @Test
    fun anUnknownStoredModeReadsAsOff() {
        assertEquals(GroupChoice.OFF, GroupChoice.of("friendsOfFriends"))
        assertEquals(GroupChoice.ANYONE, GroupChoice.of("anyone"))
    }

    // Segment tracks

    @Test
    fun groupingOnWithMatchingOffWritesSegmentTracks() = runTest {
        val s = pds.session()
        s.matching(false)
        s.grouping(GroupChoice.CONNECTED)
        val uri = s.activities.publish(draft())
        assertNotNull(segmentTrack(uri.rkey!!))
    }

    @Test
    fun turningGroupingOnFirstCoversEarlierActivities() = runTest {
        val s = pds.session()
        val uris = List(3) { s.activities.publish(draft()) }
        uris.forEach { assertNull(segmentTrack(it.rkey!!)) }

        s.grouping(GroupChoice.ANYONE)
        s.sweep()
        uris.forEach { assertNotNull(segmentTrack(it.rkey!!), "$it") }
    }

    @Test
    fun anInterruptedBackfillFinishesInALaterSession() = runTest {
        val s = pds.session()
        val uris = List(3) { s.activities.publish(draft()) }
        s.grouping(GroupChoice.ANYONE)
        pds.failPutsOf = Nsid.FEED_SEGMENT_TRACK
        s.sweep()
        uris.forEach { assertNull(segmentTrack(it.rkey!!)) }
        pds.failPutsOf = null

        pds.session().activities.repairRoutes()
        uris.forEach { assertNotNull(segmentTrack(it.rkey!!), "$it") }
    }

    @Test
    fun turningMatchingOffKeepsTracksWhileGroupingIsOn() = runTest {
        val s = pds.session()
        s.matching(true)
        s.grouping(GroupChoice.ANYONE)
        val uri = s.activities.publish(draft())

        s.matching(false)
        s.sweep()
        assertNotNull(segmentTrack(uri.rkey!!))

        s.grouping(GroupChoice.OFF)
        s.sweep()
        assertNull(segmentTrack(uri.rkey!!), "both off")
        assertNull(segmentTrack(s.activities.publish(draft()).rkey!!), "none written while both are off")
    }

    @Test
    fun turningGroupingOffKeepsTracksWhileMatchingIsOn() = runTest {
        val s = pds.session()
        s.matching(true)
        s.grouping(GroupChoice.ANYONE)
        val uri = s.activities.publish(draft())

        s.grouping(GroupChoice.OFF)
        s.sweep()
        assertNotNull(segmentTrack(uri.rkey!!))
    }

    // Leaving the group

    @Test
    fun leavingKeepsThePointsAndBothMarksSurviveARetrim() = runTest {
        val s = pds.session()
        s.grouping(GroupChoice.ANYONE)
        val uri = s.activities.publish(draft())
        val store = segmentsOf(s.clients).store
        assertEquals(false, store.groupExcluded(uri))
        val blob = segmentTrack(uri.rkey!!)?.data

        store.setExcluded(uri, true)
        s.drain()
        store.setGroupExcluded(uri, true)
        assertEquals(true, store.groupExcluded(uri), "the queued leave counts")
        store.setGroupExcluded(uri, false)
        store.setGroupExcluded(uri, true)
        s.drain()
        assertEquals(true, segmentTrack(uri.rkey!!)?.groupExcluded)
        assertEquals(true, segmentTrack(uri.rkey!!)?.excludeFromLeaderboards, "the leaderboard exclusion is kept")
        assertEquals(blob, segmentTrack(uri.rkey!!)?.data, "the same points")

        s.privacy.save(PrivacySettings(listOf(home)))
        s.activities.repairRoutes()
        assertEquals(zonesRevision(), segmentTrack(uri.rkey!!)?.zonesRevision, "trimmed again")
        assertEquals(true, segmentTrack(uri.rkey!!)?.groupExcluded, "still left")
        assertEquals(true, segmentTrack(uri.rkey!!)?.excludeFromLeaderboards, "still excluded")

        store.setGroupExcluded(uri, false)
        s.drain()
        assertNull(segmentTrack(uri.rkey!!)?.groupExcluded, "rejoined")
        assertNull(segmentTrack(s.activities.publish(draft()).rkey!!)?.groupExcluded, "a new activity starts in")
    }

    @Test
    fun aLeaveThatLosesTheConnectionIsSentOnReconnect() = runTest {
        val s = pds.session()
        s.grouping(GroupChoice.ANYONE)
        val uri = s.activities.publish(draft())
        val store = segmentsOf(s.clients).store

        store.setGroupExcluded(uri, true)
        pds.offline = true
        runCatching { s.drain() }
        assertNull(segmentTrack(uri.rkey!!)?.groupExcluded)
        pds.offline = false
        s.drain()
        assertEquals(true, segmentTrack(uri.rkey!!)?.groupExcluded)
    }

    @Test
    fun aTrackWhosePointsAreStillQueuedCantLeave() = runTest {
        val s = pds.session()
        s.grouping(GroupChoice.ANYONE)
        s.privacy.readFresh()
        pds.offline = true
        val uri = s.activities.publish(draft())
        val store = segmentsOf(s.clients).store
        assertTrue(
            s.db.outbox().pending(s.clients.did.value, uri.rkey!!).any { it.collection == Nsid.FEED_SEGMENT_TRACK },
            "its segment track is queued",
        )

        assertNull(store.groupExcluded(uri), "unknown while the track's own upload is queued")
        assertTrue(runCatching { store.setGroupExcluded(uri, true) }.isFailure)
    }

    @Test
    fun anActivityWithoutASegmentTrackCantLeave() = runTest {
        val s = pds.session()
        val uri = s.activities.publish(draft())
        val store = segmentsOf(s.clients).store

        assertNull(store.groupExcluded(uri))
        assertTrue(runCatching { store.setGroupExcluded(uri, true) }.isFailure)
    }
}
