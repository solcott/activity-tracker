package io.github.solcott.activitytracker.data

import co.touchlab.kermit.Logger
import io.github.solcott.activitytracker.activityfile.readGpxTrack
import io.github.solcott.activitytracker.data.activity.OutboxProcessor
import io.github.solcott.activitytracker.data.activity.PendingWritesException
import io.github.solcott.activitytracker.data.activity.VisibilityChoice
import io.github.solcott.activitytracker.data.activity.trim.KeptSpan
import io.github.solcott.activitytracker.data.activity.trim.TrimFixtures
import io.github.solcott.activitytracker.data.activity.trim.TrimFixtures.RIDE_END
import io.github.solcott.activitytracker.data.activity.trim.TrimFixtures.STANDING_START
import io.github.solcott.activitytracker.lexicon.AtJson
import io.github.solcott.activitytracker.lexicon.AtUri
import io.github.solcott.activitytracker.lexicon.Cid
import io.github.solcott.activitytracker.lexicon.Handle
import io.github.solcott.activitytracker.lexicon.Nsid
import io.github.solcott.activitytracker.lexicon.app.ActivityDetailsRecord
import io.github.solcott.activitytracker.lexicon.app.ActivityRecord
import io.github.solcott.activitytracker.lexicon.app.ActivityStreamData
import io.github.solcott.activitytracker.lexicon.app.ActivityView
import io.github.solcott.activitytracker.lexicon.app.DetailCategory
import io.github.solcott.activitytracker.lexicon.app.ProfileViewBasic
import io.github.solcott.activitytracker.lexicon.app.SegmentTrackData
import io.github.solcott.activitytracker.lexicon.app.SegmentTrackRecord
import io.github.solcott.activitytracker.lexicon.app.Sport
import io.github.solcott.activitytracker.lexicon.app.Visibility
import io.github.solcott.activitytracker.location.Gpx
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject

/** Trimming a saved activity in place (feed/activity-trim), against the fake PDS. */
class ActivityTrimTest {
    private val pds = FakeAthletePds()
    private val span = KeptSpan(STANDING_START, RIDE_END)

    /** A Followers ride whose heart rate is Only me. */
    private val choice = VisibilityChoice(Visibility.FOLLOWERS, mapOf(DetailCategory.HEART_RATE to Visibility.SELF))

    private fun draft() =
        TrimFixtures.draft().copy(gpx = Gpx.write("Sunday loop", Sport.RIDE, TrimFixtures.route).encodeToByteArray()).withVisibility(choice)

    private fun stored(place: String, collection: String, rkey: String): JsonObject? = pds.stored[place]?.get("$collection/$rkey")

    private fun record(uri: AtUri): ActivityRecord =
        AtJson.decodeFromJsonElement(
            ActivityRecord.serializer(),
            assertNotNull(stored(Visibility.FOLLOWERS, Nsid.FEED_ACTIVITY, uri.rkey!!)),
        )

    private fun blobOf(record: JsonObject, field: String = "data"): String {
        val cid = record[field].toString().substringAfter("\"\$link\":\"").substringBefore('"')
        return pds.blobs.getValue(cid).decodeToString()
    }

    private fun streams(place: String, uri: AtUri): ActivityStreamData? =
        stored(place, Nsid.FEED_ACTIVITY_STREAMS, uri.rkey!!)?.let { AtJson.decodeFromString(ActivityStreamData.serializer(), blobOf(it)) }

    private fun view(uri: AtUri, record: ActivityRecord = record(uri)) =
        ActivityView(
            uri = uri,
            cid = Cid("bafyrec"),
            author = ProfileViewBasic(pds.did, Handle("alice.test")),
            record = record,
            likeCount = 0,
            commentCount = 0,
            indexedAt = Clock.System.now(),
            visibility = Visibility.FOLLOWERS,
        )

    @Test
    fun aTrimRewritesEveryPartInPlace() = runTest {
        val s = pds.session()
        segmentsOf(s.clients).prefs.save(true)
        val uri = s.activities.publish(draft())
        segmentsOf(s.clients).store.setExcluded(uri, true)
        OutboxProcessor(s.db, s.clients, Logger).drain()
        segmentsOf(s.clients).store.setGroupExcluded(uri, true)
        val rkey = uri.rkey!!

        s.manager.trim(s.manager.loadForTrim(view(uri)), span)

        val trimmed = record(uri)
        assertEquals(TrimFixtures.start + STANDING_START.seconds, trimmed.startedAt)
        assertEquals((RIDE_END - STANDING_START).toLong(), trimmed.elapsedTime)
        assertEquals(TrimFixtures.DEVICE_DISTANCE - 3264, trimmed.distance)
        assertNull(trimmed.avgHeartRate, "heart rate stays out of the Followers record")

        val details =
            AtJson.decodeFromJsonElement(
                ActivityDetailsRecord.serializer(),
                assertNotNull(stored(Visibility.SELF, Nsid.FEED_ACTIVITY_DETAILS, rkey)),
            )
        assertNotNull(details.avgHeartRate, "the trimmed heart rate is still Only me")

        val onlyMe = assertNotNull(streams(Visibility.SELF, uri))
        assertNotNull(onlyMe.heartRate)
        assertEquals(0, onlyMe.time.first())
        assertEquals(RIDE_END - STANDING_START, onlyMe.time.last())
        assertNull(streams(Visibility.FOLLOWERS, uri)?.heartRate, "never wider than Only me")

        val track =
            readGpxTrack(
                blobOf(assertNotNull(stored(Visibility.SELF, Nsid.FEED_ACTIVITY_TRACK, rkey)), field = "track").encodeToByteArray(),
            )
        assertEquals(trimmed.startedAt, track.first().time)

        val segment =
            AtJson.decodeFromJsonElement(
                SegmentTrackRecord.serializer(),
                assertNotNull(stored(Visibility.SELF, Nsid.FEED_SEGMENT_TRACK, rkey)),
            )
        assertEquals(true, segment.excludeFromLeaderboards, "the leaderboard exclusion is kept")
        assertEquals(true, segment.groupExcluded, "and so is leaving the group")
        val pieces =
            AtJson.decodeFromString(SegmentTrackData.serializer(), blobOf(stored(Visibility.SELF, Nsid.FEED_SEGMENT_TRACK, rkey)!!)).pieces
        assertTrue(pieces.isNotEmpty())
        assertTrue(s.db.outbox().pending(s.clients.did.value, rkey).isEmpty())
    }

    @Test
    fun aTrimQueuedOfflineIsSentOnReconnect() = runTest {
        val s = pds.session()
        val uri = s.activities.publish(draft())
        val source = s.manager.loadForTrim(view(uri))
        pds.offline = true

        s.manager.trim(source, span)
        assertTrue(s.db.outbox().pending(s.clients.did.value, uri.rkey!!).isNotEmpty(), "queued while offline")

        pds.offline = false
        OutboxProcessor(s.db, s.clients, Logger).drain()
        assertEquals(TrimFixtures.start + STANDING_START.seconds, record(uri).startedAt)
        assertTrue(s.db.outbox().pending(s.clients.did.value, uri.rkey!!).isEmpty())
    }

    @Test
    fun anActivityStillBeingSentCantBeLoaded() = runTest {
        val s = pds.session()
        pds.offline = true
        val uri = s.activities.publish(draft())
        val view = view(uri, TrimFixtures.record)

        assertFailsWith<PendingWritesException> { s.manager.loadForTrim(view) }
    }
}
