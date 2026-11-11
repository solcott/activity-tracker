package io.github.solcott.activitytracker.app.host

import androidx.compose.runtime.Stable
import com.slack.circuit.runtime.Navigator
import com.slack.circuit.runtime.navigation.NavStackList
import com.slack.circuit.runtime.screen.PopResult
import com.slack.circuit.runtime.screen.Screen
import io.github.solcott.activitytracker.app.nav.TopLevelDestination
import io.github.solcott.activitytracker.app.settings.ActivityDefaultsScreen
import io.github.solcott.activitytracker.app.settings.SettingsScreen
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * One tab's stack of screens, and the [Navigator] its presenters drive (dev/presenter-host). The host owns the stack; a UI shows [records]
 * and reports only what the athlete did to it: going back, with [popTo].
 *
 * Main thread only, like the presenters that call it.
 */
@Stable
@Suppress("TooManyFunctions") // Navigator's nine, and beside them what a UI reports (going back, its width) and how a record is made.
class TabStack
internal constructor(
    val destination: TopLevelDestination,
    private val root: Screen,
    private val presenters: Presenters,
    private val keys: RecordKeys,
) : Navigator {
    /** The stack, first screen first. Never empty. */
    val records: StateFlow<List<HostRecord>>
        field = MutableStateFlow(listOf(record(root, isRoot = true)))

    /** Whether the UI has room to show Settings' list beside a page; see [setSize]. */
    private val wide = MutableStateFlow(false)

    /**
     * What a UI shows: [records], and, for one that reported itself wide enough, Settings laid out beside its page when that is the top of
     * the stack (ui/settings-layout). Wrapped in an object for Swift (see `SwiftFlows.kt`).
     */
    val shown: StateFlow<ShownRecords>
        field = MutableStateFlow(shownNow())

    private fun shownNow(): ShownRecords = ShownRecords(records.value, if (wide.value) SettingsSplit.of(records.value) else null)

    /** Replaces the stack, and with it what a UI shows. */
    private fun show(records: List<HostRecord>) {
        this.records.value = records
        shown.value = shownNow()
    }

    /**
     * Tells the stack the size of the UI showing it, in points: with room for it ([settingsSplitFits]), [shown] lays Settings out beside
     * its page. The records never change with it.
     */
    fun setSize(width: Double, height: Double) {
        val wide = settingsSplitFits(width, height)
        if (wide != this.wide.value) {
            this.wide.value = wide
            shown.value = shownNow()
        }
    }

    private fun record(screen: Screen, isRoot: Boolean): HostRecord {
        val key = keys.next()
        return HostRecord(
            key = key,
            screen = screen,
            isRoot = isRoot,
            showsTabs = TopLevelDestination.showsNavigation(screen),
            presenter = presenters.create(screen, this),
            presenters = presenters,
            // Settings shows Activity defaults beside its list until a row is chosen, without it being on the stack.
            paneDefault = if (screen is SettingsScreen) beside(ActivityDefaultsScreen) else null,
            paneRoot = paneRoot(key),
        )
    }

    /** A record shown beside one of the stack's, never in it; it navigates this stack like any other. */
    private fun beside(screen: Screen): HostRecord {
        val key = keys.next()
        return HostRecord(
            key,
            screen,
            isRoot = false,
            showsTabs = true,
            presenter = presenters.create(screen, this),
            presenters = presenters,
            paneRoot = paneRoot(key),
        )
    }

    /** Whether the record with [key] is the first page beside Settings' list. Asks [shown] late: the first record is made before it. */
    private fun paneRoot(key: String): StateFlow<Boolean> = mapState({ shown }) { it.split?.first?.key == key }

    /** Puts [screen] on top, unless the screen on top equals it (Circuit's rule, which makes a double tap open one screen). */
    override fun goTo(screen: Screen): Boolean {
        if (records.value.last().screen == screen) return false
        show(records.value + record(screen, isRoot = false))
        return true
    }

    /**
     * Removes the top screen and returns it. The first screen stays: popping it does nothing. [result] is ignored; no screen passes one.
     */
    override fun pop(result: PopResult?): Screen? {
        val current = records.value
        if (current.size == 1) return null
        show(current.dropLast(1))
        return current.last().screen
    }

    /**
     * Shortens the stack to its first [size] screens: what the UI calls when the athlete went back by a gesture or the system's back menu,
     * which can close several screens at once. Never empties the stack and never lengthens it.
     */
    fun popTo(size: Int) {
        val current = records.value
        val keep = size.coerceIn(1, current.size)
        if (keep < current.size) show(current.take(keep))
    }

    override fun peek(): Screen? = records.value.last().screen

    /** Top screen first, as Circuit's own navigator answers. */
    override fun peekBackStack(): List<Screen> = records.value.asReversed().map { it.screen }

    /** The stack has no forward history, so there is nothing a [NavStackList] would add. */
    override fun peekNavStack(): NavStackList<Screen>? = null

    override fun forward(): Boolean = false

    override fun backward(): Boolean = false

    /**
     * Replaces the whole stack with [newRoot]. [options] are ignored: tabs are switched by the host (`SessionHost.select`), not by saving
     * and restoring one stack as the Compose shell does, and no presenter calls this.
     */
    override fun resetRoot(newRoot: Screen, options: Navigator.StateOptions): List<Screen> {
        val old = peekBackStack()
        show(listOf(record(newRoot, isRoot = true)))
        return old
    }

    /** Back to the tab's own first screen, with nothing kept from before. */
    internal fun reset() {
        resetRoot(root, Navigator.StateOptions.Default)
    }
}
