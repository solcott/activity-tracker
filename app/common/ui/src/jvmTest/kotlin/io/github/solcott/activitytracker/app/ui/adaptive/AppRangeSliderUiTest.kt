package io.github.solcott.activitytracker.app.ui.adaptive

import androidx.compose.foundation.layout.width
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
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

    @Test
    fun aDragBelowTheTrackSlowsTheThumb() = runComposeUiTest {
        var range by mutableStateOf(0f..100f)
        val shares = mutableListOf<Float?>()
        setContent {
            AppPreview {
                AppRangeSlider(
                    value = range,
                    onValueChange = { range = it },
                    valueRange = 0f..100f,
                    modifier = Modifier.width(400.dp).testTag("slider"),
                    precision = { below -> if (below < 24.dp) 1f else 0.25f },
                    onPrecisionChange = { shares += it },
                )
            }
        }
        onNodeWithTag("slider").performTouchInput {
            down(centerLeft)
            moveBy(Offset(0f, 200f))
            // The thumb follows a quarter of the pointer's travel once it is below the track.
            moveBy(Offset(width / 2f, 0f))
            up()
        }
        assertEquals(12.5f, range.start, 1f)
        assertEquals(100f, range.endInclusive)
        assertEquals(listOf(0.25f, null), shares)
    }

    @Test
    fun bothThumbsAtOnePlaceFollowTheDragsDirection() = runComposeUiTest {
        var range by mutableStateOf(50f..50f)
        setContent {
            AppPreview {
                AppRangeSlider(
                    value = range,
                    onValueChange = { range = it },
                    valueRange = 0f..100f,
                    modifier = Modifier.width(400.dp).testTag("slider"),
                    precision = { 1f },
                )
            }
        }
        // Pressed just right of the thumbs, a drag left still moves the start, which can go that way.
        onNodeWithTag("slider").performTouchInput {
            down(center + Offset(4f, 0f))
            moveBy(Offset(-width / 4f, 0f))
            up()
        }
        assertEquals(25f, range.start, 1f)
        assertEquals(50f, range.endInclusive)
        // And pressed just left of them, a drag right moves the end.
        range = 50f..50f
        onNodeWithTag("slider").performTouchInput {
            down(center - Offset(4f, 0f))
            moveBy(Offset(width / 4f, 0f))
            up()
        }
        assertEquals(50f, range.start)
        assertEquals(75f, range.endInclusive, 1f)
    }
}
