package io.github.solcott.activitytracker.app.segment

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runComposeUiTest
import io.github.solcott.activitytracker.app.ui.preview.AppPreview
import io.github.solcott.activitytracker.data.units.UnitSystem
import kotlin.test.Test

/**
 * A segment's elevation on its screen (segments/segment-elevation): gain, loss and average grade when known, and high, low and maximum
 * grade with a profile; nothing otherwise.
 */
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
    fun showsHighLowAndMaxGrade() = runComposeUiTest {
        setContent { AppPreview { SegmentUi(SegmentSamples.elevation) } }
        onNodeWithText("1,504 m high · 1,420 m low · 12% max grade").assertExists()
    }

    @Test
    fun highLowAndMaxGradeInImperialUnits() = runComposeUiTest {
        setContent { AppPreview(units = UnitSystem.Imperial) { SegmentUi(SegmentSamples.elevation) } }
        onNodeWithText("4,934 ft high · 4,659 ft low · 12% max grade").assertExists()
    }

    @Test
    fun aProfileWithoutAMaxGradeShowsHighAndLow() = runComposeUiTest {
        setContent { AppPreview { SegmentUi(SegmentSamples.lift) } }
        onNodeWithText("2,160 m high · 1,850 m low").assertExists()
        onNodeWithText("max grade", substring = true).assertDoesNotExist()
    }

    @Test
    fun elevationFromEffortsHasNoProfile() = runComposeUiTest {
        setContent { AppPreview { SegmentUi(SegmentSamples.elevationFromEfforts) } }
        onNodeWithText("84 m gain · 12 m loss · 4.5% avg grade").assertExists()
        onNodeWithTag(SEGMENT_PROFILE_TAG).assertDoesNotExist()
        onNodeWithText("high ·", substring = true).assertDoesNotExist()
    }

    @Test
    fun aLiftShowsItsProfileToo() = runComposeUiTest {
        setContent { AppPreview { SegmentUi(SegmentSamples.lift) } }
        onNodeWithTag(SEGMENT_PROFILE_TAG).assertExists()
    }
}
