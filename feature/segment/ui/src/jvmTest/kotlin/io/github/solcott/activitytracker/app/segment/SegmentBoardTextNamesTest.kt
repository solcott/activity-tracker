package io.github.solcott.activitytracker.app.segment

import io.github.solcott.activitytracker.app.fixtures.fakeLeaderboardEntry
import io.github.solcott.activitytracker.app.fixtures.fakeMayorEntry
import io.github.solcott.activitytracker.app.text.AppText
import io.github.solcott.activitytracker.app.text.DateText
import io.github.solcott.activitytracker.app.text.StatText
import io.github.solcott.activitytracker.composeApp.resources.Res
import io.github.solcott.activitytracker.composeApp.resources.allPluralStringResources
import io.github.solcott.activitytracker.composeApp.resources.allStringResources
import io.github.solcott.activitytracker.data.units.UnitSystem
import io.github.solcott.activitytracker.lexicon.app.BoardPeriod
import io.github.solcott.activitytracker.lexicon.app.SegmentBoard
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Every string the segment boards' and starred list's texts choose exists, and an entry reads as the boards show it
 * (dev/shared-display-text): `SharedTextNamesTest` can't see a feature's contracts.
 */
class SegmentBoardTextNamesTest {
    @Test
    fun everyNameTheSegmentBoardsChooseHasItsString() {
        val choices =
            BoardPeriod.all.map { SegmentBoardTexts.period(it, BoardPeriod.ALL_TIME) } +
                listOfNotNull(SegmentBoardTexts.type("ride", listOf("ride", SegmentBoard.E_BIKE))) +
                LeaderboardBracketTexts.ageGroup(null) +
                UnitSystem.entries.map { LeaderboardBracketTexts.weightClass(null, it) } +
                LeaderboardBracketTexts.gender(null)
        val texts =
            choices.flatMap { choice -> choice.options.map { it.label } } +
                SegmentBoardTexts.reached(fakeMayorEntry(rank = 1)) +
                SegmentBoardTexts.reached(fakeMayorEntry(rank = 2)) +
                SegmentBoardTexts.efforts(fakeMayorEntry()) +
                SegmentBoardTexts.effortDay(fakeLeaderboardEntry()) +
                UnitSystem.entries.map { StarredSegmentsTexts.summary(SegmentSamples.segment, it) } +
                StarredSegmentsTexts.summary(SegmentSamples.lift.content.data!!, UnitSystem.Metric)
        val missing = texts.flatMap(::names).distinct().filterNot { it in Res.allStringResources || it in Res.allPluralStringResources }
        assertTrue(missing.isEmpty(), "No string for $missing")
    }

    @Test
    fun anEntryReadsItsDayAndTime() {
        val entry = fakeLeaderboardEntry(rank = 4, elapsed = 230)
        assertEquals(DateText.isoDate(entry.effort.startedOn), SegmentBoardTexts.effortDay(entry))
        assertEquals(AppText.written("3:50"), SegmentBoardTexts.time(entry))
        val state = SegmentLeaderboardState("Ridge")
        assertTrue(state.accents(fakeLeaderboardEntry(rank = 3)))
        assertFalse(state.accents(entry))
        val mayors = SegmentMayorsState("Ridge")
        assertTrue(mayors.accents(fakeMayorEntry(rank = 1)))
        assertFalse(mayors.accents(fakeMayorEntry(rank = 2)))
        assertTrue(mayors.crowned(fakeMayorEntry(rank = 1)))
        assertFalse(mayors.crowned(fakeMayorEntry(rank = 2)))
    }

    @Test
    fun theMayorsLineSaysMayorAndTheRestSayReached() {
        val mayor = fakeMayorEntry(rank = 1).copy(reachedOn = "2026-11-03")
        assertEquals(AppText.named("mayors_reached_mayor", DateText.isoDate("2026-11-03")), SegmentBoardTexts.reached(mayor))
        val second = fakeMayorEntry(rank = 2).copy(reachedOn = "2026-11-09")
        assertEquals(AppText.named("mayors_reached", DateText.isoDate("2026-11-09")), SegmentBoardTexts.reached(second))
    }

    @Test
    fun aStarredSegmentReadsItsLengthAndType() {
        assertEquals(
            AppText.named(
                "starred_segments_summary",
                StatText.distance(SegmentSamples.segment.distanceMeters.toDouble(), UnitSystem.Metric),
                SegmentTexts.type(SegmentSamples.segment.record.sport, null),
            ),
            StarredSegmentsTexts.summary(SegmentSamples.segment, UnitSystem.Metric),
        )
    }

    private fun names(text: AppText): List<String> =
        when (text) {
            is AppText.Named -> listOf(text.name) + text.args.flatMap(::names)
            is AppText.Plural -> listOf(text.name) + text.args.flatMap(::names)
            else -> emptyList()
        }
}
