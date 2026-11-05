package io.github.solcott.activitytracker.app.search

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.Immutable
import com.slack.circuit.runtime.CircuitUiState
import com.slack.circuit.runtime.screen.Screen
import com.slack.circuit.serialization.CircuitSerializable
import io.github.solcott.activitytracker.data.di.SessionScope
import io.github.solcott.activitytracker.lexicon.Did
import io.github.solcott.activitytracker.lexicon.app.ProfileViewBasic
import io.github.solcott.uistate.PagedState

/**
 * The Search tab: find other athletes of this app by display name, handle or DID, or, in [SearchMode.Segments], browse and search segments
 * (segments/segment-browse).
 */
@CircuitSerializable(SessionScope::class) data object AthleteSearchScreen : Screen

/** What the Search tab finds. */
enum class SearchMode {
    Athletes,
    Segments,
}

@Immutable
data class AthleteSearchState(
    /** The search box. The search runs once typing pauses. */
    val query: TextFieldState,
    /**
     * Matches for the last query searched, which lag [query] while the athlete types: kept under a refresh until the new query's first page
     * answers (ui/content-loading). Nothing loaded and nothing loading while the query is blank.
     */
    val results: PagedState<ProfileViewBasic, Unit>,
    /** Athletes, or segments, which the tab shows as the segment browse screen. Kept while the athlete visits other tabs. */
    val mode: SearchMode = SearchMode.Athletes,
    val eventSink: (AthleteSearchEvent) -> Unit,
) : CircuitUiState

sealed interface AthleteSearchEvent {
    data object Back : AthleteSearchEvent

    /** Loads the next page; ignored while a page is loading, after a failed page, or when there are no more. */
    data object LoadMore : AthleteSearchEvent

    /** Loads again what failed to load, and only that: the search, or the page of results that failed. */
    data object Retry : AthleteSearchEvent

    data class Open(val did: Did) : AthleteSearchEvent

    data class ModeChanged(val mode: SearchMode) : AthleteSearchEvent

    /** The segment browse screen shown in Segments mode opens [screen] in the tab, as it would on its own. */
    data class GoTo(val screen: Screen) : AthleteSearchEvent
}
