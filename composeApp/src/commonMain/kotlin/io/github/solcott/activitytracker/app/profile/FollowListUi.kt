package io.github.solcott.activitytracker.app.profile

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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.slack.circuit.codegen.annotations.CircuitInject
import io.github.solcott.activitytracker.app.ui.AccountRow
import io.github.solcott.activitytracker.app.ui.adaptive.AppProgressIndicator
import io.github.solcott.activitytracker.app.ui.adaptive.AppScaffold
import io.github.solcott.activitytracker.app.ui.adaptive.AppTextButton
import io.github.solcott.activitytracker.app.ui.adaptive.AppTopBar
import io.github.solcott.activitytracker.app.ui.adaptive.ReadableList
import io.github.solcott.activitytracker.app.ui.errorText
import io.github.solcott.activitytracker.app.ui.handleLabel
import io.github.solcott.activitytracker.composeApp.resources.Res
import io.github.solcott.activitytracker.composeApp.resources.action_retry
import io.github.solcott.activitytracker.composeApp.resources.follow_list_followers
import io.github.solcott.activitytracker.composeApp.resources.follow_list_following
import io.github.solcott.activitytracker.composeApp.resources.follow_list_no_followers
import io.github.solcott.activitytracker.composeApp.resources.follow_list_no_following
import io.github.solcott.activitytracker.data.di.SessionScope
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

@CircuitInject(FollowListScreen::class, SessionScope::class)
@Composable
fun FollowListUi(state: FollowListState, modifier: Modifier = Modifier) {
    val listState = rememberLazyListState()
    // Ask for the next page once the last row is on screen.
    val atEnd by remember {
        derivedStateOf {
            val info = listState.layoutInfo
            info.totalItemsCount > 0 && (info.visibleItemsInfo.lastOrNull()?.index ?: 0) >= info.totalItemsCount - 1
        }
    }
    LaunchedEffect(atEnd, state.canLoadMore, state.isLoading) {
        if (atEnd && state.canLoadMore && !state.isLoading) state.eventSink(FollowListEvent.LoadMore)
    }

    AppScaffold(
        modifier = modifier,
        topBar = {
            AppTopBar(
                title = stringResource(state.kind.title),
                subtitle = state.subject?.let { handleLabel(it.handle.value) },
                onBack = { state.eventSink(FollowListEvent.Back) },
            )
        },
    ) { padding ->
        ReadableList(listState, Modifier.fillMaxSize(), padding, minSidePadding = 0.dp) { listPadding ->
            LazyColumn(
                Modifier.fillMaxSize(),
                state = listState,
                contentPadding = listPadding + PaddingValues(vertical = 8.dp),
            ) {
                items(state.accounts, key = { it.did.value }) { account ->
                    AccountRow(account, onClick = { state.eventSink(FollowListEvent.Open(account.did)) })
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
                footer(state)
            }
        }
    }
}

/** Error with retry, a loading spinner, or the empty state, below the accounts. */
private fun LazyListScope.footer(state: FollowListState) {
    state.error?.let { message ->
        item {
            Column(Modifier.fillMaxWidth().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(errorText(message), color = MaterialTheme.colorScheme.error)
                AppTextButton(onClick = { state.eventSink(FollowListEvent.Retry) }) { Text(stringResource(Res.string.action_retry)) }
            }
        }
    }
    if (state.isLoading) {
        item { Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) { AppProgressIndicator() } }
    } else if (state.accounts.isEmpty() && state.error == null) {
        item {
            Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) { Text(stringResource(state.kind.emptyText)) }
        }
    }
}

private val FollowListKind.title: StringResource
    get() =
        when (this) {
            FollowListKind.Followers -> Res.string.follow_list_followers
            FollowListKind.Following -> Res.string.follow_list_following
        }

private val FollowListKind.emptyText: StringResource
    get() =
        when (this) {
            FollowListKind.Followers -> Res.string.follow_list_no_followers
            FollowListKind.Following -> Res.string.follow_list_no_following
        }
