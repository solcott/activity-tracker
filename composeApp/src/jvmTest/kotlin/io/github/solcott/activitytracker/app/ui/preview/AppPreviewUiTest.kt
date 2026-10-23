package io.github.solcott.activitytracker.app.ui.preview

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runComposeUiTest
import com.slack.circuit.subcircuit.SubCircuitContent
import com.slack.circuit.subcircuit.SubCircuitOuterEvent
import com.slack.circuit.subcircuit.SubScreen
import io.github.solcott.activitytracker.app.detail.ActivityDetailSamples
import io.github.solcott.activitytracker.app.detail.ActivityDetailUi
import io.github.solcott.activitytracker.app.feed.FeedSamples
import io.github.solcott.activitytracker.app.feed.FeedUi
import io.github.solcott.activitytracker.app.fixtures.Fixed
import io.github.solcott.activitytracker.app.ui.ActivityMap
import io.github.solcott.activitytracker.app.ui.adaptive.WindowWidth
import io.github.solcott.activitytracker.app.ui.theme.AppTheme
import io.github.solcott.activitytracker.app.ui.theme.BrandDark
import io.github.solcott.activitytracker.data.units.UnitSystem
import kotlin.test.Test
import kotlin.test.assertEquals

/** The harness renders screens offline in the look, theme, units and width a test chooses (dev/ui-test-harness). */
@OptIn(ExperimentalTestApi::class)
class AppPreviewUiTest {
    @Test
    fun darkThemeIsChosenNotReadFromTheSystem() = runComposeUiTest {
        var background = Color.Unspecified
        setContent { AppTheme(darkTheme = true) { background = MaterialTheme.colorScheme.background } }
        assertEquals(BrandDark.background, background)
    }

    @Test
    fun aRouteMapDrawsItsPreviewUnderInspection() = runComposeUiTest {
        setContent { AppPreview { ActivityMap(Fixed.POLYLINE) } }
        waitForIdle()
    }

    @Test
    fun theFeedRendersOfflineWithItsFilterBar() = runComposeUiTest {
        setContent { AppPreview { FeedUi(FeedSamples.loaded) } }
        onNodeWithText("Morning Ride").assertExists()
        // The filter sub-screen's own UI, from the harness's sub-circuit.
        onNodeWithText("Any time", substring = true).assertExists()
    }

    @Test
    fun imperialUnitsShowMiles() = runComposeUiTest {
        setContent { AppPreview(units = UnitSystem.Imperial) { FeedUi(FeedSamples.loaded) } }
        onNodeWithText("20.13 mi", substring = true).assertExists()
    }

    @Test
    fun theActivityPageLaysOutByWidth() {
        val lists = SemanticsMatcher.keyIsDefined(SemanticsProperties.CollectionInfo)
        val state = ActivityDetailSamples.all.getValue("loaded")
        fun listsAt(width: WindowWidth): Int {
            var count = 0
            runComposeUiTest {
                setContent { AppPreview(width = width) { ActivityDetailUi(state) } }
                count = onAllNodes(lists).fetchSemanticsNodes().size
            }
            return count
        }
        // Expanded adds the side pane's own list beside the cards.
        assertEquals(listsAt(WindowWidth.Compact) + 1, listsAt(WindowWidth.Expanded))
    }

    @Test
    fun anUnknownSubScreenRendersNothing() = runComposeUiTest {
        setContent { AppPreview { SubCircuitContent(UnknownScreen, outerEventSink = {}) } }
        onNodeWithText("SubCircuitScreen", substring = true).assertDoesNotExist()
        onNodeWithText("not available", substring = true).assertDoesNotExist()
    }

    private data object UnknownScreen : SubScreen<SubCircuitOuterEvent>
}
