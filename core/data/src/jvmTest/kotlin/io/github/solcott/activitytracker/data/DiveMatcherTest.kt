package io.github.solcott.activitytracker.data

import io.github.solcott.activitytracker.activityfile.ImportedActivity
import io.github.solcott.activitytracker.data.activity.DiveMatch
import io.github.solcott.activitytracker.data.activity.DiveMatcher
import io.github.solcott.activitytracker.data.activity.DuplicateOf
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
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

class DiveMatcherTest {
    private val did = Did("did:plc:alice")

    private fun dive(start: String, length: Duration = 45.minutes, sport: String = Sport.DIVE) =
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
        val matches = DiveMatcher.match(listOf(dive("2024-05-12T16:02:00Z")), listOf(garmin))
        assertEquals(listOf(DiveMatch(DuplicateOf.Existing(garmin))), matches)
    }

    @Test
    fun lessThanHalfAnOverlapIsAnotherDive() {
        val morning = logged("3km", "2024-05-12T10:00:00Z", 60.minutes)
        assertEquals(listOf(null), DiveMatcher.match(listOf(dive("2024-05-12T10:35:00Z", 60.minutes)), listOf(morning)))
        assertEquals(
            listOf(DiveMatch(DuplicateOf.Existing(morning))),
            DiveMatcher.match(listOf(dive("2024-05-12T10:25:00Z", 60.minutes)), listOf(morning)),
        )
    }

    @Test
    fun theSameLengthWholeHoursOffIsAZoneMixUp() {
        val logged = logged("3kz", "2024-05-12T16:00:00Z", 50.minutes)
        assertEquals(
            listOf(DiveMatch(DuplicateOf.Existing(logged), hoursApart = -6)),
            DiveMatcher.match(listOf(dive("2024-05-12T10:01:00Z", 52.minutes)), listOf(logged)),
        )
        assertEquals(listOf(null), DiveMatcher.match(listOf(dive("2024-05-12T10:01:00Z", 30.minutes)), listOf(logged)), "another length")
        assertEquals(listOf(null), DiveMatcher.match(listOf(dive("2024-05-12T10:20:00Z", 50.minutes)), listOf(logged)), "not whole hours")
    }

    @Test
    fun aDiveTwiceInTheFileMatchesTheFirst() {
        val dives = listOf(dive("2024-05-12T10:00:00Z"), dive("2024-05-12T14:00:00Z"), dive("2024-05-12T10:01:00Z"))
        assertEquals(listOf(null, null, DiveMatch(DuplicateOf.InFile(0))), DiveMatcher.match(dives, emptyList()))
    }

    @Test
    fun theLogWinsOverTheFile() {
        val logged = logged("3kl", "2024-05-12T10:00:00Z")
        val dives = listOf(dive("2024-05-12T10:00:00Z"), dive("2024-05-12T10:01:00Z"))
        assertEquals(List(2) { DiveMatch(DuplicateOf.Existing(logged)) }, DiveMatcher.match(dives, listOf(logged)))
    }

    @Test
    fun onlyDivesAreCompared() {
        val run = logged("3kr", "2024-05-12T10:00:00Z", sport = Sport.RUN)
        assertEquals(listOf(null), DiveMatcher.match(listOf(dive("2024-05-12T10:00:00Z")), listOf(run)))
        assertEquals(
            listOf(null),
            DiveMatcher.match(listOf(dive("2024-05-12T10:00:00Z", sport = Sport.RUN)), listOf(logged("3kd", "2024-05-12T10:00:00Z"))),
        )
    }
}
