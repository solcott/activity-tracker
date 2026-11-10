package io.github.solcott.activitytracker.app.host

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** ui/settings-layout "iOS decides wide and narrow by the window's size in points": the windows its scenarios name. */
class SettingsSplitFitsTest {
    @Test
    fun everyIpadAtFullScreenFitsEitherWayUp() {
        // iPad mini, iPad (A16), 11-inch and 13-inch, upright and on their sides.
        listOf(744.0 to 1_133.0, 820.0 to 1_180.0, 834.0 to 1_210.0, 1_032.0 to 1_376.0).forEach { (short, long) ->
            assertTrue(settingsSplitFits(short, long), "$short by $long")
            assertTrue(settingsSplitFits(long, short), "$long by $short")
        }
    }

    @Test
    fun anIpadSharingItsScreenDoesNot() {
        assertFalse(settingsSplitFits(605.0, 834.0), "half of an 11-inch iPad on its side")
        assertFalse(settingsSplitFits(683.0, 1_032.0), "half of a 13-inch iPad on its side")
        assertFalse(settingsSplitFits(320.0, 1_210.0), "a third of an upright iPad")
    }

    @Test
    fun anIphoneNeverDoes() {
        assertFalse(settingsSplitFits(440.0, 956.0), "the widest iPhone, upright")
        assertFalse(settingsSplitFits(956.0, 440.0), "the widest iPhone, on its side")
        assertFalse(settingsSplitFits(874.0, 402.0))
    }

    @Test
    fun theLeastWindowThatFits() {
        assertTrue(settingsSplitFits(700.0, 500.0))
        assertFalse(settingsSplitFits(699.0, 500.0))
        assertFalse(settingsSplitFits(700.0, 499.0))
    }
}
