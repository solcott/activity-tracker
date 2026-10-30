package io.github.solcott.activitytracker.app.gear

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import io.github.solcott.activitytracker.app.preview.SessionPreviewSubUis
import io.github.solcott.activitytracker.app.ui.preview.AppPreview
import io.github.solcott.activitytracker.data.units.UnitSystem
import kotlin.test.Test
import kotlin.test.assertTrue

/** A gear item's page (gear/gear-usage): its totals with starting usage, breakdowns, activities, and Edit for the owner. */
@OptIn(ExperimentalTestApi::class)
class GearUiTest {
    private fun show(state: GearState, units: UnitSystem = UnitSystem.Metric, checks: ComposeUiTest.() -> Unit) = runComposeUiTest {
        setContent { AppPreview(subUis = SessionPreviewSubUis, units = units) { GearUi(state) } }
        checks()
    }

    @Test
    fun theOwnersPageShowsTotalsStartingUsageBreakdownsAndEdit() =
        show(GearSamples.all.getValue("loaded")) {
            onNodeWithText("Edit").assertExists()
            onNodeWithText("2,065 km").assertExists()
            onNodeWithText("Includes 2,000 km before tracking").assertExists()
            onNodeWithText("By activity type").assertExists()
            onNodeWithText("By year").assertExists()
            onNodeWithText("2025").assertExists()
            onNodeWithText("Public", substring = true).assertExists()
        }

    @Test
    fun aFollowerSeesNoEdit() =
        show(GearSamples.follower) {
            onNodeWithText("Edit").assertDoesNotExist()
            onNodeWithText("Gravel loop").assertExists()
        }

    @Test
    fun milesInImperial() = show(GearSamples.all.getValue("loaded"), UnitSystem.Imperial) { onNodeWithText("1,283 mi").assertExists() }

    @Test
    fun anItemTheViewerMayNotSeeSaysSo() =
        show(GearSamples.notFound) { onNodeWithText("This gear isn't available", substring = true).assertExists() }

    @Test
    fun aBikeListsItsComponentsNowAndBeforeWithMountForTheOwner() =
        show(GearSamples.components) {
            onNodeWithText("Components").assertExists()
            onNodeWithText("Chain #4").assertExists()
            onNodeWithText("Since 2026-07-01").assertExists()
            onNodeWithText("Previous").assertExists()
            onNodeWithText("2025-03-01 – 2026-07-01").assertExists()
            onNodeWithText("Mount a component").assertExists()
            onNodeWithContentDescription("Actions for Chain #4").assertExists()
        }

    @Test
    fun aFollowerCannotManageComponents() =
        show(GearSamples.componentsFollower) {
            onNodeWithText("Chain #4").assertExists()
            onNodeWithText("Mount a component").assertDoesNotExist()
            onNodeWithContentDescription("Actions for Chain #4").assertDoesNotExist()
        }

    @Test
    fun aChainShowsTheBikeItWasMountedOn() =
        show(GearSamples.chain) {
            onNodeWithText("Mounted on").assertExists()
            onNodeWithText("Gravel bike").assertExists()
        }

    @Test
    fun replacingAChainOffersTheOwnersOtherParts() =
        show(GearSamples.replacing) {
            onNodeWithText("Replace Chain #4").assertExists()
            onNodeWithText("Chain #5").assertExists()
            onNodeWithText("New item").assertExists()
        }

    @Test
    fun activityCardsAreSpacedApart() =
        show(GearSamples.follower) {
            fun card(title: String) = onNode(hasClickAction() and hasText(title, substring = true))
            onNode(hasScrollToNodeAction()).performScrollToNode(hasText("Morning Ride", substring = true))
            val first = card("Gravel loop").getUnclippedBoundsInRoot()
            val second = card("Morning Ride").getUnclippedBoundsInRoot()
            assertTrue(second.top - first.bottom >= 12.dp, "gap ${second.top - first.bottom}")
        }
}
