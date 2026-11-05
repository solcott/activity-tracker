package io.github.solcott.activitytracker.app.segment

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runComposeUiTest
import io.github.solcott.activitytracker.app.ui.preview.AppPreview
import io.github.solcott.activitytracker.data.units.UnitSystem
import kotlin.test.Test

/** A segment's elevation on its screen (segments/segment-elevation): gain, loss and average grade when known, nothing otherwise. */
@OptIn(ExperimentalTestApi::class)
class SegmentElevationUiTest {
    @Test
    fun showsGainLossAndGrade() = runComposeUiTest {
        setContent { AppPreview { SegmentUi(SegmentSamples.elevation) } }
        onNodeWithText("84 m gain · 12 m loss · 4.5% avg grade").assertExists()
    }

    @Test
    fun showsFeetInImperialUnits() = runComposeUiTest {
        setContent { AppPreview(units = UnitSystem.Imperial) { SegmentUi(SegmentSamples.elevation) } }
        onNodeWithText("276 ft gain · 39 ft loss · 4.5% avg grade").assertExists()
    }

    @Test
    fun aSegmentWithoutElevationShowsNone() = runComposeUiTest {
        setContent { AppPreview { SegmentUi(SegmentSamples.loaded) } }
        onNodeWithText("avg grade", substring = true).assertDoesNotExist()
    }
}
