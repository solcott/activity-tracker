package io.github.solcott.activitytracker.data.segments

import co.touchlab.kermit.Logger
import io.github.solcott.activitytracker.data.FakeAthletePds
import io.github.solcott.activitytracker.data.activity.NewPhoto
import io.github.solcott.activitytracker.data.activity.OutboxProcessor
import io.github.solcott.activitytracker.data.activity.OutboxWriter
import io.github.solcott.activitytracker.lexicon.AtJson
import io.github.solcott.activitytracker.lexicon.AtUri
import io.github.solcott.activitytracker.lexicon.Cid
import io.github.solcott.activitytracker.lexicon.Did
import io.github.solcott.activitytracker.lexicon.Handle
import io.github.solcott.activitytracker.lexicon.Nsid
import io.github.solcott.activitytracker.lexicon.app.ActivityMedia
import io.github.solcott.activitytracker.lexicon.app.AspectRatio
import io.github.solcott.activitytracker.lexicon.app.ProfileViewBasic
import io.github.solcott.activitytracker.lexicon.app.SegmentRecord
import io.github.solcott.activitytracker.lexicon.app.SegmentView
import io.github.solcott.activitytracker.lexicon.app.TrailCondition
import io.github.solcott.activitytracker.lexicon.app.TrailConditions
import io.github.solcott.activitytracker.lexicon.app.TrailReportRecord
import io.github.solcott.activitytracker.lexicon.app.Visibility
import io.github.solcott.activitytracker.lexicon.atproto.BlobRef
import io.github.solcott.activitytracker.lexicon.atproto.StrongRef
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant
import kotlinx.coroutines.test.runTest

/** Posting, sharing photos to, and deleting trail reports in the data layer (segments/trail-reports). */
class TrailReportsTest {
    private val pds = FakeAthletePds()
    private val segment = StrongRef(AtUri.of(Did("did:plc:importer"), Nsid.FEED_SEGMENT, "osm-1"), Cid("bafyseg"))
    private var now = Instant.parse("2026-11-20T12:00:00Z")
    private val clock =
        object : Clock {
            override fun now() = now
        }

    private val view =
        SegmentView(
            uri = segment.uri,
            cid = segment.cid,
            creator = ProfileViewBasic(Did("did:plc:importer"), Handle("importer.test")),
            record = SegmentRecord("Trail 214", "ride", "_p~iF~ps|U", "osm", "ODbL-1.0", Instant.parse("2026-01-01T00:00:00Z")),
            distanceMeters = 1200,
        )

    private suspend fun reports(): TrailReports {
        val s = pds.session()
        return TrailReports(s.clients, OutboxWriter(s.db, s.clients, clock), OutboxProcessor(s.db, s.clients, Logger), clock, s.feed)
    }

    private fun stored(): Map<String, TrailReportRecord> =
        pds.stored[pds.repo]
            .orEmpty()
            .filterKeys { it.startsWith("${Nsid.FEED_TRAIL_REPORT}/") }
            .mapValues { AtJson.decodeFromJsonElement(TrailReportRecord.serializer(), it.value) }

    private val photo = NewPhoto(byteArrayOf(1, 2, 3), "image/webp", 4, 3)

    @Test
    fun aReportGoesInTheRepoAndShowsAtOnce() = runTest {
        val trail = reports()
        val uri = trail.post(segment, listOf(TrailCondition.MUDDY, "foggy"), " Soup ", listOf(ReportPhoto.New(photo, "Mud")))
        assertEquals(listOf("put ${pds.repo} ${Nsid.FEED_TRAIL_REPORT}/${uri.rkey}"), pds.writes.filter { "trailReport" in it })
        val record = stored().values.single()
        assertEquals(listOf(TrailCondition.MUDDY), record.conditions)
        assertEquals("Soup", record.note)
        assertEquals("Mud", record.images.single().alt)
        val shown = trail.applyTo(view).conditions!!
        assertEquals(TrailCondition.MUDDY, shown.lead)
        assertEquals(1, shown.riders)
        assertEquals(1, shown.photoCount)
        assertEquals("Soup", shown.latestNote?.note)
    }

    @Test
    fun aCountedReportIsNotCountedTwice() = runTest {
        val trail = reports()
        trail.post(segment, listOf(TrailCondition.DRY), null, emptyList())
        val served =
            TrailConditions(lead = TrailCondition.DRY, leadRiders = 1, riders = 1, latestAt = now, tags = emptyList(), photoCount = 0)
        assertEquals(1, trail.applyTo(view.copy(conditions = served)).conditions?.riders)
    }

    @Test
    fun anUncountedReportStopsShowingAfterTheGrace() = runTest {
        val trail = reports()
        trail.post(segment, listOf(TrailCondition.DRY), null, emptyList())
        now += 6.minutes
        assertNull(trail.applyTo(view).conditions)
    }

    @Test
    fun closedLeadsAtOnce() = runTest {
        val trail = reports()
        val served =
            TrailConditions(
                lead = TrailCondition.MUDDY,
                leadRiders = 3,
                riders = 3,
                latestAt = now - 60.minutes,
                tags = emptyList(),
                photoCount = 0,
            )
        trail.post(segment, listOf(TrailCondition.CLOSED), null, emptyList())
        assertEquals(TrailCondition.CLOSED, trail.applyTo(view.copy(conditions = served)).conditions?.lead)
    }

    @Test
    fun aSharedPhotoIsUploadedAgainFromItsTier() = runTest {
        pds.blobs["bafkfollowers"] = byteArrayOf(9, 8, 7)
        val trail = reports()
        val image =
            ActivityMedia.Image(BlobRef(ref = BlobRef.Link("bafkfollowers"), mimeType = "image/webp", size = 3), "Berm", AspectRatio(4, 3))
        val shared = ReportPhoto.Shared(image, Visibility.FOLLOWERS)
        assertTrue(shared.fromNonPublic)
        trail.post(segment, emptyList(), null, listOf(shared))
        val uploaded = stored().values.single().images.single()
        assertNotEquals("bafkfollowers", uploaded.image.ref.link, "a new blob, so nothing links back to the ride")
        assertContentEquals(byteArrayOf(9, 8, 7), pds.blobs[uploaded.image.ref.link])
        assertEquals("Berm", uploaded.alt)
    }

    @Test
    fun nothingToReportIsRefused() = runTest {
        assertFailsWith<IllegalArgumentException> { reports().post(segment, listOf("foggy"), "Hi", emptyList()) }
    }

    @Test
    fun removingTheLastPhotoOfATaglessReportDeletesIt() = runTest {
        val trail = reports()
        val uri = trail.post(segment, emptyList(), null, listOf(ReportPhoto.New(photo)))
        val report = trail.applyTo(segment.uri, emptyList()).single()
        trail.removePhoto(report, trail.recordOf(report), 1)
        assertEquals(emptyMap(), stored())
        assertTrue(pds.writes.contains("delete ${pds.repo} ${Nsid.FEED_TRAIL_REPORT}/${uri.rkey}"))
        assertEquals(emptyList(), trail.applyTo(segment.uri, emptyList()))
    }

    @Test
    fun removingOnePhotoKeepsTheRest() = runTest {
        val trail = reports()
        trail.post(
            segment,
            listOf(TrailCondition.WET),
            null,
            listOf(ReportPhoto.New(photo), ReportPhoto.New(NewPhoto(byteArrayOf(4), "image/webp", 1, 1))),
        )
        val report = trail.applyTo(segment.uri, emptyList()).single()
        trail.removePhoto(report, trail.recordOf(report), 1)
        assertEquals(1, stored().values.single().images.size)
        assertEquals(1, trail.applyTo(segment.uri, listOf(report)).single().images.size)
    }

    @Test
    fun pendingReportsListNewestFirst() = runTest {
        val trail = reports()
        val older = trail.post(segment, listOf(TrailCondition.DRY), null, emptyList())
        now += 1.minutes
        val newer = trail.post(segment, listOf(TrailCondition.WET), null, emptyList())
        assertEquals(listOf(newer, older), trail.applyTo(segment.uri, emptyList()).map { it.uri })
    }

    @Test
    fun deletingAReportHidesIt() = runTest {
        val trail = reports()
        trail.post(segment, listOf(TrailCondition.DRY), null, emptyList())
        val report = trail.applyTo(segment.uri, emptyList()).single()
        trail.delete(report)
        assertEquals(emptyMap(), stored())
        assertEquals(emptyList(), trail.applyTo(segment.uri, listOf(report)))
    }
}
