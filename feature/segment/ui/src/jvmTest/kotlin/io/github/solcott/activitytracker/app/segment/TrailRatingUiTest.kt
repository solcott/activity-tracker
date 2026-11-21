package io.github.solcott.activitytracker.app.segment

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.v2.runComposeUiTest
import io.github.solcott.activitytracker.app.ui.TestTags
import io.github.solcott.activitytracker.app.ui.preview.AppPreview
import kotlin.test.Test

/**
 * Rating colours on the Trail page and browse (segments/trail-ratings): the map's legend, and each rated row's swatch read by its level.
 */
@OptIn(ExperimentalTestApi::class)
class TrailRatingUiTest {
    @Test
    fun theTrailPagesLegendAndRowsNameTheirLevels() = runComposeUiTest {
        setContent { AppPreview { PathUi(PathSamples.loaded) } }
        val legend = onNodeWithTag(TestTags.TRAIL_RATING_LEGEND, useUnmergedTree = true)
        legend.assertExists()
        onNode(hasAnyAncestor(hasTestTag(TestTags.TRAIL_RATING_LEGEND)) and hasText("Easy"), useUnmergedTree = true).assertExists()
        onNode(hasAnyAncestor(hasTestTag(TestTags.TRAIL_RATING_LEGEND)) and hasText("Intermediate"), useUnmergedTree = true).assertExists()
        onNode(hasClickAction() and hasText("Ann's") and hasContentDescription("Easy")).assertExists()
        onNode(hasClickAction() and hasText("Ann's: Lupine Hiking → Potato Hill") and hasContentDescription("Intermediate")).assertExists()
    }

    @Test
    fun browseRowsShowTheirLevelAndTheLegendListsEveryOne() = runComposeUiTest {
        setContent { AppPreview { SegmentBrowseUi(SegmentBrowseSamples.loaded) } }
        onNode(hasClickAction() and hasText("Ridge Trail") and hasContentDescription("Intermediate")).assertExists()
        onNode(hasClickAction() and hasText("Pine St") and hasContentDescription("Easiest")).assertExists()
        for (level in listOf("Easiest", "Easy", "Intermediate", "Difficult", "Extreme")) {
            onNode(hasAnyAncestor(hasTestTag(TestTags.TRAIL_RATING_LEGEND)) and hasText(level), useUnmergedTree = true).assertExists()
        }
    }

    @Test
    fun anUnratedRowShowsNoSwatch() = runComposeUiTest {
        setContent { AppPreview { PathUi(PathSamples.empty) } }
        onNodeWithTag(TestTags.TRAIL_RATING_LEGEND, useUnmergedTree = true).assertDoesNotExist()
        onNodeWithTag(TestTags.TRAIL_RATING_SWATCH, useUnmergedTree = true).assertDoesNotExist()
    }
}
