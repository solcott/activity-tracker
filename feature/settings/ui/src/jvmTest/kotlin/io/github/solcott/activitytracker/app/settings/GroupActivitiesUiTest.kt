package io.github.solcott.activitytracker.app.settings

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.v2.runComposeUiTest
import io.github.solcott.activitytracker.app.ui.preview.AppPreview
import io.github.solcott.activitytracker.data.segments.GroupChoice
import kotlin.test.Test
import kotlin.test.assertEquals

/** Settings' group activities choice (feed/group-activities). */
@OptIn(ExperimentalTestApi::class)
class GroupActivitiesUiTest {
    @Test
    fun pickingAChoiceSendsIt() = runComposeUiTest {
        val events = mutableListOf<SettingsEvent>()
        setContent { AppPreview { SettingsUi(SettingsSamples.default.copy(eventSink = { events += it })) } }
        onNodeWithText("Group activities").performScrollTo().assertExists()
        onNodeWithText("Links your activities with others'", substring = true).assertExists()
        onNodeWithText("Anyone").performScrollTo().performClick()
        assertEquals(SettingsEvent.GroupActivitiesChanged(GroupChoice.ANYONE), events.last())
    }

    @Test
    fun anOldSignInIsToldToSignInAgain() = runComposeUiTest {
        setContent { AppPreview { SettingsUi(SettingsSamples.default.copy(canUseGroups = false)) } }
        onNodeWithText("Sign in again to use group activities.").performScrollTo().assertExists()
        onNodeWithText("Anyone").assertIsNotEnabled()
    }
}
