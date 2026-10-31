package io.github.solcott.activitytracker.app.record

import io.github.solcott.activitytracker.data.activity.ActivityMatch
import io.github.solcott.activitytracker.data.activity.DuplicateOf
import io.github.solcott.activitytracker.data.activity.OwnActivity
import io.github.solcott.activitytracker.lexicon.AtUri
import io.github.solcott.activitytracker.lexicon.app.ActivityRecord
import io.github.solcott.activitytracker.lexicon.app.Sport
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

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

    @Test
    fun anExactCopyIsIgnoredAndTickedBackAsImportAnyway() {
        val copy = ActivityMatch(DuplicateOf.InBatch(0), exactCopy = true)
        val selection = BatchSelection(batch = true)
        assertEquals(DuplicateAction.Ignore, selection.action(1, copy))
        assertEquals(false, selection.isSelected(1, copy))
        assertEquals(DuplicateAction.ImportAnyway, selection.toggled(1, copy).action(1, copy))
    }

    @Test
    fun anotherSportImportsAnywayInABatchButCombinesAlone() {
        val walk = ActivityMatch(DuplicateOf.InBatch(0), sameSport = false)
        assertEquals(DuplicateAction.ImportAnyway, BatchSelection(batch = true).action(1, walk))
        assertEquals(DuplicateAction.Combine, BatchSelection().action(1, walk))
    }

    @Test
    fun oneActionSetsEveryShownDuplicate() {
        val selection = BatchSelection(batch = true).withShownAction(DuplicateAction.Ignore, matches.indices.toList(), matches::getOrNull)
        assertEquals(listOf(true, true, false, true), selection.selected())
        assertEquals(
            DuplicateAction.Combine,
            selection.withShownAction(DuplicateAction.Combine, listOf(2), matches::getOrNull).action(2, matches[2]),
        )
    }

    @Test
    fun aCopyLeftOutStandsForWhatItCopiesInAChain() {
        // B (1) is an ignored exact copy of A (0); C (2) combines with B.
        val chain = listOf(null, ActivityMatch(DuplicateOf.InBatch(0), exactCopy = true), ActivityMatch(DuplicateOf.InBatch(1)))
        val plan = batchPlan(chain.size, chain, BatchSelection(batch = true))
        assertEquals(listOf(listOf(0, 2)), plan.groups.map { it.items })
        assertEquals(emptyList(), plan.alone)
    }

    @Test
    fun combineForAllImportsAnywayWhatCantCombineYet() {
        val record = ActivityRecord(Sport.RIDE, Instant.parse("2026-09-01T09:00:00Z"), 7200, Instant.parse("2026-09-01T12:00:00Z"))
        // Still in the outbox: no CID yet, so it can't be combined with.
        val uploading =
            ActivityMatch(DuplicateOf.Existing(OwnActivity(AtUri("at://did:plc:a/app.activitytracker.feed.activity/3k"), null, record)))
        val selection = BatchSelection(batch = true).withShownAction(DuplicateAction.Combine, listOf(0), listOf(uploading)::getOrNull)
        assertEquals(DuplicateAction.ImportAnyway, selection.action(0, uploading))
    }
}
