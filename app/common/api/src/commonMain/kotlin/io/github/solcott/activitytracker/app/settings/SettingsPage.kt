package io.github.solcott.activitytracker.app.settings

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.compositionLocalOf
import com.slack.circuit.runtime.screen.Screen

/**
 * A page of Settings (ui/settings-layout): on a wide window it opens in the detail pane beside the Settings list, where any other screen
 * opens full width. Settings' own pages carry it, and so do the gear list and the blocked and muted lists it opens.
 */
interface SettingsPage : Screen

/**
 * How Settings is laid out right now: [twoPane] while its list shows beside a detail pane, and [shownPage] the page that pane shows (the
 * first of the settings pages on the stack, or the one shown until a row is chosen). Narrow, it's [Single].
 */
@Immutable
data class SettingsPane(val twoPane: Boolean, val shownPage: SettingsPage?) {
    companion object {
        val Single = SettingsPane(twoPane = false, shownPage = null)
    }
}

/** The layout around Settings, which the navigation shell provides; the list marks [SettingsPane.shownPage]'s row. */
val LocalSettingsPane = compositionLocalOf { SettingsPane.Single }
