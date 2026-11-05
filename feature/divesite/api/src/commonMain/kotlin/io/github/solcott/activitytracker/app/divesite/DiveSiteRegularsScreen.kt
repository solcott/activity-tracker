package io.github.solcott.activitytracker.app.divesite

import androidx.compose.runtime.Immutable
import com.slack.circuit.runtime.CircuitUiState
import com.slack.circuit.runtime.screen.Screen
import com.slack.circuit.serialization.CircuitSerializable
import io.github.solcott.activitytracker.data.di.SessionScope
import io.github.solcott.activitytracker.data.divesites.RegularsMeta
import io.github.solcott.activitytracker.lexicon.Did
import io.github.solcott.activitytracker.lexicon.app.DiveSiteRegularEntry
import io.github.solcott.activitytracker.lexicon.app.LeaderboardScope
import io.github.solcott.activitytracker.lexicon.app.RegularsPeriod
import io.github.solcott.uistate.PagedState
import io.github.solcott.uistate.pagedStateOf

/**
 * The Regulars board of the active dive site at [uri] (dive-sites/site-regulars), titled [name]: divers by how many visits they have there.
 */
@CircuitSerializable(SessionScope::class) data class DiveSiteRegularsScreen(val uri: String, val name: String) : Screen

@Immutable
data class DiveSiteRegularsState(
    val name: String,
    /** A [RegularsPeriod] value: the window the board counts. */
    val period: String = RegularsPeriod.ALL_TIME,
    /** A [LeaderboardScope] value. */
    val scope: String = LeaderboardScope.EVERYONE,
    /**
     * The board's entries, most visits first, a page at a time, with the viewer's own entry as its meta; a new choice starts from nothing.
     */
    val entries: PagedState<DiveSiteRegularEntry, RegularsMeta> = pagedStateOf(),
    /** The viewer's own entry, shown apart from [entries] when it isn't among them; null when they aren't on the board. */
    val viewerEntry: DiveSiteRegularEntry? = null,
    val eventSink: (DiveSiteRegularsEvent) -> Unit = {},
) : CircuitUiState

sealed interface DiveSiteRegularsEvent {
    data object Back : DiveSiteRegularsEvent

    /** Shows the board for [period], a [RegularsPeriod] value, from its first page. */
    data class PeriodChanged(val period: String) : DiveSiteRegularsEvent

    /** Shows the board for [scope], a [LeaderboardScope] value, from its first page. */
    data class ScopeChanged(val scope: String) : DiveSiteRegularsEvent

    /** Loads the next page; ignored while a page is loading or when there are no more. */
    data object LoadMore : DiveSiteRegularsEvent

    /** Tries the page that failed to load again. */
    data object Retry : DiveSiteRegularsEvent

    /** Opens an entry's diver's profile. */
    data class OpenAthlete(val did: Did) : DiveSiteRegularsEvent
}
