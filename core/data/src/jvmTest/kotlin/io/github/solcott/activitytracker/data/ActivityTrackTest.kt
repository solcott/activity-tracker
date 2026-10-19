package io.github.solcott.activitytracker.data

import io.github.solcott.activitytracker.activityfile.DiveGas
import io.github.solcott.activitytracker.activityfile.DivePosition
import io.github.solcott.activitytracker.activityfile.ImportedActivity
import io.github.solcott.activitytracker.activityfile.ImportedDive
import io.github.solcott.activitytracker.data.activity.ActivityDraft
import io.github.solcott.activitytracker.data.activity.ActivityEdit
import io.github.solcott.activitytracker.data.activity.OwnActivity
import io.github.solcott.activitytracker.data.activity.VisibilityChoice
import io.github.solcott.activitytracker.lexicon.AtJson
import io.github.solcott.activitytracker.lexicon.AtUri
import io.github.solcott.activitytracker.lexicon.Cid
import io.github.solcott.activitytracker.lexicon.Handle
import io.github.solcott.activitytracker.lexicon.Nsid
import io.github.solcott.activitytracker.lexicon.app.ActivityRecord
import io.github.solcott.activitytracker.lexicon.app.ActivityType
import io.github.solcott.activitytracker.lexicon.app.ActivityView
import io.github.solcott.activitytracker.lexicon.app.ProfileViewBasic
import io.github.solcott.activitytracker.lexicon.app.RecordingView
import io.github.solcott.activitytracker.lexicon.app.Sport
import io.github.solcott.activitytracker.lexicon.app.Visibility
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * An activity's full-resolution track (`feed/activity-track`): a `feed.activityTrack` record in the athlete's `self` space whatever the
 * activity's visibility, written, merged and deleted with it.
 */
class ActivityTrackTest {
    private val pds = FakeAthletePds()
    private val did = pds.did
    private val gpx = "<gpx/>".encodeToByteArray()
    private val start = Instant.parse("2026-10-14T07:00:00Z")

    private fun draft(choice: VisibilityChoice = VisibilityChoice.PUBLIC, gpx: ByteArray? = this.gpx) =
        ActivityDraft(type = ActivityType(Sport.RUN), title = "Run", description = null, startedAt = start, elapsed = 30.minutes, gpx = gpx)
            .withVisibility(choice)

    private fun tracksOf(rkey: String) = pds.placesOf(Nsid.FEED_ACTIVITY_TRACK, rkey)

    private fun view(uri: AtUri, recordings: List<AtUri> = emptyList()) =
        ActivityView(
            uri = uri,
            cid = Cid("bafyrec"),
            author = ProfileViewBasic(did, Handle("alice.test")),
            record = ActivityRecord(sport = Sport.RUN, startedAt = start, elapsedTime = 1800, createdAt = Clock.System.now()),
            likeCount = 0,
            commentCount = 0,
            indexedAt = Clock.System.now(),
            recordings = recordings.map { RecordingView(it, start) }.ifEmpty { null },
        )

    @Test
    fun aPublicActivitysTrackGoesToTheSelfSpaceOnly() = runTest {
        val uri = pds.session().activities.publish(draft())
        val rkey = uri.rkey!!

        assertEquals(setOf(pds.repo), pds.placesOf(Nsid.FEED_ACTIVITY, rkey))
        assertEquals(setOf(Visibility.SELF), tracksOf(rkey), "never in the repo")
        assertFalse("track" in pds.stored.getValue(pds.repo).getValue("${Nsid.FEED_ACTIVITY}/$rkey"))
        val track = pds.stored.getValue(Visibility.SELF).getValue("${Nsid.FEED_ACTIVITY_TRACK}/$rkey")
        assertEquals(uri.value, track.getValue("activity").jsonPrimitive.content)
        assertTrue("bafyblob" in track.getValue("track").jsonObject.toString(), "the GPX is uploaded as its blob")
    }

    @Test
    fun aFollowersActivitysTrackGoesToTheSelfSpaceToo() = runTest {
        val uri = pds.session().activities.publish(draft(VisibilityChoice(Visibility.FOLLOWERS)))

        assertEquals(setOf(Visibility.FOLLOWERS), pds.placesOf(Nsid.FEED_ACTIVITY, uri.rkey!!))
        assertEquals(setOf(Visibility.SELF), tracksOf(uri.rkey!!))
    }

    @Test
    fun aTracklessDivesSurfaceFixesAreItsTrackInTheSelfSpaceAndNeverItsRecord() = runTest {
        val dive =
            ImportedDive(
                maxDepthMeters = 18.0,
                avgDepthMeters = null,
                minWaterTempCelsius = null,
                diveNumber = null,
                surfaceIntervalMinutes = null,
                gradientFactorLow = null,
                gradientFactorHigh = null,
                startCns = null,
                endCns = null,
                gases = listOf(DiveGas(21, 0)),
                closedCircuit = false,
                profile = emptyList(),
                entry = DivePosition(13.86622, -61.07885),
                exit = DivePosition(13.86701, -61.07912),
            )
        val imported =
            ImportedActivity(
                type = ActivityType(Sport.DIVE),
                startedAt = start,
                elapsed = 45.minutes,
                movingSeconds = null,
                distanceMeters = null,
                elevationGainMeters = null,
                maxSpeedMps = null,
                avgHeartRate = null,
                maxHeartRate = null,
                avgTemperatureCelsius = null,
                device = null,
                samples = emptyList(),
                dive = dive,
            )
        val uri = pds.session().activities.publish(ActivityDraft.fromImport(imported, imported.type, "Dive"))
        val rkey = uri.rkey!!

        assertEquals(setOf(Visibility.SELF), tracksOf(rkey))
        val record = pds.stored.getValue(pds.repo).getValue("${Nsid.FEED_ACTIVITY}/$rkey")
        assertFalse("polyline" in record, "two fixes make no polyline")
        assertFalse("13.866" in record.toString() || "-61.07" in record.toString(), "no position in the record: $record")
    }

    @Test
    fun anActivityWithoutPositionsHasNoTrack() = runTest {
        val uri = pds.session().activities.publish(draft(gpx = null))

        assertEquals(emptySet(), tracksOf(uri.rkey!!))
    }

    @Test
    fun aTrackPublishedOfflineIsSentWithItsActivity() = runTest {
        val s = pds.session()
        pds.offline = true
        val uri = s.activities.publish(draft())
        assertEquals(2, s.activities.pendingWrites.first(), "the activity and its track wait together")

        pds.offline = false
        assertEquals(0, s.activities.sync())

        assertEquals(setOf(pds.repo), pds.placesOf(Nsid.FEED_ACTIVITY, uri.rkey!!))
        assertEquals(setOf(Visibility.SELF), tracksOf(uri.rkey!!))
    }

    @Test
    fun aVisibilityChangeLeavesTheTrackWhereItIs() = runTest {
        val s = pds.session()
        val uri = s.activities.publish(draft())
        val rkey = uri.rkey!!
        val writesBefore = pds.writes.size

        s.manager.setVisibility(view(uri), VisibilityChoice(Visibility.SELF))

        assertEquals(setOf(Visibility.SELF), tracksOf(rkey))
        assertTrue(pds.writes.drop(writesBefore).none { Nsid.FEED_ACTIVITY_TRACK in it }, "the track isn't touched")
    }

    @Test
    fun deletingAnActivityDeletesItsTrack() = runTest {
        val s = pds.session()
        val uri = s.activities.publish(draft())

        s.manager.delete(view(uri), withRecordings = false)

        assertEquals(emptySet(), tracksOf(uri.rkey!!))
    }

    @Test
    fun deletingWithRecordingsDeletesEveryTrack() = runTest {
        val s = pds.session()
        val main = s.activities.publish(draft())
        val recording = s.activities.publish(draft())

        s.manager.delete(view(main, recordings = listOf(recording)), withRecordings = true)

        assertEquals(emptySet(), tracksOf(main.rkey!!))
        assertEquals(emptySet(), tracksOf(recording.rkey!!))
    }

    @Test
    fun deletingOnlyTheMainActivityKeepsItsRecordingsTracks() = runTest {
        val s = pds.session()
        val main = s.activities.publish(draft())
        val recording = s.activities.publish(draft())

        s.manager.delete(view(main, recordings = listOf(recording)), withRecordings = false)

        assertEquals(emptySet(), tracksOf(main.rkey!!))
        assertEquals(setOf(Visibility.SELF), tracksOf(recording.rkey!!))
    }

    @Test
    fun combiningAnImportKeepsEachMembersTrackAndSplittingLeavesThem() = runTest {
        val s = pds.session()
        val uri = s.activities.publish(draft())
        val writesBefore = pds.writes.size

        val combined =
            s.manager.combine(OwnActivity(uri, Cid("bafyrec"), ownRecord(uri)), draft(gpx = "<gpx>other</gpx>".encodeToByteArray()))
        val imported = ownRecord(combined).combined!!.members.single { it != uri }

        assertEquals(setOf(Visibility.SELF), tracksOf(imported.rkey!!), "the import keeps its own track")
        assertTrue(pds.writes.drop(writesBefore).none { "${Nsid.FEED_ACTIVITY_TRACK}/${uri.rkey}" in it }, "the logged one's is untouched")

        val beforeSplit = pds.writes.size
        s.manager.split(view(combined).copy(record = ownRecord(combined)))
        assertEquals(setOf(Visibility.SELF), tracksOf(uri.rkey!!))
        assertEquals(setOf(Visibility.SELF), tracksOf(imported.rkey!!))
        assertTrue(
            pds.writes.drop(beforeSplit).none {
                "${Nsid.FEED_ACTIVITY_TRACK}/${uri.rkey}" in it || "${Nsid.FEED_ACTIVITY_TRACK}/${imported.rkey}" in it
            }
        )
    }

    @Test
    fun editingAndUngroupingLeaveTheTrack() = runTest {
        val s = pds.session()
        val main = s.activities.publish(draft())
        val recording = s.activities.publish(draft())
        val writesBefore = pds.writes.size

        s.manager.edit(view(main), ActivityEdit(title = "Renamed", description = "", diveSite = null, media = emptyList()))
        s.manager.ungroup(recording, from = main)

        assertEquals(setOf(Visibility.SELF), tracksOf(main.rkey!!))
        assertEquals(setOf(Visibility.SELF), tracksOf(recording.rkey!!))
        assertTrue(pds.writes.drop(writesBefore).none { Nsid.FEED_ACTIVITY_TRACK in it })
    }

    private fun ownRecord(uri: AtUri): ActivityRecord =
        AtJson.decodeFromJsonElement(
            ActivityRecord.serializer(),
            pds.stored.getValue(pds.repo).getValue("${Nsid.FEED_ACTIVITY}/${uri.rkey}"),
        )
}
