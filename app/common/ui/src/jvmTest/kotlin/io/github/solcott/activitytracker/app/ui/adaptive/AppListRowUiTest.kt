package io.github.solcott.activitytracker.app.ui.adaptive

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import io.github.solcott.activitytracker.app.ui.preview.AppPreview
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class AppListRowUiTest {
    @Test
    fun clickOpensAndSelectedIsMarked() = runComposeUiTest {
        var clicks = 0
        setContent {
            AppPreview {
                AppListSection("Privacy & sharing") {
                    AppListRow("Activity defaults", summary = "Followers", onClick = { clicks++ }, selected = true)
                    AppListRow("Privacy zones", onClick = {})
                }
            }
        }
        onNodeWithText("Followers").assertExists()
        onNodeWithText("Activity defaults", useUnmergedTree = false).assertIsSelected().performClick()
        onNodeWithText("Privacy zones").assertIsNotSelected()
        assertEquals(1, clicks)
    }

    @Test
    fun disabledRowDoesNotClick() = runComposeUiTest {
        var clicks = 0
        setContent { AppPreview { AppListRow("Blocked accounts", onClick = { clicks++ }, enabled = false) } }
        onNodeWithText("Blocked accounts").performClick()
        assertEquals(0, clicks)
    }
}
