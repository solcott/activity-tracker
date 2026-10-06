package io.github.solcott.activitytracker.app.search

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.Immutable
import com.slack.circuit.runtime.CircuitUiState
import com.slack.circuit.runtime.screen.Screen
import com.slack.circuit.serialization.CircuitSerializable
import io.github.solcott.activitytracker.app.UiError
import io.github.solcott.activitytracker.data.di.SessionScope
import io.github.solcott.activitytracker.lexicon.Did
import io.github.solcott.activitytracker.lexicon.app.ProfileViewBasic
import kotlinx.collections.immutable.ImmutableList

/** Find other athletes of this app by display name, handle or DID. */
@CircuitSerializable(SessionScope::class) data object AthleteSearchScreen : Screen

@Immutable
data class AthleteSearchState(
    /** The search box. The search runs once typing pauses. */
    val query: TextFieldState,
    /** Matches for the last query searched, which lags [query] while the athlete types. */
    val results: ImmutableList<ProfileViewBasic>,
    val isLoading: Boolean,
    val canLoadMore: Boolean,
    /** Whether the search for the current [query] has finished, so empty [results] means nobody matched. */
    val searched: Boolean,
    val error: UiError?,
    val eventSink: (AthleteSearchEvent) -> Unit,
) : CircuitUiState

sealed interface AthleteSearchEvent {
    data object Back : AthleteSearchEvent

    /** Loads the next page; ignored while a page is loading or when there are no more. */
    data object LoadMore : AthleteSearchEvent

    /** Searches again after an error. */
    data object Retry : AthleteSearchEvent

    data class Open(val did: Did) : AthleteSearchEvent
}
