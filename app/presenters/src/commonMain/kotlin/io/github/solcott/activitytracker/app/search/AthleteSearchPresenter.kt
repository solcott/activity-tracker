package io.github.solcott.activitytracker.app.search

import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.Composable
import com.slack.circuit.codegen.annotations.CircuitInject
import com.slack.circuit.runtime.Navigator
import io.github.solcott.activitytracker.app.CursorPage
import io.github.solcott.activitytracker.app.UiError
import io.github.solcott.activitytracker.app.profile.ProfileScreen
import io.github.solcott.activitytracker.app.rememberCursorPaging
import io.github.solcott.activitytracker.data.di.SessionScope
import io.github.solcott.activitytracker.data.profile.AthleteSearchRepository
import io.github.solcott.activitytracker.lexicon.app.ProfileViewBasic
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.delay

/** How long typing must pause before a search runs, so each keystroke isn't a request. */
internal val SEARCH_DEBOUNCE = 300.milliseconds

@CircuitInject(AthleteSearchScreen::class, SessionScope::class)
@Composable
fun AthleteSearchPresenter(navigator: Navigator, athletes: AthleteSearchRepository): AthleteSearchState {
    // Saveable, so the query (and with it the results) is still there on coming back from an athlete's profile.
    val query = rememberTextFieldState()
    val trimmed = query.text.trim().toString()
    val enabled = trimmed.isNotEmpty()
    val results =
        rememberCursorPaging(trimmed, ProfileViewBasic::did, enabled) { cursor ->
            if (cursor == null) delay(SEARCH_DEBOUNCE)
            // Offset paging can repeat an athlete when the index changes between pages; the paging drops the repeat.
            athletes.search(trimmed, cursor).let { CursorPage(it.actors, it.cursor) }
        }

    return AthleteSearchState(
        query = query,
        results = results.items,
        isLoading = results.isLoading,
        canLoadMore = results.canLoadMore,
        searched = enabled && results.loaded && !results.isLoading,
        error = results.failure?.let { UiError.of(UiError.Kind.SearchAthletes, it) },
    ) { event ->
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
