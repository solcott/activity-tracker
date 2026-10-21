package io.github.solcott.activitytracker.data

import co.touchlab.kermit.Logger
import io.github.solcott.activitytracker.data.activity.ActivityDraft
import io.github.solcott.activitytracker.data.activity.OutboxProcessor
import io.github.solcott.activitytracker.data.activity.PrivacySettings
import io.github.solcott.activitytracker.data.activity.VisibilityChoice
import io.github.solcott.activitytracker.data.segments.SegmentMatching
import io.github.solcott.activitytracker.lexicon.AtJson
import io.github.solcott.activitytracker.lexicon.AtUri
import io.github.solcott.activitytracker.lexicon.Cid
import io.github.solcott.activitytracker.lexicon.Handle
import io.github.solcott.activitytracker.lexicon.Nsid
import io.github.solcott.activitytracker.lexicon.app.ActivityRecord
import io.github.solcott.activitytracker.lexicon.app.ActivityType
import io.github.solcott.activitytracker.lexicon.app.ActivityView
import io.github.solcott.activitytracker.lexicon.app.PrivacyZone
import io.github.solcott.activitytracker.lexicon.app.PrivacyZonesRecord
import io.github.solcott.activitytracker.lexicon.app.ProfileViewBasic
import io.github.solcott.activitytracker.lexicon.app.SegmentPrefsRecord
import io.github.solcott.activitytracker.lexicon.app.SegmentTrackData
import io.github.solcott.activitytracker.lexicon.app.SegmentTrackRecord
import io.github.solcott.activitytracker.lexicon.app.Sport
import io.github.solcott.activitytracker.lexicon.app.Visibility
import io.github.solcott.activitytracker.location.Gpx
import io.github.solcott.activitytracker.location.LocationSample
import io.github.solcott.activitytracker.location.PrivacyCircle
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Segment tracks (`segments/segment-track`): the athlete's consent in `actor.segmentPrefs`, and while it's on a copy of each activity's
 * track trimmed by their privacy zones, in pieces and without wall-clock time, kept in `self` with the full track, re-trimmed when the
 * zones change and deleted when matching goes off.
 */
class SegmentTrackTest {
    private val pds = FakeAthletePds()
    private val start = Instant.parse("2026-10-14T07:00:00Z")

    /** About 3.3 km east along the equator from (0, 0), a point every 11 m and 4 s. */
    private val route =
        (0..300).map { LocationSample(0.0, it * 0.0001, 12.0, speed = null, accuracy = 5.0, time = start + (it * 4).seconds) }

    /** A 200 m zone around the route's start, and one around its middle, stored as is. */
    private val home = PrivacyZone(latitudeE7 = 0, longitudeE7 = 0, radiusMeters = 200, label = "Home")
    private val middle = PrivacyZone(latitudeE7 = 0, longitudeE7 = 150_000, radiusMeters = 200, label = "Work")

    private fun draft(choice: VisibilityChoice = VisibilityChoice.PUBLIC, points: List<LocationSample> = route) =
        ActivityDraft(
                type = ActivityType(Sport.RUN),
                title = "Run",
                description = null,
                startedAt = points.firstOrNull()?.time ?: start,
                elapsed = 20.minutes,
                distanceMeters = 3336.0,
                route = points,
                gpx = if (points.isEmpty()) null else Gpx.write("Run", Sport.RUN, points).encodeToByteArray(),
            )
            .withVisibility(choice)

    private fun segmentTrackKey(rkey: String) = "${Nsid.FEED_SEGMENT_TRACK}/$rkey"

    private fun segmentTrack(rkey: String): SegmentTrackRecord? =
        pds.stored[Visibility.SELF]?.get(segmentTrackKey(rkey))?.let { AtJson.decodeFromJsonElement(SegmentTrackRecord.serializer(), it) }

    private fun dataOf(rkey: String): SegmentTrackData {
        val cid = assertNotNull(assertNotNull(segmentTrack(rkey)).data).ref.link
        return AtJson.decodeFromString(SegmentTrackData.serializer(), pds.blobs.getValue(cid).decodeToString())
    }

    private fun segmentTrackPuts(rkey: String) = pds.writes.count { it.startsWith("put") && it.endsWith(segmentTrackKey(rkey)) }

    private fun zonesRevision(): String? =
        pds.stored[Visibility.SELF]?.get("${Nsid.ACTOR_PRIVACY_ZONES}/self")?.let {
            AtJson.decodeFromJsonElement(PrivacyZonesRecord.serializer(), it).revision
        }

    private fun view(uri: AtUri) =
        ActivityView(
            uri = uri,
            cid = Cid("bafyrec"),
            author = ProfileViewBasic(pds.did, Handle("alice.test")),
            record = ActivityRecord(sport = Sport.RUN, startedAt = start, elapsedTime = 1200, createdAt = Clock.System.now()),
            likeCount = 0,
            commentCount = 0,
            indexedAt = Clock.System.now(),
        )

    /** Waits in real time, since the sweep's requests run off the test scheduler, until [done]. */
    @Suppress("InjectDispatcher") // Real time, off the test scheduler, is the point.
    private suspend fun awaitReal(done: () -> Boolean) =
        withContext(Dispatchers.Default) { withTimeout(5.seconds) { while (!done()) delay(10) } }

    private suspend fun FakeAthletePds.Session.matching(on: Boolean) = segmentsOf(clients).prefs.save(on)

    private suspend fun FakeAthletePds.Session.sweep() = segmentsOf(clients).sweeper.sweep()

    // The setting

    @Test
    fun anAthleteWhoNeverChoseHasMatchingOffAndNoSegmentTracks() = runTest {
        val s = pds.session()
        val uri = s.activities.publish(draft())

        assertEquals(SegmentMatching.NEVER_ASKED, segmentsOf(s.clients).prefs.load())
        assertNull(segmentTrack(uri.rkey!!))
    }

    @Test
    fun theSettingIsStoredInSelfOnly() = runTest {
        val s = pds.session()
        s.matching(true)

        assertEquals(setOf(Visibility.SELF), pds.placesOf(Nsid.ACTOR_SEGMENT_PREFS, SegmentPrefsRecord.RKEY))
        val record = pds.stored.getValue(Visibility.SELF).getValue("${Nsid.ACTOR_SEGMENT_PREFS}/self")
        assertEquals("true", record.getValue("matching").jsonPrimitive.content)
        assertEquals(SegmentMatching.ON, segmentsOf(pds.session().clients).prefs.load(), "another device reads it")
    }

    @Test
    fun aSaveOfflineFailsAndKeepsTheEarlierSetting() = runTest {
        val s = pds.session()
        s.matching(false)
        pds.offline = true

        assertTrue(runCatching { s.matching(true) }.isFailure)
        assertEquals(SegmentMatching.OFF, segmentsOf(s.clients).prefs.current.value)
        pds.offline = false
        assertEquals(SegmentMatching.OFF, segmentsOf(pds.session().clients).prefs.load())
    }

    @Test
    fun aGrantWithoutTheSegmentCollectionsCantUseIt() = runTest {
        val scope =
            "atproto transition:generic space:${Nsid.SPACE_ATHLETE}?collection=${Nsid.FEED_ACTIVITY}" +
                "&collection=${Nsid.FEED_ACTIVITY_TRACK}"
        val s = pds.session(scope = scope)
        val prefs = segmentsOf(s.clients).prefs

        assertFalse(prefs.canUse)
        assertNull(prefs.load())
        assertTrue(runCatching { prefs.save(true) }.isFailure)
        assertEquals(emptySet(), pds.placesOf(Nsid.ACTOR_SEGMENT_PREFS, SegmentPrefsRecord.RKEY))
    }

    // Writing with each publish

    @Test
    fun aPublicActivitysSegmentTrackIsInSelfTrimmedInPiecesWithoutWallClockTime() = runTest {
        val s = pds.session()
        s.privacy.save(PrivacySettings(listOf(home, middle)))
        s.matching(true)
        val uri = s.activities.publish(draft())
        val rkey = uri.rkey!!

        assertEquals(setOf(Visibility.SELF), pds.placesOf(Nsid.FEED_SEGMENT_TRACK, rkey), "never in the repo")
        val record = assertNotNull(segmentTrack(rkey))
        assertEquals(uri, record.activity)
        assertEquals(zonesRevision(), record.zonesRevision)
        val data = dataOf(rkey)
        assertEquals(2, data.pieces.size, "split around the middle zone, never joined across it")
        // A cut point lies on a zone's boundary, give or take the centimeter that 1e-7 degrees rounds to.
        val circles = listOf(home, middle).map { PrivacyCircle(it.latitudeE7 / 1e7, it.longitudeE7 / 1e7, it.radiusMeters - 0.1) }
        for (piece in data.pieces) {
            for (i in piece.lat.indices) {
                assertTrue(circles.none { it.contains(piece.lat[i] / 1e7, piece.lng[i] / 1e7) }, "a point inside a zone")
            }
        }
        assertTrue(data.pieces.first().t.first() > 0, "starts where the run leaves home, timed from the run's start")
        assertEquals(1200, data.pieces.first().alt?.first(), "altitude in centimeters")
        val json = pds.blobs.getValue(record.data!!.ref.link).decodeToString()
        assertFalse("2026" in json || start.epochSeconds.toString() in json, "no wall-clock time: $json")
    }

    @Test
    fun aFollowersActivitysSegmentTrackIsInSelfToo() = runTest {
        val s = pds.session()
        s.matching(true)
        val uri = s.activities.publish(draft(VisibilityChoice(Visibility.FOLLOWERS)))

        assertEquals(setOf(Visibility.SELF), pds.placesOf(Nsid.FEED_SEGMENT_TRACK, uri.rkey!!))
    }

    @Test
    fun aSegmentTrackPublishedOfflineIsSentWithItsActivityTrimmedByTheZonesLastRead() = runTest {
        val s = pds.session()
        s.matching(true)
        s.privacy.refresh()
        pds.offline = true
        val uri = s.activities.publish(draft())
        assertEquals(3, s.activities.pendingWrites.first(), "the activity, its track and its segment track wait together")

        pds.offline = false
        s.activities.sync()
        assertNotNull(segmentTrack(uri.rkey!!))
        assertEquals(setOf(Visibility.SELF), pds.placesOf(Nsid.FEED_ACTIVITY_TRACK, uri.rkey!!))
    }

    @Test
    fun matchingOffWritesNoSegmentTrack() = runTest {
        val s = pds.session()
        s.matching(false)
        val uri = s.activities.publish(draft())

        assertNull(segmentTrack(uri.rkey!!))
    }

    @Test
    fun aManualEntryHasNoSegmentTrack() = runTest {
        val s = pds.session()
        s.matching(true)
        val uri = s.activities.publish(draft(points = emptyList()))

        assertNull(segmentTrack(uri.rkey!!))
    }

    @Test
    fun aFullyHiddenRouteHasNoSegmentTrack() = runTest {
        val s = pds.session()
        // The zone covers all but about 70 m at each end, which the hidden ends take.
        s.privacy.save(
            PrivacySettings(listOf(PrivacyZone(latitudeE7 = 0, longitudeE7 = 150_000, radiusMeters = 1600)), hideEndsMeters = 200)
        )
        s.matching(true)
        val uri = s.activities.publish(draft())

        assertNull(segmentTrack(uri.rkey!!))
    }

    @Test
    fun withZonesUnreadNoSegmentTrackIsWrittenUntilTheyAreRead() = runTest {
        val s = pds.session()
        s.matching(true)
        pds.failReadsOf = "${Nsid.ACTOR_PRIVACY_ZONES}/self"
        val uri = s.activities.publish(draft())
        assertNull(segmentTrack(uri.rkey!!), "never trimmed by zones it couldn't read")

        pds.failReadsOf = null
        s.activities.repairRoutes()
        assertNotNull(segmentTrack(uri.rkey!!))
    }

    @Test
    fun deletingAnActivityDeletesItsSegmentTrack() = runTest {
        val s = pds.session()
        s.matching(true)
        val uri = s.activities.publish(draft())
        assertNotNull(segmentTrack(uri.rkey!!))

        s.manager.delete(view(uri), withRecordings = false)
        assertNull(segmentTrack(uri.rkey!!))
    }

    @Test
    fun aVisibilityChangeLeavesTheSegmentTrack() = runTest {
        val s = pds.session()
        s.matching(true)
        val uri = s.activities.publish(draft())
        val before = segmentTrack(uri.rkey!!)

        s.manager.setVisibility(view(uri), VisibilityChoice(Visibility.SELF))
        assertEquals(before, segmentTrack(uri.rkey!!))
        assertEquals(1, segmentTrackPuts(uri.rkey!!))
    }

    @Test
    fun aCombinedActivitysSegmentTrackBreaksBetweenItsMembers() = runTest {
        val s = pds.session()
        s.matching(true)
        val later = route.map { it.copy(latitude = 0.01, time = it.time + 1.minutes * 30) }
        val combined = s.manager.combine(listOf(draft(), draft(points = later)))

        val data = dataOf(combined.rkey!!)
        assertEquals(2, data.pieces.size, "no line from the end of one member to the start of the next")
        assertEquals(0L, data.pieces.first().t.first())
    }

    // The sweep

    @Test
    fun turningMatchingOnCoversEarlierActivities() = runTest {
        val s = pds.session()
        val uris = List(3) { s.activities.publish(draft()) }
        uris.forEach { assertNull(segmentTrack(it.rkey!!)) }

        s.matching(true)
        s.sweep()
        uris.forEach { assertNotNull(segmentTrack(it.rkey!!), "$it") }
    }

    @Test
    fun anInterruptedBackfillFinishesInALaterSession() = runTest {
        val s = pds.session()
        val uris = List(3) { s.activities.publish(draft()) }
        s.matching(true)
        pds.failPutsOf = Nsid.FEED_SEGMENT_TRACK
        s.sweep()
        uris.forEach { assertNull(segmentTrack(it.rkey!!)) }
        pds.failPutsOf = null

        pds.session().activities.repairRoutes()
        uris.forEach { assertNotNull(segmentTrack(it.rkey!!), "$it") }
    }

    @Test
    fun aZoneChangeRewritesSegmentTracksByTheNewZonesOnce() = runTest {
        val s = pds.session()
        s.matching(true)
        val uri = s.activities.publish(draft())
        assertEquals(0L, dataOf(uri.rkey!!).pieces.first().t.first(), "nothing hidden yet")

        s.privacy.save(PrivacySettings(listOf(home)))
        s.activities.repairRoutes()
        assertEquals(zonesRevision(), segmentTrack(uri.rkey!!)?.zonesRevision)
        assertTrue(dataOf(uri.rkey!!).pieces.first().t.first() > 0, "trimmed by the new zone")
        assertEquals(2, segmentTrackPuts(uri.rkey!!))

        pds.session().activities.repairRoutes()
        assertEquals(2, segmentTrackPuts(uri.rkey!!), "not rewritten again until the zones change")
    }

    @Test
    fun turningMatchingOffDeletesEverySegmentTrack() = runTest {
        val s = pds.session()
        s.matching(true)
        val uris = List(2) { s.activities.publish(draft()) }

        s.matching(false)
        s.sweep()
        uris.forEach { assertNull(segmentTrack(it.rkey!!)) }
        assertEquals(SegmentMatching.OFF, segmentsOf(s.clients).prefs.current.value, "the setting was saved first")

        val later = s.activities.publish(draft())
        assertNull(segmentTrack(later.rkey!!), "none written while off")
    }

    @Test
    fun anInterruptedDeleteFinishesInALaterSession() = runTest {
        val s = pds.session()
        s.matching(true)
        val uri = s.activities.publish(draft())
        s.matching(false)
        pds.failSpaceDeletes = true
        s.sweep()
        assertNotNull(segmentTrack(uri.rkey!!))
        pds.failSpaceDeletes = false

        pds.session().activities.repairRoutes()
        assertNull(segmentTrack(uri.rkey!!))
    }

    @Test
    fun savingTheSettingStartsTheSweepForTheSession() = runTest {
        val s = pds.session()
        val uri = s.activities.publish(draft())
        val sweeps = launch { s.activities.repairRoutesOnSave() }
        yield()

        s.matching(true)
        awaitReal { segmentTrack(uri.rkey!!) != null }

        s.matching(false)
        awaitReal { segmentTrack(uri.rkey!!) == null }
        sweeps.cancel()
    }

    @Test
    fun theFullTrackIsUntouchedBySegmentTracks() = runTest {
        val s = pds.session()
        s.privacy.save(PrivacySettings(listOf(home)))
        s.matching(true)
        val uri = s.activities.publish(draft())

        val full = assertNotNull(s.tracks.read(uri))
        assertEquals(route.size, full.size, "the full track keeps every point, inside the zone included")
        assertTrue(pds.stored.getValue(Visibility.SELF).getValue(segmentTrackKey(uri.rkey!!)).jsonObject.containsKey("data"))
    }

    // Leaderboard choices (segments/segment-leaderboards)

    private suspend fun FakeAthletePds.Session.drain() = OutboxProcessor(db, clients, Logger).drain()

    private fun storedPrefs(): SegmentPrefsRecord =
        AtJson.decodeFromJsonElement(
            SegmentPrefsRecord.serializer(),
            pds.stored.getValue(Visibility.SELF).getValue("${Nsid.ACTOR_SEGMENT_PREFS}/self"),
        )

    @Test
    fun eachSettingsSaveKeepsTheOther() = runTest {
        val s = pds.session()
        val prefs = segmentsOf(s.clients).prefs
        s.matching(true)
        prefs.saveLeaderboards(hidden = true)
        assertEquals(true, storedPrefs().hideFromLeaderboards)
        assertTrue(storedPrefs().matching)

        val other = segmentsOf(pds.session().clients).prefs
        other.save(false)
        assertEquals(true, storedPrefs().hideFromLeaderboards, "another device's matching save keeps it")
        assertEquals(true, other.hiddenFromLeaderboards.value)

        prefs.saveLeaderboards(hidden = false)
        assertNull(storedPrefs().hideFromLeaderboards)
        assertFalse(storedPrefs().matching, "the stored matching, not this session's")
    }

    @Test
    fun aFailedLeaderboardSaveKeepsTheEarlierChoice() = runTest {
        val s = pds.session()
        val prefs = segmentsOf(s.clients).prefs
        s.matching(true)
        pds.offline = true

        assertTrue(runCatching { prefs.saveLeaderboards(hidden = true) }.isFailure)
        assertEquals(false, prefs.hiddenFromLeaderboards.value)
        pds.offline = false
        assertNull(storedPrefs().hideFromLeaderboards)
    }

    @Test
    fun anExclusionKeepsThePointsAndSurvivesARetrim() = runTest {
        val s = pds.session()
        s.matching(true)
        val uri = s.activities.publish(draft())
        val store = segmentsOf(s.clients).store
        assertEquals(false, store.excluded(uri))
        val blob = segmentTrack(uri.rkey!!)?.data
        val uploads = pds.uploads

        store.setExcluded(uri, true)
        assertNull(store.excluded(uri), "unknown while the write is queued")
        assertTrue(runCatching { store.setExcluded(uri, false) }.isFailure)
        s.drain()
        assertEquals(true, segmentTrack(uri.rkey!!)?.excludeFromLeaderboards)
        assertEquals(blob, segmentTrack(uri.rkey!!)?.data, "the same points")
        assertEquals(uploads, pds.uploads, "nothing uploaded again")
        assertEquals(true, store.excluded(uri))

        s.privacy.save(PrivacySettings(listOf(home)))
        s.activities.repairRoutes()
        assertEquals(zonesRevision(), segmentTrack(uri.rkey!!)?.zonesRevision, "trimmed again")
        assertEquals(true, segmentTrack(uri.rkey!!)?.excludeFromLeaderboards, "still excluded")

        val next = s.activities.publish(draft())
        assertNull(segmentTrack(next.rkey!!)?.excludeFromLeaderboards, "a new activity starts unexcluded")
    }

    @Test
    fun anActivityWithoutASegmentTrackCantBeExcluded() = runTest {
        val s = pds.session()
        val uri = s.activities.publish(draft())
        val store = segmentsOf(s.clients).store

        assertNull(store.excluded(uri))
        assertTrue(runCatching { store.setExcluded(uri, true) }.isFailure)
    }
}
