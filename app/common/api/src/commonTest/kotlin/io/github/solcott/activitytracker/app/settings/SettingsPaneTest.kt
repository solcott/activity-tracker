package io.github.solcott.activitytracker.app.settings

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Which row Settings' list marks (ui/settings-layout): the shown page's, and only beside it. */
class SettingsPaneTest {
    private data object Zones : SettingsPage

    private data object Gear : SettingsPage

    /** Two pages of one class, as the blocked and muted lists are. */
    private data class Accounts(val kind: String) : SettingsPage

    @Test
    fun theShownPagesRowIsMarkedBesideIt() {
        val pane = SettingsPane(twoPane = true, shownPage = Zones)

        assertTrue(pane.marks(Zones))
        assertFalse(pane.marks(Gear))
    }

    @Test
    fun pagesOfOneClassAreToldApart() {
        val pane = SettingsPane(twoPane = true, shownPage = Accounts("blocked"))

        assertTrue(pane.marks(Accounts("blocked")))
        assertFalse(pane.marks(Accounts("muted")))
    }

    @Test
    fun nothingIsMarkedOnePageAtATime() {
        assertFalse(SettingsPane.Single.marks(Zones))
        assertFalse(SettingsPane(twoPane = false, shownPage = Zones).marks(Zones), "a page that replaced the list has no row to mark")
    }
}
