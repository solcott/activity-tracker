package io.github.solcott.activitytracker.app.divesite

import io.github.solcott.activitytracker.app.boards.RankBoardTexts
import io.github.solcott.activitytracker.app.text.AppText
import io.github.solcott.activitytracker.app.text.DateText
import io.github.solcott.activitytracker.data.units.formatWhole
import io.github.solcott.activitytracker.lexicon.Did
import io.github.solcott.activitytracker.lexicon.Handle
import io.github.solcott.activitytracker.lexicon.app.DiveSiteRegularEntry
import io.github.solcott.activitytracker.lexicon.app.ProfileViewBasic
import io.github.solcott.activitytracker.lexicon.app.RegularsPeriod
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.collections.immutable.persistentListOf

/** What a dive site's Regulars board offers and says (dive-sites/site-regulars, dev/shared-display-text). */
class DiveSiteRegularsTextsTest {
    private val bob = ProfileViewBasic(Did("did:plc:bob"), Handle("bob.test"), null, null)

    private fun entry(rank: Int, visits: Int = 3) = DiveSiteRegularEntry(rank, bob, visits, "2026-10-26")

    @Test
    fun theWindowsAreAllTimeThisYearAndLastYear() {
        val choice = DiveSiteRegularsTexts.period(RegularsPeriod.LAST_YEAR)
        assertEquals(RegularsPeriod.all, choice.options.map { it.value })
        assertEquals(
            listOf("leaderboard_period_all_time", "leaderboard_period_year", "mayor_period_last_year").map(AppText::named),
            choice.options.map { it.label },
        )
        assertEquals(AppText.named("mayor_period_last_year"), choice.label)
    }

    @Test
    fun anEntryReadsItsDayAndDives() {
        assertEquals(RankBoardTexts.reached(DateText.date(2026, 10, 26)), DiveSiteRegularsTexts.reached(entry(1)))
        assertEquals(AppText.Plural("dive_site_visits", 3, persistentListOf(AppText.written("3"))), DiveSiteRegularsTexts.visits(entry(1)))
        assertEquals(AppText.written(formatWhole(1204)), (DiveSiteRegularsTexts.visits(1204) as AppText.Plural).args.single())
    }

    @Test
    fun onlyTheTopRegularIsAccented() {
        val state = DiveSiteRegularsState("Blue Hole")
        assertTrue(state.accents(entry(1)))
        assertFalse(state.accents(entry(2)))
        assertEquals(AppText.named("leaderboard_period_all_time"), state.periodChoice.label)
        assertFalse(state.scopeChoice.narrows)
    }
}
