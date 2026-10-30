package io.github.solcott.activitytracker.app.gear

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runComposeUiTest
import io.github.solcott.activitytracker.app.preview.SessionPreviewSubUis
import io.github.solcott.activitytracker.app.ui.preview.AppPreview
import io.github.solcott.activitytracker.data.units.UnitSystem
import kotlin.test.Test

/** Each row of the gear list shows its item's main total, in the athlete's units (gear/gear-usage). */
@OptIn(ExperimentalTestApi::class)
class GearListUiTest {
    @Test
    fun theMixedListShowsEachTypesMainTotal() = runComposeUiTest {
        setContent { AppPreview(subUis = SessionPreviewSubUis) { GearListUi(GearListSamples.all.getValue("loaded")) } }
        onNodeWithText("3,200 km").assertExists()
        onNodeWithText("14 days · 41,000 m").assertExists()
        onNodeWithText("120 h").assertExists()
        onNodeWithText("No activities yet").assertExists()
    }

    @Test
    fun totalsFollowTheAthletesUnits() = runComposeUiTest {
        setContent {
            AppPreview(subUis = SessionPreviewSubUis, units = UnitSystem.Imperial) { GearListUi(GearListSamples.all.getValue("loaded")) }
        }
        onNodeWithText("1,988 mi").assertExists()
        onNodeWithText("14 days · 134,514 ft").assertExists()
    }
}
