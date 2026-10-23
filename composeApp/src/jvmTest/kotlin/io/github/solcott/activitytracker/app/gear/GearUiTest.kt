package io.github.solcott.activitytracker.app.gear

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runComposeUiTest
import io.github.solcott.activitytracker.app.ui.preview.AppPreview
import io.github.solcott.activitytracker.data.units.UnitSystem
import kotlin.test.Test

/** A gear item's page (gear/gear-usage): its totals with starting usage, breakdowns, activities, and Edit for the owner. */
@OptIn(ExperimentalTestApi::class)
class GearUiTest {
    private fun show(state: GearState, units: UnitSystem = UnitSystem.Metric, checks: ComposeUiTest.() -> Unit) = runComposeUiTest {
        setContent { AppPreview(units = units) { GearUi(state) } }
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
}
