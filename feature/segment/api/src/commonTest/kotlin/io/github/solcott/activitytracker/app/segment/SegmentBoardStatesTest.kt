package io.github.solcott.activitytracker.app.segment

import io.github.solcott.activitytracker.data.segments.BoardMeta
import io.github.solcott.activitytracker.lexicon.AtUri
import io.github.solcott.activitytracker.lexicon.Did
import io.github.solcott.activitytracker.lexicon.Handle
import io.github.solcott.activitytracker.lexicon.app.EffortView
import io.github.solcott.activitytracker.lexicon.app.LeaderboardEntry
import io.github.solcott.activitytracker.lexicon.app.MayorEntry
import io.github.solcott.activitytracker.lexicon.app.ProfileViewBasic
import io.github.solcott.activitytracker.lexicon.app.SegmentViewBasic
import io.github.solcott.dataresult.Origin
import io.github.solcott.dataresult.Outcome
import io.github.solcott.uistate.Page
import io.github.solcott.uistate.PagedState
import io.github.solcott.uistate.pagedStateOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The viewer's own row and their gap to first on a segment's boards (segments/segment-leaderboards, segments/segment-mayors). */
class SegmentBoardStatesTest {
    @Test
    fun theLeaderboardGapIsToTheBoardsFirstEntry() {
        val viewer = time(18, ALICE, 303)
        val state = SegmentLeaderboardState("Ridge", entries = loaded(listOf(time(1, BOB, 252)), viewer), viewerEntry = viewer)
        assertEquals(BoardGapTexts.behindFirst(51), state.gap)
        val longer = time(18, ALICE, 317)
        assertEquals(BoardGapTexts.behindFirst(65), state.copy(viewerEntry = longer).gap)
    }

    @Test
    fun aFilteredBoardsGapIsToItsOwnFirst() {
        val viewer = time(7, ALICE, 303)
        val ageGroup = SegmentLeaderboardState("Ridge", entries = loaded(listOf(time(1, CAROL, 270)), viewer), viewerEntry = viewer)
        assertEquals(BoardGapTexts.behindFirst(33), ageGroup.gap)
    }

    @Test
    fun noGapBeforeTheFirstEntryLoadsOrWhenTheViewerIsFirst() {
        val viewer = time(18, ALICE, 303)
        assertNull(SegmentLeaderboardState("Ridge", viewerEntry = viewer).gap)
        val first = time(1, ALICE, 252)
        assertNull(SegmentLeaderboardState("Ridge", entries = loaded(listOf(time(1, BOB, 252)), first), viewerEntry = first).gap)
        assertNull(SegmentLeaderboardState("Ridge", entries = loaded(listOf(time(1, BOB, 252)), null)).gap)
    }

    @Test
    fun theViewersRowIsTheirsWhereverItShows() {
        val viewer = time(2, ALICE, 260)
        val state = SegmentLeaderboardState("Ridge", entries = loaded(listOf(time(1, BOB, 252), viewer), viewer))
        assertTrue(state.isOwn(viewer))
        assertFalse(state.isOwn(state.entries.items.first()))
        assertNull(state.gap, "no pinned entry while the viewer is among the loaded rows")
    }

    @Test
    fun beingMayorTakesTheLeadersCountLessTheViewersPlusOne() {
        val viewer = count(4, ALICE, 6)
        val state = SegmentMayorsState("Ridge", entries = loaded(listOf(count(1, BOB, 13)), viewer), viewerEntry = viewer)
        assertEquals(BoardGapTexts.moreToBeMayor(8), state.gap)
        assertTrue(state.isOwn(viewer))
    }

    @Test
    fun aLevelCountReachedLaterNeedsOneMore() {
        val viewer = count(2, ALICE, 13)
        val state = SegmentMayorsState("Ridge", entries = loaded(listOf(count(1, BOB, 13)), viewer), viewerEntry = viewer)
        assertEquals(BoardGapTexts.moreToBeMayor(1), state.gap)
    }

    @Test
    fun noMayorGapForTheMayorOrBeforeTheFirstLoads() {
        val mayor = count(1, ALICE, 14)
        assertNull(SegmentMayorsState("Ridge", entries = loaded(listOf(count(1, BOB, 14)), mayor), viewerEntry = mayor).gap)
        assertNull(SegmentMayorsState("Ridge", viewerEntry = count(4, ALICE, 6)).gap)
    }

    private fun <E> loaded(items: List<E>, viewer: E?): PagedState<E, BoardMeta<E>> =
        pagedStateOf(Outcome.Data(Page(items, BoardMeta(viewer), next = null), Origin.Network)) { it }

    private fun athlete(did: Did) = ProfileViewBasic(did, Handle("${did.value.substringAfterLast(':')}.test"))

    private fun time(rank: Int, did: Did, elapsed: Long) =
        LeaderboardEntry(
            rank,
            athlete(did),
            EffortView(SEGMENT, AtUri("at://${did.value}/app.activitytracker.feed.activity/a$rank"), 0, elapsed, "2026-05-31"),
        )

    private fun count(rank: Int, did: Did, efforts: Int) = MayorEntry(rank, athlete(did), efforts, "2026-05-20")

    private companion object {
        val ALICE = Did("did:plc:alice")
        val BOB = Did("did:plc:bob")
        val CAROL = Did("did:plc:carol")
        val SEGMENT = SegmentViewBasic(AtUri("at://did:plc:bob/app.activitytracker.feed.segment/s"), "Ridge", "ride", 1_600)
    }
}
