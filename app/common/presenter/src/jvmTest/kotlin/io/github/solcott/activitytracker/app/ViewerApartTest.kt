package io.github.solcott.activitytracker.app

import io.github.solcott.dataresult.Origin
import io.github.solcott.dataresult.Outcome
import io.github.solcott.uistate.Page
import io.github.solcott.uistate.PagedState
import io.github.solcott.uistate.pagedStateOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** A board's own entry apart from its list: shown only once answered, and only when no loaded entry is the viewer's. */
class ViewerApartTest {
    private data class Entry(val athlete: String, val rank: Int)

    private data class Meta(val viewer: Entry?)

    private fun board(items: List<Entry>, viewer: Entry?): PagedState<Entry, Meta> =
        pagedStateOf(Outcome.Data(Page(items, Meta(viewer), next = null), Origin.Network), idOf = { it.athlete })

    private fun PagedState<Entry, Meta>.apart(): Entry? = viewerApart(viewerOf = { it.viewer }, athleteOf = { it.athlete })

    @Test
    fun ownEntryOffThePageIsApart() {
        val own = Entry("alice", 58)
        assertEquals(own, board(listOf(Entry("bob", 1), Entry("carol", 2)), own).apart())
    }

    @Test
    fun ownEntryOnThePageIsNotRepeated() {
        assertNull(board(listOf(Entry("bob", 1), Entry("alice", 2)), Entry("alice", 2)).apart())
    }

    @Test
    fun matchesByAthleteNotWholeEntry() {
        assertNull(board(listOf(Entry("alice", 3)), Entry("alice", 58)).apart(), "a different rank is still the viewer's row")
    }

    @Test
    fun noOwnEntryIsNothing() {
        assertNull(board(listOf(Entry("bob", 1)), viewer = null).apart())
    }

    @Test
    fun nothingBeforeTheFirstAnswer() {
        assertNull(pagedStateOf<Entry, Meta>().apart())
    }
}
