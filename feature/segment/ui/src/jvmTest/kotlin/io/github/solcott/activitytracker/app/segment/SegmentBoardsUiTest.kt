package io.github.solcott.activitytracker.app.segment

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import io.github.solcott.activitytracker.app.fixtures.PagedStates
import io.github.solcott.activitytracker.app.ui.BoardChipRowTag
import io.github.solcott.activitytracker.app.ui.OwnPlaceBarTag
import io.github.solcott.activitytracker.app.ui.RankRowCrownTag
import io.github.solcott.activitytracker.app.ui.RankRowValueTag
import io.github.solcott.activitytracker.app.ui.preview.AppPreview
import io.github.solcott.activitytracker.data.segments.BoardMeta
import io.github.solcott.activitytracker.lexicon.app.LeaderboardPeriod
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * A segment's leaderboard and Mayors board (segments/segment-leaderboards, segments/segment-mayors): rows that line up, the viewer's own
 * row marked, their place pinned with their gap to first, and the choices as chips.
 */
@OptIn(ExperimentalTestApi::class)
class SegmentBoardsUiTest {
    private val you = SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "You")

    @Test
    fun aViewerFarDownIsPinnedWithTheirGapToFirst() = runComposeUiTest {
        setContent { AppPreview { SegmentLeaderboardUi(SegmentLeaderboardSamples.variants.getValue("filtered")) } }
        onNodeWithTag(OwnPlaceBarTag).assertExists()
        onNodeWithText("Your place").assertExists()
        onNodeWithText("2:52 behind 1st").assertExists()
        onNode(you).assertExists()
    }

    @Test
    fun theLastRowScrollsClearOfTheBar() = runComposeUiTest {
        setContent { AppPreview { SegmentLeaderboardUi(SegmentLeaderboardSamples.variants.getValue("filtered")) } }
        onAllNodes(hasScrollAction()).onFirst().performScrollToNode(hasText("Erin"))
        val bar = onNodeWithTag(OwnPlaceBarTag).getBoundsInRoot()
        val last = onNodeWithText("Erin").getBoundsInRoot()
        assertTrue(last.bottom <= bar.top, "Erin's row ends at ${last.bottom}, the bar starts at ${bar.top}")
    }

    @Test
    fun aViewerAmongTheRowsIsMarkedAndNotPinned() = runComposeUiTest {
        setContent { AppPreview { SegmentLeaderboardUi(SegmentLeaderboardSamples.variants.getValue("ownRow")) } }
        onNodeWithTag(OwnPlaceBarTag).assertDoesNotExist()
        onAllNodes(you).assertCountEquals(1)
        onNode(you).assert(hasText("Alice", substring = true))
    }

    @Test
    fun scopeIsAChipBesideTheBrackets() = runComposeUiTest {
        val events = mutableListOf<BoardFilterEvent>()
        val state = SegmentLeaderboardSamples.variants.getValue("filtered")
        setContent { AppPreview { SegmentLeaderboardUi(state.copy(filters = state.filters.copy(eventSink = { events += it }))) } }
        onNodeWithText("Everyone").performClick()
        onNodeWithText("Following").performClick()
        assertEquals(BoardFilterEvent.ScopeChanged("following"), events.single())
    }

    @Test
    fun theDateRangeIsAChipThatOffersToday() = runComposeUiTest {
        val events = mutableListOf<BoardFilterEvent>()
        val state = SegmentLeaderboardSamples.all.getValue("loaded")
        setContent { AppPreview { SegmentLeaderboardUi(state.copy(filters = state.filters.copy(eventSink = { events += it }))) } }
        onNodeWithText("All time").performClick()
        onNodeWithText("Today").performClick()
        assertEquals(BoardFilterEvent.PeriodChanged(LeaderboardPeriod.TODAY), events.single())
    }

    @Test
    fun eachBracketIsItsOwnChip() = runComposeUiTest {
        val events = mutableListOf<BoardFilterEvent>()
        val state = SegmentLeaderboardSamples.all.getValue("loaded")
        setContent { AppPreview { SegmentLeaderboardUi(state.copy(filters = state.filters.copy(eventSink = { events += it }))) } }
        onNodeWithText("Filters").assertDoesNotExist()
        onNodeWithText("All ages").performClick()
        onNodeWithText("35–44").performClick()
        assertEquals(BoardFilterEvent.AgeGroupChanged("35-44"), events.single())
    }

    @Test
    fun theChipsStayOnOneRowThatScrollsSideways() = runComposeUiTest {
        setContent { AppPreview { SegmentLeaderboardUi(SegmentLeaderboardSamples.variants.getValue("filtered")) } }
        val row = onNodeWithTag(BoardChipRowTag)
        val bounds = row.getBoundsInRoot()
        fun onTheRow(chip: String) {
            val chipBounds = onNodeWithText(chip, useUnmergedTree = true).getBoundsInRoot()
            assertTrue(chipBounds.top >= bounds.top && chipBounds.bottom <= bounds.bottom, "$chip is on the row's one line")
            assertTrue(chipBounds.left >= bounds.left && chipBounds.right <= bounds.right, "$chip is in view")
        }
        onTheRow("Everyone")
        row.performScrollToNode(hasText("All genders"))
        onTheRow("All genders")
    }

    @Test
    fun aTimeStaysOnOneLineAtTwiceTheTextSize() = runComposeUiTest {
        setContent {
            AppPreview {
                val density = LocalDensity.current
                CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale = 2f)) {
                    SegmentLeaderboardUi(SegmentLeaderboardSamples.variants.getValue("filtered"))
                }
            }
        }
        val values = onAllNodesWithTag(RankRowValueTag, useUnmergedTree = true).fetchSemanticsNodes()
        assertTrue(values.isNotEmpty())
        values.indices.forEach { assertEquals(1, onAllNodesWithTag(RankRowValueTag, useUnmergedTree = true)[it].lineCount()) }
    }

    @Test
    fun theMayorsBoardSaysWhatItTakesAndOffersEveryChoiceAsAChip() = runComposeUiTest {
        val events = mutableListOf<BoardFilterEvent>()
        val state = SegmentMayorsSamples.variants.getValue("filtered")
        setContent { AppPreview { SegmentMayorsUi(state.copy(filters = state.filters.copy(eventSink = { events += it }))) } }
        onNodeWithText("Your place").assertExists()
        onNodeWithText("1,204 more efforts to be Mayor").assertExists()
        onNodeWithText("This week").assertExists()
        onNodeWithText("Everyone").performClick()
        onNodeWithText("Following").performClick()
        assertEquals(BoardFilterEvent.ScopeChanged("following"), events.single())
    }

    @Test
    fun aMayorsViewerAmongTheRowsIsMarkedAndNotPinned() = runComposeUiTest {
        setContent { AppPreview { SegmentMayorsUi(SegmentMayorsSamples.variants.getValue("ownRow")) } }
        onNodeWithTag(OwnPlaceBarTag).assertDoesNotExist()
        onAllNodes(you).assertCountEquals(1)
    }

    @Test
    fun theMayorIsCrownedAndNoOneElse() = runComposeUiTest {
        setContent { AppPreview { SegmentMayorsUi(SegmentMayorsSamples.variants.getValue("ownRow")) } }
        onAllNodesWithTag(RankRowCrownTag, useUnmergedTree = true).assertCountEquals(1)
        onNodeWithContentDescription("Mayor", useUnmergedTree = true).assertExists()
        onNode(hasText("Bob", substring = true) and hasText("Mayor · Reached", substring = true)).assertExists()
        onNodeWithText("1").assertDoesNotExist()
        onNodeWithText("2").assertExists()
    }

    @Test
    fun aMayorViewerIsCrownedAndMarkedAsTheirs() = runComposeUiTest {
        val entries = SegmentMayorsSamples.entries
        setContent {
            AppPreview { SegmentMayorsUi(SegmentMayorsState("Ridge", entries = PagedStates.end(entries, BoardMeta(entries[0])))) }
        }
        onNode(you).assert(hasText("Bob", substring = true))
        onAllNodesWithTag(RankRowCrownTag, useUnmergedTree = true).assertCountEquals(1)
        onNodeWithTag(OwnPlaceBarTag).assertDoesNotExist()
    }

    @Test
    fun theLeaderboardsFirstIsNotCrowned() = runComposeUiTest {
        setContent { AppPreview { SegmentLeaderboardUi(SegmentLeaderboardSamples.variants.getValue("ownRow")) } }
        onAllNodesWithTag(RankRowCrownTag, useUnmergedTree = true).assertCountEquals(0)
        onNodeWithText("1").assertExists()
    }

    private fun SemanticsNodeInteraction.lineCount(): Int {
        val layouts = mutableListOf<TextLayoutResult>()
        fetchSemanticsNode().config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action?.invoke(layouts)
        return layouts.single().lineCount
    }
}
