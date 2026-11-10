package io.github.solcott.activitytracker.app.host

import androidx.compose.runtime.Stable
import com.slack.circuit.runtime.screen.Screen
import io.github.solcott.activitytracker.app.settings.SettingsPage
import io.github.solcott.activitytracker.app.settings.SettingsPane
import io.github.solcott.activitytracker.app.settings.SettingsScreen

/**
 * The least width, in points, at which a native shell shows Settings' list beside the page it opened (ui/settings-layout): under every iPad
 * held upright at full screen (the narrowest is 744), and over half of the widest iPad's screen (683).
 */
const val SETTINGS_SPLIT_MIN_WIDTH = 700

/** The least height, in points, for the same: over every iPhone on its side (440 at most), which shows one page at a time. */
const val SETTINGS_SPLIT_MIN_HEIGHT = 500

/** Whether a native shell's window of this size, in points, has room for Settings' list beside a page. */
fun settingsSplitFits(width: Double, height: Double): Boolean = width >= SETTINGS_SPLIT_MIN_WIDTH && height >= SETTINGS_SPLIT_MIN_HEIGHT

/**
 * How many settings pages are on top of Settings in [stack], listed top screen first: 0 when Settings itself is on top. Null when the top
 * of the stack isn't Settings with only [SettingsPage]s above it, such as a profile opened from the blocked list, which shows full width.
 *
 * The one rule for when Settings can lay its list beside a page, which the Compose shell's decoration and the presenter host both ask.
 */
fun <T> settingsPagesOnTop(stack: List<T>, screenOf: (T) -> Screen): Int? {
    val pages = stack.takeWhile { screenOf(it) is SettingsPage }.size
    return pages.takeIf { stack.getOrNull(it)?.let(screenOf) is SettingsScreen }
}

/**
 * Settings laid out as its list beside a page, for a UI that reported a size with room for it (`TabStack.setSize`): which record is the
 * list and which page shows beside it. The stack holds the same records either way; this only says how to show its top
 * (dev/presenter-host).
 */
@Stable
class SettingsSplit
private constructor(
    /** Settings itself: the list. */
    val settings: HostRecord,
    /** What shows beside the list: the top settings page, or [HostRecord.paneDefault] until a row is chosen. */
    val page: HostRecord,
    /** The first page opened beside the list, whose row it marks and which has no back button; [page] unless a deeper page is open. */
    internal val first: HostRecord,
) {
    /** The layout, as the list reads it to mark the row of the page shown. */
    val pane: SettingsPane = SettingsPane(twoPane = true, shownPage = first.screen as? SettingsPage)

    internal companion object {
        /** The split for a stack's [records], first screen first, or null when its top isn't Settings and its pages. */
        fun of(records: List<HostRecord>): SettingsSplit? {
            val pages = settingsPagesOnTop(records.asReversed()) { it.screen } ?: return null
            val settings = records[records.lastIndex - pages]
            // Until a row is chosen the page beside the list is the one Settings brought with it, which is then also the first.
            return settings.paneDefault?.let { default ->
                if (pages == 0) SettingsSplit(settings, default, default)
                else SettingsSplit(settings, page = records.last(), first = records[records.size - pages])
            }
        }
    }
}
