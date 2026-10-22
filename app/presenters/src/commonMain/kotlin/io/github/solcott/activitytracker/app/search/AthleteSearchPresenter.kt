package io.github.solcott.activitytracker.app.search

import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.Composable
import com.slack.circuit.codegen.annotations.CircuitInject
import com.slack.circuit.runtime.Navigator
import io.github.solcott.activitytracker.app.profile.ProfileScreen
import io.github.solcott.activitytracker.data.di.SessionScope
import io.github.solcott.activitytracker.data.profile.AthleteSearchRepository
import io.github.solcott.activitytracker.lexicon.app.ProfileViewBasic
import io.github.solcott.uistate.OnKeyChange
import io.github.solcott.uistate.circuit.rememberRetainedPaging
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flow

/** How long typing must pause before a search runs, so each keystroke isn't a request. */
internal val SEARCH_DEBOUNCE = 300.milliseconds

@CircuitInject(AthleteSearchScreen::class, SessionScope::class)
@Composable
fun AthleteSearchPresenter(navigator: Navigator, athletes: AthleteSearchRepository): AthleteSearchState {
    // Saveable, so the query (and with it the results) is still there on coming back from an athlete's profile.
    val query = rememberTextFieldState()
    val trimmed = query.text.trim().toString()
    val enabled = trimmed.isNotEmpty()
    // A new query keeps the last results until its first page answers; the debounce is part of that first page, so typing again cancels it.
    val results =
        rememberRetainedPaging(trimmed, idOf = ProfileViewBasic::did, onKeyChange = OnKeyChange.KeepItems, enabled = enabled) { cursor ->
            flow {
                if (cursor == null) delay(SEARCH_DEBOUNCE)
                // Offset paging can repeat an athlete when the index changes between pages; the paging drops the repeat.
                emit(athletes.searchPage(trimmed, cursor))
            }
        }

    return AthleteSearchState(query = query, results = results.state) { event ->
        when (event) {
            AthleteSearchEvent.Back -> {
                navigator.pop()
            }

            AthleteSearchEvent.LoadMore -> {
                results.loadMore()
            }

            AthleteSearchEvent.Retry -> {
                results.retry()
            }

            is AthleteSearchEvent.Open -> {
                navigator.goTo(ProfileScreen(event.did.value))
            }
        }
    }
}
