package io.github.solcott.activitytracker.app.search

import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import com.slack.circuit.codegen.annotations.CircuitInject
import com.slack.circuit.runtime.Navigator
import io.github.solcott.activitytracker.app.UiError
import io.github.solcott.activitytracker.app.profile.ProfileScreen
import io.github.solcott.activitytracker.atproto.runSuspendCatching
import io.github.solcott.activitytracker.data.di.SessionScope
import io.github.solcott.activitytracker.data.profile.AthleteSearchRepository
import io.github.solcott.activitytracker.lexicon.app.ProfileViewBasic
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** How long typing must pause before a search runs, so each keystroke isn't a request. */
internal val SEARCH_DEBOUNCE = 300.milliseconds

@CircuitInject(AthleteSearchScreen::class, SessionScope::class)
@Composable
fun AthleteSearchPresenter(navigator: Navigator, athletes: AthleteSearchRepository): AthleteSearchState {
    // Saveable, so the query (and with it the results) is still there on coming back from an athlete's profile.
    val query = rememberTextFieldState()
    var results by remember { mutableStateOf<ImmutableList<ProfileViewBasic>>(persistentListOf()) }
    var cursor by remember { mutableStateOf<String?>(null) }
    var searchedFor by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<UiError?>(null) }
    var retryKey by remember { mutableIntStateOf(0) }
    var nextPage by remember { mutableStateOf<Job?>(null) }
    val scope = rememberCoroutineScope()
    val trimmed = query.text.trim().toString()

    LaunchedEffect(trimmed, retryKey) {
        // A page of the previous query must not land in this one's results.
        nextPage?.cancel()
        error = null
        if (trimmed.isEmpty()) {
            results = persistentListOf()
            cursor = null
            searchedFor = null
            isLoading = false
            return@LaunchedEffect
        }
        isLoading = true
        delay(SEARCH_DEBOUNCE)
        runSuspendCatching { athletes.search(trimmed) }
            .onSuccess {
                results = it.actors.toImmutableList()
                cursor = it.cursor
            }
            .onFailure {
                results = persistentListOf()
                cursor = null
                error = UiError.of(UiError.Kind.SearchAthletes, it)
            }
        searchedFor = trimmed
        isLoading = false
    }

    return AthleteSearchState(
        query = query,
        results = results,
        isLoading = isLoading,
        canLoadMore = cursor != null && error == null,
        searched = searchedFor == trimmed && trimmed.isNotEmpty() && !isLoading,
        error = error,
    ) { event ->
        when (event) {
            AthleteSearchEvent.Back -> {
                navigator.pop()
            }

            AthleteSearchEvent.LoadMore -> {
                val after = cursor
                val searched = searchedFor
                // A failed page clears the cursor, so there is no LoadMore after an error.
                if (!isLoading && after != null && searched != null) {
                    isLoading = true
                    nextPage = scope.launch {
                        runSuspendCatching { athletes.search(searched, after) }
                            .onSuccess { page ->
                                // Offset paging can repeat an athlete when the index changes between pages.
                                results = (results + page.actors).distinctBy { it.did }.toImmutableList()
                                cursor = page.cursor
                            }
                            .onFailure {
                                // Retry starts over from the first page.
                                cursor = null
                                error = UiError.of(UiError.Kind.SearchAthletes, it)
                            }
                        isLoading = false
                    }
                }
            }

            AthleteSearchEvent.Retry -> {
                retryKey++
            }

            is AthleteSearchEvent.Open -> {
                navigator.goTo(ProfileScreen(event.did.value))
            }
        }
    }
}
