package io.github.solcott.activitytracker.app.segment

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runComposeUiTest
import io.github.solcott.activitytracker.app.ui.preview.AppPreview
import io.github.solcott.activitytracker.data.units.UnitSystem
import kotlin.math.roundToInt
import kotlin.test.Test
import kotlin.test.assertEquals

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
        onNodeWithTag(SEGMENT_PROFILE_TAG).assertDoesNotExist()
    }

    @Test
    fun drawsTheTerrainProfile() = runComposeUiTest {
        setContent { AppPreview { SegmentUi(SegmentSamples.elevation) } }
        onNodeWithTag(SEGMENT_PROFILE_TAG).assertExists()
        onNodeWithContentDescription("Elevation profile over 1.60 km, from 1,420 m to 1,492 m", substring = true).assertExists()
    }

    @Test
    fun theProfileIsInTheViewersUnits() = runComposeUiTest {
        setContent { AppPreview(units = UnitSystem.Imperial) { SegmentUi(SegmentSamples.elevation) } }
        onNodeWithContentDescription("Elevation profile over 0.99 mi, from 4,659 ft to 4,895 ft", substring = true).assertExists()
    }

    @Test
    fun elevationFromEffortsHasNoProfile() = runComposeUiTest {
        setContent { AppPreview { SegmentUi(SegmentSamples.elevationFromEfforts) } }
        onNodeWithText("84 m gain · 12 m loss · 4.5% avg grade").assertExists()
        onNodeWithTag(SEGMENT_PROFILE_TAG).assertDoesNotExist()
    }

    @Test
    fun aLiftShowsItsProfileToo() = runComposeUiTest {
        setContent { AppPreview { SegmentUi(SegmentSamples.lift) } }
        onNodeWithTag(SEGMENT_PROFILE_TAG).assertExists()
    }

    @Test
    fun distanceLabelsAreRoundSteps() {
        assertEquals(listOf(0.0, 0.5, 1.0, 1.5), distanceLabels(1.6))
        assertEquals(listOf(0.0, 0.2, 0.4, 0.6, 0.8), distanceLabels(0.99).map { (it * 10).roundToInt() / 10.0 })
        assertEquals(listOf(0.0, 10.0, 20.0, 30.0, 40.0, 50.0), distanceLabels(50.0))
    }
}
