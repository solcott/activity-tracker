package io.github.solcott.activitytracker.app.settings

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.v2.runComposeUiTest
import io.github.solcott.activitytracker.app.ui.TestTags
import io.github.solcott.activitytracker.app.ui.preview.AppPreview
import kotlin.test.Test
import kotlin.test.assertEquals

/** Settings' Activity defaults page (ui/settings-layout). */
@OptIn(ExperimentalTestApi::class)
class ActivityDefaultsUiTest {
    @Test
    fun showsTheDefaultsAndGoesBack() = runComposeUiTest {
        val events = mutableListOf<ActivityDefaultsEvent>()
        setContent { AppPreview { ActivityDefaultsUi(ActivityDefaultsSamples.default.copy(eventSink = { events += it })) } }
        onNodeWithText("Activity defaults").assertExists()
        onNodeWithText("Who can see new activities").assertExists()
        onNodeWithTag(TestTags.BACK, useUnmergedTree = true).performClick()
        assertEquals(listOf<ActivityDefaultsEvent>(ActivityDefaultsEvent.Back), events)
    }

    @Test
    fun aFailedSaveIsReportedOnThePage() = runComposeUiTest {
        setContent { AppPreview { ActivityDefaultsUi(ActivityDefaultsSamples.all.getValue("saveFailed")) } }
        onNodeWithText("Couldn't save settings", substring = true).performScrollTo().assertExists()
    }
}
