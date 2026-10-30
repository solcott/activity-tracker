package io.github.solcott.activitytracker.app

import io.github.solcott.activitytracker.app.feedfilter.covers
import io.github.solcott.activitytracker.app.feedfilter.toggle
import io.github.solcott.activitytracker.data.sport.SportCatalog
import io.github.solcott.activitytracker.lexicon.app.ActivityType
import io.github.solcott.activitytracker.lexicon.app.Sport
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TypeSelectionTest {
    private val run = ActivityType(Sport.RUN)
    private val runSubs = SportCatalog.subSports(Sport.RUN).map { ActivityType(Sport.RUN, it.subSport) }

    @Test
    fun aSportCoversItsSubSports() {
        val picked = emptySet<ActivityType>().toggle(runSubs.first()).toggle(run)
        assertEquals(setOf(run), picked, "the sport replaces its own sub-sports")
        assertTrue(runSubs.all(picked::covers))
    }

    @Test
    fun everySubSportCollapsesToTheSport() {
        val picked = runSubs.fold(emptySet<ActivityType>()) { acc, type -> acc.toggle(type) }
        assertEquals(setOf(run), picked)
    }

    @Test
    fun uncheckingACoveredSubSportKeepsTheOthers() {
        val picked = setOf(run).toggle(runSubs.first())
        assertEquals(runSubs.drop(1).toSet(), picked)
        assertEquals(emptySet(), picked.toggle(run).toggle(run), "toggling the sport twice clears it")
    }
}
