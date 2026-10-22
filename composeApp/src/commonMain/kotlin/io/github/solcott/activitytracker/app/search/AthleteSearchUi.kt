package io.github.solcott.activitytracker.app.search

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.plus
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.clearText
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.slack.circuit.codegen.annotations.CircuitInject
import io.github.solcott.activitytracker.app.UiError
import io.github.solcott.activitytracker.app.ui.AccountRow
import io.github.solcott.activitytracker.app.ui.LoadMoreAtEnd
import io.github.solcott.activitytracker.app.ui.adaptive.AppIcon
import io.github.solcott.activitytracker.app.ui.adaptive.AppIconButton
import io.github.solcott.activitytracker.app.ui.adaptive.AppScaffold
import io.github.solcott.activitytracker.app.ui.adaptive.AppTextField
import io.github.solcott.activitytracker.app.ui.adaptive.AppTopBar
import io.github.solcott.activitytracker.app.ui.adaptive.PlatformIcon
import io.github.solcott.activitytracker.app.ui.adaptive.ReadableList
import io.github.solcott.activitytracker.app.ui.adaptive.readableWidth
import io.github.solcott.activitytracker.app.ui.adaptive.rememberAppTopBarScrollBehavior
import io.github.solcott.activitytracker.app.ui.pagedFooter
import io.github.solcott.activitytracker.app.ui.pagedRefreshStatus
import io.github.solcott.activitytracker.composeApp.resources.Res
import io.github.solcott.activitytracker.composeApp.resources.search_clear
import io.github.solcott.activitytracker.composeApp.resources.search_label
import io.github.solcott.activitytracker.composeApp.resources.search_no_results
import io.github.solcott.activitytracker.composeApp.resources.search_prompt
import io.github.solcott.activitytracker.composeApp.resources.search_title
import io.github.solcott.activitytracker.data.di.SessionScope
import org.jetbrains.compose.resources.stringResource

@CircuitInject(AthleteSearchScreen::class, SessionScope::class)
@Composable
fun AthleteSearchUi(state: AthleteSearchState, modifier: Modifier = Modifier) {
    val listState = rememberLazyListState()
    LoadMoreAtEnd(listState, state.results.canLoadMore) { state.eventSink(AthleteSearchEvent.LoadMore) }

    val scrollBehavior = rememberAppTopBarScrollBehavior()
    AppScaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            AppTopBar(
                title = stringResource(Res.string.search_title),
                onBack = { state.eventSink(AthleteSearchEvent.Back) },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            SearchField(state.query, Modifier.readableWidth().padding(horizontal = 16.dp, vertical = 8.dp))
            ReadableList(listState, Modifier.fillMaxSize(), minSidePadding = 0.dp) { listPadding ->
                LazyColumn(
                    Modifier.fillMaxSize(),
                    state = listState,
                    contentPadding = listPadding + PaddingValues(bottom = 8.dp),
                ) {
                    pagedRefreshStatus(state.results, UiError.Kind.SearchAthletes) { state.eventSink(AthleteSearchEvent.Retry) }
                    items(state.results.items, key = { it.did.value }) { account ->
                        AccountRow(account, onClick = { state.eventSink(AthleteSearchEvent.Open(account.did)) })
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    }
                    footer(state)
                }
            }
        }
    }
}

@Composable
private fun SearchField(query: TextFieldState, modifier: Modifier = Modifier) {
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    // Open ready to type, unless coming back to a search already made.
    SideEffect(Unit) { if (query.text.isEmpty()) focus.requestFocus() }
    AppTextField(
        query,
        modifier = modifier.focusRequester(focus),
        placeholder = stringResource(Res.string.search_label),
        leadingIcon = { PlatformIcon(AppIcon.Search, null) },
        trailingIcon = {
            if (query.text.isNotEmpty()) {
                AppIconButton(
                    AppIcon.Clear,
                    stringResource(Res.string.search_clear),
                    onClick = { query.clearText() },
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        lineLimits = TextFieldLineLimits.SingleLine,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        onKeyboardAction = { keyboard?.hide() },
    )
}

/** The prompt before a search; then a spinner, why the search failed, or the no-matches message, below the results. */
private fun LazyListScope.footer(state: AthleteSearchState) {
    if (state.query.text.isBlank()) {
        item { Message(stringResource(Res.string.search_prompt)) }
        return
    }
    pagedFooter(state.results, UiError.Kind.SearchAthletes, onRetry = { state.eventSink(AthleteSearchEvent.Retry) }) {
        Message(stringResource(Res.string.search_no_results, state.query.text.trim().toString()))
    }
}

@Composable
private fun Message(text: String, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
        Text(text, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
