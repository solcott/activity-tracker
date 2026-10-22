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

/** Find other athletes of this app by display name, handle or DID. */
@CircuitSerializable(SessionScope::class) data object AthleteSearchScreen : Screen

@Immutable
data class AthleteSearchState(
    /** The search box. The search runs once typing pauses. */
    val query: TextFieldState,
    /**
     * Matches for the last query searched, which lag [query] while the athlete types: kept under a refresh until the new query's first page
     * answers (ui/content-loading). Nothing loaded and nothing loading while the query is blank.
     */
    val results: PagedState<ProfileViewBasic, Unit>,
    val eventSink: (AthleteSearchEvent) -> Unit,
) : CircuitUiState

sealed interface AthleteSearchEvent {
    data object Back : AthleteSearchEvent

    /** Loads the next page; ignored while a page is loading, after a failed page, or when there are no more. */
    data object LoadMore : AthleteSearchEvent

    /** Loads again what failed to load, and only that: the search, or the page of results that failed. */
    data object Retry : AthleteSearchEvent

    data class Open(val did: Did) : AthleteSearchEvent
}
