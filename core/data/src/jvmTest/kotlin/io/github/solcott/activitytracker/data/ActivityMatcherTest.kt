package io.github.solcott.activitytracker.data

import io.github.solcott.activitytracker.activityfile.ImportedActivity
import io.github.solcott.activitytracker.data.activity.ActivityMatch
import io.github.solcott.activitytracker.data.activity.ActivityMatcher
import io.github.solcott.activitytracker.data.activity.DuplicateOf
import io.github.solcott.activitytracker.data.activity.MatchSpan
import io.github.solcott.activitytracker.data.activity.OwnActivity
import io.github.solcott.activitytracker.lexicon.AtUri
import io.github.solcott.activitytracker.lexicon.Cid
import io.github.solcott.activitytracker.lexicon.Did
import io.github.solcott.activitytracker.lexicon.Nsid
import io.github.solcott.activitytracker.lexicon.app.ActivityRecord
import io.github.solcott.activitytracker.lexicon.app.ActivityType
import io.github.solcott.activitytracker.lexicon.app.Sport
import io.github.solcott.activitytracker.lexicon.app.SubSport
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlin.time.TimeSource
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

class ActivityMatcherTest {
    private val did = Did("did:plc:alice")

    /** A dive log's dive by default: its clock has no zone ([ImportedActivity.localStart]). */
    private fun imported(
        start: String,
        length: Duration = 45.minutes,
        sport: String = Sport.DIVE,
        zoneless: Boolean = sport == Sport.DIVE,
    ) =
        ImportedActivity(
            type = ActivityType(sport, if (sport == Sport.DIVE) SubSport.SINGLE_GAS else null),
            startedAt = Instant.parse(start),
            elapsed = length,
            movingSeconds = null,
            distanceMeters = null,
            elevationGainMeters = null,
            maxSpeedMps = null,
            avgHeartRate = null,
            maxHeartRate = null,
            avgTemperatureCelsius = null,
            device = null,
            samples = emptyList(),
            localStart = if (zoneless) Instant.parse(start).toLocalDateTime(TimeZone.UTC) else null,
        )

    private fun logged(rkey: String, start: String, length: Duration = 45.minutes, sport: String = Sport.DIVE) =
        OwnActivity(
            AtUri.of(did, Nsid.FEED_ACTIVITY, rkey),
            Cid("bafy$rkey"),
            ActivityRecord(sport, Instant.parse(start), length.inWholeSeconds, Instant.parse("2026-10-01T00:00:00Z")),
        )

    @Test
    fun aDiveOverlappingOneInTheLogIsADuplicate() {
        val garmin = logged("3kg", "2024-05-12T16:00:00Z", 46.minutes)
        // The Shearwater's clock is two minutes ahead of the Garmin's.
        val matches = ActivityMatcher.match(listOf(imported("2024-05-12T16:02:00Z")), listOf(garmin))
        assertEquals(listOf(ActivityMatch(DuplicateOf.Existing(garmin))), matches)
    }

    @Test
    fun lessThanHalfAnOverlapIsAnotherDive() {
        val morning = logged("3km", "2024-05-12T10:00:00Z", 60.minutes)
        assertEquals(listOf(null), ActivityMatcher.match(listOf(imported("2024-05-12T10:35:00Z", 60.minutes)), listOf(morning)))
        assertEquals(
            listOf(ActivityMatch(DuplicateOf.Existing(morning))),
            ActivityMatcher.match(listOf(imported("2024-05-12T10:25:00Z", 60.minutes)), listOf(morning)),
        )
    }

    @Test
    fun theSameLengthWholeHoursOffIsAZoneMixUp() {
        val logged = logged("3kz", "2024-05-12T16:00:00Z", 50.minutes)
        assertEquals(
            listOf(ActivityMatch(DuplicateOf.Existing(logged), hoursApart = -6)),
            ActivityMatcher.match(listOf(imported("2024-05-12T10:01:00Z", 52.minutes)), listOf(logged)),
        )
        assertEquals(
            listOf(null),
            ActivityMatcher.match(listOf(imported("2024-05-12T10:01:00Z", 30.minutes)), listOf(logged)),
            "another length",
        )
        assertEquals(
            listOf(null),
            ActivityMatcher.match(listOf(imported("2024-05-12T10:20:00Z", 50.minutes)), listOf(logged)),
            "not whole hours",
        )
    }

    @Test
    fun aDiveTwiceInTheFileMatchesTheFirst() {
        val dives = listOf(imported("2024-05-12T10:00:00Z"), imported("2024-05-12T14:00:00Z"), imported("2024-05-12T10:01:00Z"))
        assertEquals(listOf(null, null, ActivityMatch(DuplicateOf.InBatch(0))), ActivityMatcher.match(dives, emptyList()))
    }

    @Test
    fun theLogWinsOverTheFile() {
        val logged = logged("3kl", "2024-05-12T10:00:00Z")
        val dives = listOf(imported("2024-05-12T10:00:00Z"), imported("2024-05-12T10:01:00Z"))
        assertEquals(List(2) { ActivityMatch(DuplicateOf.Existing(logged)) }, ActivityMatcher.match(dives, listOf(logged)))
    }

    @Test
    fun anEdgeRideOverlappingTheWatchRideIsADuplicate() {
        val watch = logged("3kw", "2024-05-12T09:00:00Z", 120.minutes, sport = Sport.RIDE)
        val edge = imported("2024-05-12T08:58:00Z", 123.minutes, sport = Sport.RIDE)
        assertEquals(listOf(ActivityMatch(DuplicateOf.Existing(watch))), ActivityMatcher.match(listOf(edge), listOf(watch)))
    }

    @Test
    fun aRunOverlappingARideByUnderHalfIsAnotherActivity() {
        val ride = logged("3kb", "2024-05-12T11:00:00Z", 80.minutes, sport = Sport.RIDE)
        assertEquals(
            listOf(null),
            ActivityMatcher.match(listOf(imported("2024-05-12T12:00:00Z", 60.minutes, sport = Sport.RUN)), listOf(ride)),
        )
    }

    @Test
    fun sportsDontNeedToAgree() {
        val walk = logged("3kk", "2024-05-12T10:00:00Z", 60.minutes, sport = Sport.WALK)
        assertEquals(
            listOf(ActivityMatch(DuplicateOf.Existing(walk))),
            ActivityMatcher.match(listOf(imported("2024-05-12T10:05:00Z", 50.minutes, sport = Sport.RUN)), listOf(walk)),
        )
    }

    @Test
    fun aFitRideHoursOffIsAnotherRide() {
        val ride = logged("3kf", "2024-05-12T16:00:00Z", 90.minutes, sport = Sport.RIDE)
        assertEquals(
            listOf(null),
            ActivityMatcher.match(listOf(imported("2024-05-12T14:00:00Z", 90.minutes, sport = Sport.RIDE)), listOf(ride)),
        )
    }

    @Test
    fun aUddfDiveTwoHoursOffMatches() {
        val fit = logged("3ku", "2024-05-12T16:00:00Z", 50.minutes)
        assertEquals(
            listOf(ActivityMatch(DuplicateOf.Existing(fit), hoursApart = 2)),
            ActivityMatcher.match(listOf(imported("2024-05-12T18:01:00Z", 50.minutes)), listOf(fit)),
        )
    }

    @Test
    fun aWatchAndAnEdgeRidePickedTogetherMatch() {
        val watch = imported("2024-05-12T09:00:00Z", 120.minutes, sport = Sport.RIDE)
        val run = imported("2024-05-13T07:00:00Z", 40.minutes, sport = Sport.RUN)
        val edge = imported("2024-05-12T08:58:00Z", 123.minutes, sport = Sport.RIDE)
        assertEquals(
            listOf(null, null, ActivityMatch(DuplicateOf.InBatch(0))),
            ActivityMatcher.match(listOf(watch, run, edge), emptyList()),
        )
    }

    @Test
    fun aLongLoggedActivityStillMatchesAnImportLateInIt() {
        val expedition = logged("3ke", "2024-05-10T06:00:00Z", (3 * 24 * 60).minutes, sport = Sport.HIKE)
        val day = imported("2024-05-12T08:00:00Z", (30 * 60).minutes, sport = Sport.HIKE)
        assertEquals(listOf(ActivityMatch(DuplicateOf.Existing(expedition))), ActivityMatcher.match(listOf(day), listOf(expedition)))
    }

    @Test
    fun matchesThousandsQuickly() {
        val start = Instant.parse("2015-01-01T07:00:00Z")
        val days = 5_000
        val log = List(days) { logged("3k$it", (start + (it * 24 * 60).minutes).toString(), 60.minutes, sport = Sport.RUN) }
        // An export of the same activities, a minute later, plus a second copy of each from another device.
        val spans = List(days * 2) { i -> MatchSpan(start + ((i / 2) * 24 * 60).minutes + 1.minutes, 60.minutes, zoneless = false) }
        val started = TimeSource.Monotonic.markNow()
        val matches = ActivityMatcher.matchSpans(spans, log)
        val took = started.elapsedNow()
        assertEquals(spans.size, matches.count { it?.of is DuplicateOf.Existing })
        assertTrue(took < 2.seconds, "matched in $took")
    }
}
