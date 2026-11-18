package io.github.solcott.activitytracker.app.ui.adaptive

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.v2.runComposeUiTest
import io.github.solcott.activitytracker.app.ui.preview.AppPreview
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The slider wrappers are controlled: the thumbs show the host's value, and a drag only reports through `onValueChange`. Material 3 1.5's
 * state-taking sliders no longer move their own state when given `onValueChange`, so the wrappers keep a state that follows the value.
 */
@OptIn(ExperimentalTestApi::class)
class AppSliderUiTest {
    @Test
    fun aSliderFollowsTheHostsValue() = runComposeUiTest {
        var value by mutableFloatStateOf(2f)
        setContent { AppPreview { AppSlider(value, onValueChange = {}, valueRange = 0f..10f, modifier = Modifier.testTag(SLIDER)) } }
        assertEquals(2f, onNodeWithTag(SLIDER).progress())

        value = 7f
        assertEquals(7f, onNodeWithTag(SLIDER).progress())
    }

    @Test
    fun aSliderReportsAMoveAndShowsItOnceTheHostTakesIt() = runComposeUiTest {
        var value by mutableFloatStateOf(2f)
        val reported = mutableListOf<Float>()
        setContent {
            AppPreview { AppSlider(value, onValueChange = { reported += it }, valueRange = 0f..10f, modifier = Modifier.testTag(SLIDER)) }
        }

        onNodeWithTag(SLIDER).performSemanticsAction(SemanticsActions.SetProgress) { it(5f) }
        assertEquals(listOf(5f), reported)
        assertEquals(2f, onNodeWithTag(SLIDER).progress(), "the host hasn't taken the move yet")

        value = reported.last()
        assertEquals(5f, onNodeWithTag(SLIDER).progress())
    }

    @Test
    fun aRangeSlidersThumbsFollowTheHostsRange() = runComposeUiTest {
        var range by mutableStateOf(2f..6f)
        setContent { AppPreview { AppRangeSlider(range, onValueChange = {}, valueRange = 0f..10f) } }
        assertEquals(listOf(2f, 6f), thumbs())

        range = 3f..8f
        assertEquals(listOf(3f, 8f), thumbs())
    }

    @Test
    fun aRangeSliderReportsTheRangeAThumbMoves() = runComposeUiTest {
        val reported = mutableListOf<ClosedFloatingPointRange<Float>>()
        setContent { AppPreview { AppRangeSlider(2f..6f, onValueChange = { reported += it }, valueRange = 0f..10f) } }

        onAllNodes(hasProgress)[1].performSemanticsAction(SemanticsActions.SetProgress) { it(9f) }
        assertEquals(listOf(2f..9f), reported)
        assertEquals(listOf(2f, 6f), thumbs(), "the host hasn't taken the move")
    }

    private fun androidx.compose.ui.test.ComposeUiTest.thumbs(): List<Float> =
        onAllNodes(hasProgress).fetchSemanticsNodes().map { it.config[SemanticsProperties.ProgressBarRangeInfo].current }

    private fun SemanticsNodeInteraction.progress(): Float = fetchSemanticsNode().config[SemanticsProperties.ProgressBarRangeInfo].current

    private companion object {
        const val SLIDER = "slider"
        val hasProgress = SemanticsMatcher.keyIsDefined(SemanticsProperties.ProgressBarRangeInfo)
    }
}
