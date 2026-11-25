package io.github.solcott.activitytracker.app.detail

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.slack.circuit.retained.rememberRetained
import com.slack.circuit.runtime.Navigator
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.toImmutableSet

/**
 * How the viewer has the activity page laid out (feed/activity-page-layout): which cards they opened or closed, and whether All data shows
 * in the side pane. Retained, so coming back to the page keeps it.
 */
@Stable
internal class PageView {
    private var opened by mutableStateOf<Set<DetailSection>?>(null)

    var allData by mutableStateOf<DataSection?>(null)
        private set

    /** The cards open, or null while only the lead is. */
    val openCards: ImmutableSet<DetailSection>?
        get() = opened?.toImmutableSet()

    /** [event] on the page whose lead card is [lead], at [uri]: a card toggled, or All data opened or closed. */
    fun on(event: ActivityDetailEvent, lead: DetailSection?, uri: String, navigator: Navigator) {
        when (event) {
            is ActivityDetailEvent.ToggleCard -> {
                val open = opened ?: setOfNotNull(lead)
                opened = if (event.section in open) open - event.section else open + event.section
            }
            is ActivityDetailEvent.OpenAllData ->
                if (event.inPane) allData = event.section ?: DataSection.Stats
                else {
                    // The window has no side pane now: one left open from a wider window mustn't come back when it widens again.
                    allData = null
                    navigator.goTo(ActivityDataScreen(uri, event.section?.name))
                }
            ActivityDetailEvent.CloseAllData -> allData = null
            else -> {}
        }
    }
}

@Composable internal fun rememberPageView(): PageView = rememberRetained { PageView() }
