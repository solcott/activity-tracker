package io.github.solcott.activitytracker.app.segment

import io.github.solcott.activitytracker.app.text.AppText
import io.github.solcott.activitytracker.lexicon.AtUri
import io.github.solcott.activitytracker.lexicon.Cid
import io.github.solcott.activitytracker.lexicon.Did
import io.github.solcott.activitytracker.lexicon.Handle
import io.github.solcott.activitytracker.lexicon.Nsid
import io.github.solcott.activitytracker.lexicon.app.ActivityMediaView
import io.github.solcott.activitytracker.lexicon.app.GetPathConditions
import io.github.solcott.activitytracker.lexicon.app.ProfileViewBasic
import io.github.solcott.activitytracker.lexicon.app.SegmentRecord
import io.github.solcott.activitytracker.lexicon.app.SegmentView
import io.github.solcott.activitytracker.lexicon.app.SegmentViewerState
import io.github.solcott.activitytracker.lexicon.app.TrailCondition
import io.github.solcott.activitytracker.lexicon.app.TrailConditionTag
import io.github.solcott.activitytracker.lexicon.app.TrailConditions
import io.github.solcott.activitytracker.lexicon.app.TrailPhotoView
import io.github.solcott.activitytracker.lexicon.app.TrailReportView
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant
import kotlinx.collections.immutable.persistentListOf
import kotlinx.datetime.TimeZone

/** What the Conditions card and the Trail page's roll-up say (segments/trail-reports), as the spec's examples read. */
class TrailReportTextsTest {
    private val now = Instant.parse("2026-11-23T18:00:00Z")
    private val zone = TimeZone.UTC
    private val maya = ProfileViewBasic(Did("did:plc:maya"), Handle("maya.test"), displayName = "Maya Chen")
    private val segmentUri = AtUri.of(Did("did:plc:importer"), Nsid.FEED_SEGMENT, "osm-1")

    private fun segment(conditions: TrailConditions?, viewer: SegmentViewerState? = null) =
        SegmentView(
            uri = segmentUri,
            cid = Cid("bafyseg"),
            creator = ProfileViewBasic(Did("did:plc:importer"), Handle("importer.test")),
            record = SegmentRecord("Wiggly Woods", "ride", "_p~iF~ps|U", "osm", "ODbL-1.0", Instant.parse("2026-01-01T00:00:00Z")),
            distanceMeters = 2400,
            conditions = conditions,
            viewer = viewer,
        )

    private fun photo(n: Int) =
        TrailPhotoView(
            image = ActivityMediaView.Image("https://appview.test/img/media/did:plc:maya/bafk$n", alt = "Photo $n"),
            report = AtUri.of(maya.did, Nsid.FEED_TRAIL_REPORT, "3k$n"),
            cid = Cid("bafy$n"),
            position = 1,
            segment = segmentUri,
            author = maya,
            indexedAt = now - 2.days,
        )

    private val note =
        TrailReportView(
            uri = AtUri.of(maya.did, Nsid.FEED_TRAIL_REPORT, "3kn"),
            cid = Cid("bafyn"),
            segment = segmentUri,
            author = maya,
            conditions = listOf(TrailCondition.MUDDY, TrailCondition.DOWNED_TREE),
            note = "Big tree down just past the second bridge.",
            images = emptyList(),
            indexedAt = now - 2.days,
            current = true,
        )

    private fun plural(name: String, count: Int, vararg args: AppText) = AppText.Plural(name, count, persistentListOf(*args))

    @Test
    fun muddySegment() {
        val conditions =
            TrailConditions(
                lead = TrailCondition.MUDDY,
                leadRiders = 3,
                riders = 3,
                latestAt = now - 2.days,
                tags = listOf(TrailConditionTag(TrailCondition.DOWNED_TREE, 2)),
                latestNote = note,
                photoCount = 16,
                photos = (1..4).map(::photo),
            )
        val card = TrailReportTexts.card(segment(conditions), now, zone, canWrite = true)!!
        assertEquals(AppText.named("trail_condition_muddy"), card.lead)
        assertFalse(card.closed)
        assertEquals(
            plural("trail_conditions_riders", 3, AppText.written("3"), plural("trail_age_days", 2, AppText.written("2"))),
            card.leadLine,
        )
        assertEquals(
            listOf(AppText.named("trail_condition_count", AppText.named("trail_condition_downedTree"), AppText.written("2"))),
            card.tags,
        )
        assertEquals("Maya Chen", card.note?.author)
        assertEquals(4, card.photos.size)
        assertEquals(AppText.named("trail_photos_more", AppText.written("12")), card.morePhotos)
        assertEquals(AppText.named("trail_photos_see_all", AppText.written("16")), card.seeAll)
        assertFalse(card.offersReport, "the viewer may not report")
        assertNull(card.empty)
    }

    @Test
    fun closed() {
        val conditions =
            TrailConditions(
                lead = TrailCondition.CLOSED,
                leadRiders = 1,
                riders = 2,
                latestAt = now - 1.days,
                tags = emptyList(),
                photoCount = 0,
            )
        val card = TrailReportTexts.card(segment(conditions), now, zone, canWrite = true)!!
        assertTrue(card.closed)
        assertEquals(AppText.named("trail_condition_closed"), card.lead)
    }

    @Test
    fun onlyOlderReports() {
        val conditions =
            TrailConditions(
                riders = 0,
                tags = emptyList(),
                lastReportAt = now - 35.days,
                lastReportTags = listOf(TrailCondition.DRY),
                photoCount = 0,
            )
        val card = TrailReportTexts.card(segment(conditions), now, zone, canWrite = true)!!
        assertEquals(AppText.named("trail_conditions_none"), card.empty)
        assertEquals(
            AppText.named(
                "trail_conditions_last",
                AppText.named("trail_condition_dry"),
                plural("trail_age_weeks", 5, AppText.written("5")),
            ),
            card.emptyDetail,
        )
    }

    @Test
    fun rodeItNothingReported() {
        val viewer = SegmentViewerState(canReportConditions = true, lastEffortAt = now - 3.days)
        val card = TrailReportTexts.card(segment(null, viewer), now, zone, canWrite = true)!!
        assertTrue(card.offersReport)
        assertEquals(AppText.named("trail_conditions_ask", plural("trail_age_days", 3, AppText.written("3"))), card.ask)
    }

    @Test
    fun noReportsNoRide() {
        assertNull(TrailReportTexts.card(segment(null, SegmentViewerState()), now, zone, canWrite = true))
    }

    @Test
    fun aSessionThatCantWriteIsNotAsked() {
        val viewer = SegmentViewerState(canReportConditions = true, lastEffortAt = now - 3.days)
        assertNull(TrailReportTexts.card(segment(null, viewer), now, zone, canWrite = false))
    }

    @Test
    fun ages() {
        assertEquals(AppText.named("trail_age_today"), TrailReportTexts.age(now - 1.days / 4, now, zone))
        assertEquals(AppText.named("trail_age_yesterday"), TrailReportTexts.age(now - 1.days, now, zone))
        assertEquals(plural("trail_age_months", 3, AppText.written("3")), TrailReportTexts.age(now - 95.days, now, zone))
    }

    @Test
    fun kingdomLoop() {
        fun row(name: String, lead: String, at: Instant) =
            GetPathConditions.SegmentConditions(
                AtUri.of(Did("did:plc:importer"), Nsid.FEED_SEGMENT, name),
                name,
                TrailConditions(lead = lead, leadRiders = 1, riders = 1, latestAt = at, tags = emptyList(), photoCount = 0),
            )
        val card =
            PathConditionsTexts.card(
                GetPathConditions.Output(
                    segments =
                        listOf(
                            row("Ridge Run", TrailCondition.CLOSED, now - 1.days),
                            row("Wiggly Woods", TrailCondition.MUDDY, now - 2.days),
                        ),
                    segmentCount = 8,
                    photoCount = 0,
                ),
                now,
                zone,
            )!!
        assertEquals(plural("path_conditions_summary", 8, AppText.written("2"), AppText.written("8")), card.summary)
        val ridge = card.segments.first()
        assertTrue(ridge.closed)
        assertEquals(
            AppText.named("path_segment_condition", AppText.named("trail_condition_closed"), AppText.named("trail_age_yesterday")),
            card.closedLine(ridge.uri),
        )
        assertNull(card.closedLine(card.segments.last().uri))
    }
}
