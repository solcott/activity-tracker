package io.github.solcott.activitytracker.app.boards

import io.github.solcott.activitytracker.app.text.AppText
import io.github.solcott.activitytracker.lexicon.app.LeaderboardScope
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** What a board's choice shows and offers (segments/segment-leaderboards, dev/shared-display-text). */
class BoardChoiceTest {
    private fun label(token: String) = AppText.written(token.uppercase())

    @Test
    fun theChosenOptionsLabelShows() {
        val choice = BoardChoice.of(listOf("a", "b"), "b", label = ::label)
        assertEquals(AppText.written("B"), choice.label)
        assertTrue(choice.isChosen(choice.options[1]))
        assertFalse(choice.isChosen(choice.options[0]))
    }

    @Test
    fun anUnlistedChoiceReadsAsItself() {
        assertEquals(AppText.written("c"), BoardChoice.of(listOf("a", "b"), "c", label = ::label).label)
    }

    @Test
    fun oneOptionIsALabelAndTwoAMenu() {
        assertFalse(BoardChoice.of(listOf("a"), "a", label = ::label).offersMenu)
        assertTrue(BoardChoice.of(listOf("a", "b"), "a", label = ::label).offersMenu)
    }

    @Test
    fun anAllChoiceNarrowsOnlyWhileOneIsChosen() {
        val all = AppText.written("All")
        val none = BoardChoice.orAll(all, listOf("a", "b"), null, ::label)
        assertEquals(listOf(null, "a", "b"), none.options.map { it.value })
        assertEquals(all, none.label)
        assertFalse(none.narrows)
        assertTrue(BoardChoice.orAll(all, listOf("a", "b"), "a", ::label).narrows)
    }

    @Test
    fun theScopeIsEveryoneOrFollowing() {
        val everyone = RankBoardTexts.scope(LeaderboardScope.EVERYONE)
        assertEquals(
            listOf(AppText.named("leaderboard_scope_everyone"), AppText.named("leaderboard_scope_following")),
            everyone.options.map { it.label },
        )
        assertFalse(everyone.narrows)
        assertTrue(RankBoardTexts.scope(LeaderboardScope.FOLLOWING).narrows)
    }

    @Test
    fun aRankIsNeverGroupedAndTheReachedDayIsNamed() {
        assertEquals(AppText.written("1204"), RankBoardTexts.rank(1204))
        assertEquals(AppText.named("mayors_reached", AppText.written("day")), RankBoardTexts.reached(AppText.written("day")))
    }
}
