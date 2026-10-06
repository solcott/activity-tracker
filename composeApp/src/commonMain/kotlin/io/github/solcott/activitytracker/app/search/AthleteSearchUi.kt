package io.github.solcott.activitytracker.app.search

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.clearText
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.slack.circuit.codegen.annotations.CircuitInject
import io.github.solcott.activitytracker.app.ui.AccountRow
import io.github.solcott.activitytracker.app.ui.errorText
import io.github.solcott.activitytracker.composeApp.resources.Res
import io.github.solcott.activitytracker.composeApp.resources.action_back
import io.github.solcott.activitytracker.composeApp.resources.action_retry
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
    // Ask for the next page once the last row is on screen.
    val atEnd by remember {
        derivedStateOf {
            val info = listState.layoutInfo
            info.totalItemsCount > 0 && (info.visibleItemsInfo.lastOrNull()?.index ?: 0) >= info.totalItemsCount - 1
        }
    }
    LaunchedEffect(atEnd, state.canLoadMore, state.isLoading) {
        if (atEnd && state.canLoadMore && !state.isLoading) state.eventSink(AthleteSearchEvent.LoadMore)
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(Res.string.search_title)) },
                navigationIcon = {
                    IconButton(onClick = { state.eventSink(AthleteSearchEvent.Back) }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(Res.string.action_back))
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            SearchField(
                state.query,
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            )
            LazyColumn(Modifier.fillMaxSize(), state = listState, contentPadding = PaddingValues(bottom = 8.dp)) {
                items(state.results, key = { it.did.value }) { account ->
                    AccountRow(account, onClick = { state.eventSink(AthleteSearchEvent.Open(account.did)) })
                    HorizontalDivider()
                }
                footer(state)
            }
        }
    }
}

@Composable
private fun SearchField(query: TextFieldState, modifier: Modifier = Modifier) {
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    // Open ready to type, unless coming back to a search already made.
    LaunchedEffect(Unit) { if (query.text.isEmpty()) focus.requestFocus() }
    OutlinedTextField(
        query,
        modifier = modifier.focusRequester(focus),
        label = { Text(stringResource(Res.string.search_label)) },
        leadingIcon = { Icon(Icons.Default.Search, null) },
        trailingIcon = {
            if (query.text.isNotEmpty()) {
                IconButton(onClick = { query.clearText() }) { Icon(Icons.Default.Clear, stringResource(Res.string.search_clear)) }
            }
        },
        lineLimits = TextFieldLineLimits.SingleLine,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        onKeyboardAction = { keyboard?.hide() },
    )
}

/** Error with retry, a loading spinner, the prompt before a search, or the no-matches message, below the results. */
private fun LazyListScope.footer(state: AthleteSearchState) {
    val error = state.error
    when {
        error != null -> {
            item {
                Column(Modifier.fillMaxWidth().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(errorText(error), color = MaterialTheme.colorScheme.error)
                    TextButton(onClick = { state.eventSink(AthleteSearchEvent.Retry) }) { Text(stringResource(Res.string.action_retry)) }
                }
            }
        }

        state.isLoading -> {
            item { Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() } }
        }

        state.query.text.isBlank() -> {
            item { Message(stringResource(Res.string.search_prompt)) }
        }

        state.searched && state.results.isEmpty() -> {
            item { Message(stringResource(Res.string.search_no_results, state.query.text.trim().toString())) }
        }
    }
}

@Composable
private fun Message(text: String, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
        Text(text, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
