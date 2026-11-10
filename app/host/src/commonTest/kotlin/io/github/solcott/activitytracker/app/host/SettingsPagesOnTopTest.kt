package io.github.solcott.activitytracker.app.host

import com.slack.circuit.runtime.screen.Screen
import io.github.solcott.activitytracker.app.profile.BlockListKind
import io.github.solcott.activitytracker.app.profile.BlockListScreen
import io.github.solcott.activitytracker.app.profile.ProfileScreen
import io.github.solcott.activitytracker.app.settings.PrivacyZonesScreen
import io.github.solcott.activitytracker.app.settings.SettingsScreen
import io.github.solcott.activitytracker.app.settings.SportZonesScreen
import io.github.solcott.activitytracker.app.settings.TrainingZonesScreen
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** When Settings can show its list beside a page (ui/settings-layout): the rule both shells ask. Stacks are listed top screen first. */
class SettingsPagesOnTopTest {
    private val profile = ProfileScreen("did:plc:alice")

    private fun pages(vararg stack: Screen) = settingsPagesOnTop(stack.toList()) { it }

    @Test fun settingsAloneHasNoPageAboveIt() = assertEquals(0, pages(SettingsScreen, profile))

    @Test
    fun settingsPagesAboveSettingsAreCounted() {
        assertEquals(1, pages(PrivacyZonesScreen, SettingsScreen, profile))
        assertEquals(2, pages(SportZonesScreen("run"), TrainingZonesScreen, SettingsScreen, profile))
    }

    @Test
    fun anyOtherScreenOnTopShowsFullWidth() =
        assertNull(pages(ProfileScreen("did:plc:bob"), BlockListScreen(BlockListKind.Blocked), SettingsScreen, profile))

    @Test fun aStackWithoutSettingsHasNothingToSplit() = assertNull(pages(profile))

    @Test
    fun aSettingsPageWithNoSettingsBeneathIsAnOrdinaryScreen() {
        // The gear list opened from the profile, not from Settings.
        assertNull(pages(PrivacyZonesScreen, profile))
        assertNull(pages(PrivacyZonesScreen))
    }

    @Test fun anEmptyStackHasNothingToSplit() = assertNull(pages())
}
