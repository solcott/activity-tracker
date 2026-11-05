package io.github.solcott.activitytracker.app.search

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.plus
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.slack.circuit.codegen.annotations.CircuitInject
import com.slack.circuit.foundation.CircuitContent
import com.slack.circuit.runtime.Navigator
import com.slack.circuit.runtime.screen.PopResult
import com.slack.circuit.runtime.screen.Screen
import io.github.solcott.activitytracker.app.UiError
import io.github.solcott.activitytracker.app.segment.SegmentBrowseScreen
import io.github.solcott.activitytracker.app.ui.AccountRow
import io.github.solcott.activitytracker.app.ui.LoadMoreAtEnd
import io.github.solcott.activitytracker.app.ui.OpenNewResultsAtTop
import io.github.solcott.activitytracker.app.ui.SearchField
import io.github.solcott.activitytracker.app.ui.adaptive.AppScaffold
import io.github.solcott.activitytracker.app.ui.adaptive.AppSegmentedControl
import io.github.solcott.activitytracker.app.ui.adaptive.AppTopBar
import io.github.solcott.activitytracker.app.ui.adaptive.ReadableList
import io.github.solcott.activitytracker.app.ui.adaptive.readableWidth
import io.github.solcott.activitytracker.app.ui.adaptive.rememberAppTopBarScrollBehavior
import io.github.solcott.activitytracker.app.ui.pagedRows
import io.github.solcott.activitytracker.composeApp.resources.Res
import io.github.solcott.activitytracker.composeApp.resources.search_label
import io.github.solcott.activitytracker.composeApp.resources.search_mode_athletes
import io.github.solcott.activitytracker.composeApp.resources.search_mode_segments
import io.github.solcott.activitytracker.composeApp.resources.search_no_results
import io.github.solcott.activitytracker.composeApp.resources.search_prompt
import io.github.solcott.activitytracker.composeApp.resources.search_tab_title
import io.github.solcott.activitytracker.composeApp.resources.search_title
import io.github.solcott.activitytracker.data.di.SessionScope
import kotlinx.collections.immutable.persistentListOf
import org.jetbrains.compose.resources.stringResource

@CircuitInject(AthleteSearchScreen::class, SessionScope::class)
@Composable
fun AthleteSearchUi(state: AthleteSearchState, modifier: Modifier = Modifier) {
    val listState = rememberLazyListState()
    LoadMoreAtEnd(listState, state.results.canLoadMore) { state.eventSink(AthleteSearchEvent.LoadMore) }
    OpenNewResultsAtTop(listState, state.results.items.firstOrNull()?.did?.value)

    val scrollBehavior = rememberAppTopBarScrollBehavior()
    AppScaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            AppTopBar(
                title = stringResource(if (state.mode == SearchMode.Segments) Res.string.search_tab_title else Res.string.search_title),
                onBack = { state.eventSink(AthleteSearchEvent.Back) },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            AppSegmentedControl(
                persistentListOf(stringResource(Res.string.search_mode_athletes), stringResource(Res.string.search_mode_segments)),
                selected = state.mode.ordinal,
                onSelect = { state.eventSink(AthleteSearchEvent.ModeChanged(SearchMode.entries[it])) },
                modifier = Modifier.readableWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            )
            if (state.mode == SearchMode.Segments) {
                SegmentsMode(state, Modifier.fillMaxSize())
                return@AppScaffold
            }
            SearchField(
                state.query,
                stringResource(Res.string.search_label),
                Modifier.readableWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                autoFocus = true,
            )
            ReadableList(listState, Modifier.fillMaxSize(), minSidePadding = 0.dp) { listPadding ->
                LazyColumn(
                    Modifier.fillMaxSize(),
                    state = listState,
                    contentPadding = listPadding + PaddingValues(bottom = 8.dp),
                ) {
                    pagedRows(
                        state.results,
                        UiError.Kind.SearchAthletes,
                        onRetry = { state.eventSink(AthleteSearchEvent.Retry) },
                        key = { it.did.value },
                        empty = state.empty(),
                    ) { account ->
                        AccountRow(account, onClick = { state.eventSink(AthleteSearchEvent.Open(account.did)) })
                    }
                    if (state.query.text.isBlank()) item { Message(stringResource(Res.string.search_prompt)) }
                }
            }
        }
    }
}

/**
 * The segment browse screen, in place of athlete search. What it opens goes on this tab's back stack, through this screen's presenter, as
 * if it were the tab's own screen.
 */
@Composable
private fun SegmentsMode(state: AthleteSearchState, modifier: Modifier = Modifier) {
    // The sink is a new lambda on each of the tab presenter's recompositions; reading the latest keeps the navigator, and with it the
    // browse presenter CircuitContent keys on it, the same.
    val sink by rememberUpdatedState(state.eventSink)
    val navigator = remember {
        object : Navigator by Navigator.NoOp {
            override fun goTo(screen: Screen): Boolean {
                sink(AthleteSearchEvent.GoTo(screen))
                return true
            }

            override fun pop(result: PopResult?): Screen? {
                sink(AthleteSearchEvent.Back)
                return null
            }
        }
    }
    CircuitContent(SegmentBrowseScreen, modifier = modifier, navigator = navigator)
}

/** What the results say when empty: that nothing matched; none before a query, which shows the prompt instead. */
private fun AthleteSearchState.empty(): (@Composable () -> Unit)? =
    if (query.text.isBlank()) null
    else {
        { Message(stringResource(Res.string.search_no_results, query.text.trim().toString())) }
    }

@Composable
private fun Message(text: String, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
        Text(text, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
