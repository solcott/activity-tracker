package io.github.solcott.activitytracker.app.record

import io.github.solcott.activitytracker.data.activity.ActivityMatch
import io.github.solcott.activitytracker.data.activity.DuplicateOf
import kotlin.test.Test
import kotlin.test.assertEquals

class BatchSelectionTest {
    /** Four activities: the third repeats the first in the batch. */
    private val matches = listOf(null, null, ActivityMatch(DuplicateOf.InBatch(0)), null)

    private fun BatchSelection.selected() = matches.indices.map { isSelected(it, matches[it]) }

    @Test
    fun everythingStartsSelectedAndADuplicateCombines() {
        val selection = BatchSelection()
        assertEquals(listOf(true, true, true, true), selection.selected())
        assertEquals(DuplicateAction.Combine, selection.action(2, matches[2]))
    }

    @Test
    fun togglingDeclinesAndTicksAgain() {
        val declined = BatchSelection().toggled(1, null).toggled(2, matches[2])
        assertEquals(listOf(true, false, false, true), declined.selected())
        assertEquals(DuplicateAction.Ignore, declined.action(2, matches[2]))
        assertEquals(listOf(true, true, true, true), declined.toggled(1, null).toggled(2, matches[2]).selected())
    }

    @Test
    fun deselectAllLeavesDuplicatesUnlessOnlyTheyShow() {
        val all = BatchSelection().all(selected = false, shown = matches.indices.toList(), matchOf = matches::getOrNull, duplicates = false)
        assertEquals(listOf(false, false, true, false), all.selected())
        val duplicates = BatchSelection().all(selected = false, shown = listOf(2), matchOf = matches::getOrNull, duplicates = true)
        assertEquals(listOf(true, true, false, true), duplicates.selected())
    }

    @Test
    fun aPlanCombinesCopiesAndKeepsTheRestAlone() {
        val plan = batchPlan(matches.size, matches, BatchSelection())
        assertEquals(listOf(1, 3), plan.alone)
        assertEquals(listOf(listOf(0, 2)), plan.groups.map { it.items })
    }
}
