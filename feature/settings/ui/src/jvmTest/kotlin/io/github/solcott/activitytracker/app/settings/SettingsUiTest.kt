package io.github.solcott.activitytracker.app.settings

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.v2.runComposeUiTest
import io.github.solcott.activitytracker.app.gear.GearListScreen
import io.github.solcott.activitytracker.app.ui.preview.AppPreview
import io.github.solcott.activitytracker.app.ui.theme.UiPlatform
import kotlin.test.Test
import kotlin.test.assertEquals

/** Settings' grouped list (ui/settings-layout). */
@OptIn(ExperimentalTestApi::class)
class SettingsUiTest {
    @Test
    fun sectionsComeInOrderWithNoModerationForOthers() = runComposeUiTest {
        setContent { AppPreview(platform = UiPlatform.Desktop) { SettingsUi(SettingsSamples.default) } }
        // The column lays out every section, so each has a place even below the fold.
        val tops =
            listOf("Account", "General", "Privacy & sharing", "Training", "Safety").map { text ->
                onNodeWithText(text, useUnmergedTree = true).fetchSemanticsNode().positionInRoot.y
            }
        assertEquals(tops.sorted(), tops)
        onNodeWithText("Moderation").assertDoesNotExist()
        onNodeWithText("Sign out").assertDoesNotExist()
    }

    @Test
    fun theAccountRowShowsTheHandleAndOpensTheAccountPage() = runComposeUiTest {
        val events = mutableListOf<SettingsEvent>()
        setContent { AppPreview { SettingsUi(SettingsSamples.default.copy(eventSink = { events += it })) } }
        onNodeWithText("Sign in again to use everything").assertDoesNotExist()
        onNodeWithText("alice.test").performClick()
        assertEquals(SettingsEvent.Open(AccountScreen), events.last())
    }

    @Test
    fun theAccountRowSaysWhenTheSignInNeedsRenewing() = runComposeUiTest {
        setContent { AppPreview { SettingsUi(SettingsSamples.all.getValue("needsSignIn")) } }
        onNodeWithText("Sign in again to use everything").assertExists()
    }

    @Test
    fun rowsSummarizeAndOpenTheirPages() = runComposeUiTest {
        val events = mutableListOf<SettingsEvent>()
        setContent { AppPreview { SettingsUi(SettingsSamples.default.copy(eventSink = { events += it })) } }
        onNodeWithText("Followers").assertExists()
        onNodeWithText("1 zone, start and end hidden").assertExists()
        onNodeWithText("Matching on").assertExists()
        onNodeWithText("Gear").performScrollTo().performClick()
        assertEquals(SettingsEvent.Open(GearListScreen), events.last())
    }

    @Test
    fun unreadSummariesAreLeftOut() = runComposeUiTest {
        setContent { AppPreview { SettingsUi(SettingsSamples.all.getValue("loading")) } }
        onNodeWithText("Matching on").assertDoesNotExist()
        onNodeWithText("Matching off").assertDoesNotExist()
    }

    @Test
    fun moderatorsOnDesktopSeeModeration() = runComposeUiTest {
        setContent { AppPreview(platform = UiPlatform.Desktop) { SettingsUi(SettingsSamples.all.getValue("moderator")) } }
        onNodeWithText("Moderation").performScrollTo().assertExists()
    }

    @Test
    fun theShownPagesRowIsMarkedBesideIt() = runComposeUiTest {
        setContent {
            AppPreview {
                CompositionLocalProvider(LocalSettingsPane provides SettingsPane(twoPane = true, shownPage = SegmentsAndGroupsScreen)) {
                    SettingsUi(SettingsSamples.default)
                }
            }
        }
        onNodeWithText("Segments & groups").assertIsSelected()
        onNodeWithText("Activity defaults").assertIsNotSelected()
    }
}
