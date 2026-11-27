package io.github.solcott.activitytracker.app.ui.adaptive

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.v2.runComposeUiTest
import io.github.solcott.activitytracker.app.ui.preview.AppPreview
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class AppRangeSliderUiTest {
    @Test
    fun eachThumbReadsByItsLabelAndMovesAlone() = runComposeUiTest {
        var range by mutableStateOf(0f..100f)
        setContent {
            AppPreview {
                AppRangeSlider(
                    value = range,
                    onValueChange = { range = it },
                    valueRange = 0f..100f,
                    labels = RangeSliderLabels("Trim start", "${range.start.toInt()} in", "Trim end", "${range.endInclusive.toInt()} in"),
                )
            }
        }
        onNodeWithContentDescription("Trim start").assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "0 in"))
        onNodeWithContentDescription("Trim start").performSemanticsAction(SemanticsActions.SetProgress) { check(it(30f)) }
        assertEquals(30f..100f, range)
        onNodeWithContentDescription("Trim start").assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "30 in"))

        // The end can't be set below the start.
        onNodeWithContentDescription("Trim end").performSemanticsAction(SemanticsActions.SetProgress) { check(it(10f)) }
        assertEquals(30f..30f, range)
        // Material's own "Range start" node is hidden behind the labelled ones.
        onNodeWithContentDescription("Range start").assertDoesNotExist()
    }
}
