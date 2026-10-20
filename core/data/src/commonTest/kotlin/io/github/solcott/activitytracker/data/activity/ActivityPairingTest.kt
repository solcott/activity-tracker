package io.github.solcott.activitytracker.data.activity

import io.github.solcott.activitytracker.lexicon.app.ActivityRecord
import io.github.solcott.activitytracker.lexicon.app.Sport
import io.github.solcott.activitytracker.lexicon.app.SubSport
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

class ActivityPairingTest {
    private fun record(sport: String, start: String, length: Duration, subSport: String? = null) =
        ActivityRecord(
            sport = sport,
            startedAt = Instant.parse(start),
            elapsedTime = length.inWholeSeconds,
            createdAt = Instant.parse("2026-10-01T00:00:00Z"),
            subSport = subSport,
        )

    @Test
    fun aSkiLunchIsAContinuation() {
        val morning = record(Sport.SKI, "2026-10-10T09:00:00Z", 2.hours + 45.minutes) // until 11:45
        val afternoon = record(Sport.SKI, "2026-10-10T13:00:00Z", 3.hours)
        assertEquals(Pairing.Continuation, ActivityMatcher.pairing(afternoon, morning))
        assertEquals(Pairing.Continuation, ActivityMatcher.pairing(morning, afternoon))
    }

    @Test
    fun aMorningAndEveningRunAreSeparate() {
        val morning = record(Sport.RUN, "2026-10-10T11:30:00Z", 1.hours) // until 12:30
        val evening = record(Sport.RUN, "2026-10-10T18:00:00Z", 1.hours)
        assertNull(ActivityMatcher.pairing(evening, morning))
    }

    @Test
    fun aRideAfterARunIsAnotherActivity() {
        val run = record(Sport.RUN, "2026-10-10T08:00:00Z", 40.minutes)
        val ride = record(Sport.RIDE, "2026-10-10T08:50:00Z", 1.hours)
        assertNull(ActivityMatcher.pairing(ride, run))
    }

    @Test
    fun repeatDivesAreSeparate() {
        val first = record(Sport.DIVE, "2026-10-10T09:00:00Z", 45.minutes, SubSport.SINGLE_GAS)
        val second = record(Sport.DIVE, "2026-10-10T10:45:00Z", 45.minutes, SubSport.SINGLE_GAS)
        assertNull(ActivityMatcher.pairing(second, first))
    }

    @Test
    fun aMountainRideContinuesARide() {
        val ride = record(Sport.RIDE, "2026-10-10T09:00:00Z", 1.hours)
        val mtb = record(Sport.RIDE, "2026-10-10T10:25:00Z", 30.minutes, SubSport.MOUNTAIN)
        assertEquals(Pairing.Continuation, ActivityMatcher.pairing(mtb, ride))
    }

    @Test
    fun anOverlapIsTheSameActivity() {
        val watch = record(Sport.RUN, "2026-10-10T07:01:00Z", 45.minutes)
        val phone = record(Sport.RUN, "2026-10-10T07:00:00Z", 45.minutes)
        assertEquals(Pairing.Overlap, ActivityMatcher.pairing(phone, watch))
    }
}
