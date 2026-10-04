package io.github.solcott.activitytracker.app.feed

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.slack.circuit.codegen.annotations.CircuitInject
import io.github.solcott.activitytracker.app.ui.ActivityCard
import io.github.solcott.activitytracker.app.ui.errorText
import io.github.solcott.activitytracker.composeApp.resources.Res
import io.github.solcott.activitytracker.composeApp.resources.feed_add_manual
import io.github.solcott.activitytracker.composeApp.resources.feed_empty_can_record
import io.github.solcott.activitytracker.composeApp.resources.feed_empty_manual_only
import io.github.solcott.activitytracker.composeApp.resources.feed_my_profile
import io.github.solcott.activitytracker.composeApp.resources.feed_pending_writes
import io.github.solcott.activitytracker.composeApp.resources.feed_record
import io.github.solcott.activitytracker.composeApp.resources.feed_refresh
import io.github.solcott.activitytracker.composeApp.resources.feed_title
import io.github.solcott.activitytracker.data.di.SessionScope
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

@CircuitInject(FeedScreen::class, SessionScope::class)
@Composable
fun FeedUi(state: FeedState, modifier: Modifier = Modifier) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(Res.string.feed_title)) },
                actions = {
                    IconButton(onClick = { state.eventSink(FeedEvent.Refresh) }) {
                        Icon(Icons.Default.Refresh, stringResource(Res.string.feed_refresh))
                    }
                    IconButton(onClick = { state.eventSink(FeedEvent.AddManual) }) {
                        Icon(Icons.Default.Edit, stringResource(Res.string.feed_add_manual))
                    }
                    IconButton(onClick = { state.eventSink(FeedEvent.OpenMyProfile) }) {
                        Icon(Icons.Default.Person, stringResource(Res.string.feed_my_profile))
                    }
                },
            )
        },
        floatingActionButton = {
            if (state.canRecord) {
                ExtendedFloatingActionButton(
                    onClick = { state.eventSink(FeedEvent.Record) },
                    icon = { Icon(Icons.Default.Add, null) },
                    text = { Text(stringResource(Res.string.feed_record)) },
                )
            }
        },
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = state.refreshing,
            onRefresh = { state.eventSink(FeedEvent.Refresh) },
            modifier = Modifier.padding(padding).fillMaxSize(),
        ) {
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                if (state.pendingWrites > 0) {
                    item {
                        Text(
                            pluralStringResource(Res.plurals.feed_pending_writes, state.pendingWrites, state.pendingWrites),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
                state.error?.let { item { Text(errorText(it), color = MaterialTheme.colorScheme.error) } }
                items(state.activities, key = { it.uri.value }) { activity ->
                    ActivityCard(
                        activity = activity,
                        onClick = { state.eventSink(FeedEvent.Open(activity)) },
                        onAuthorClick = { state.eventSink(FeedEvent.OpenAuthor(activity)) },
                        onKudos =
                            { state.eventSink(FeedEvent.ToggleKudos(activity)) }.takeUnless {
                                activity.author.did.value == state.viewerDid
                            },
                    )
                }
                if (state.activities.isEmpty() && !state.refreshing) {
                    item {
                        Box(
                            Modifier.fillMaxSize().padding(32.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                stringResource(if (state.canRecord) Res.string.feed_empty_can_record else Res.string.feed_empty_manual_only)
                            )
                        }
                    }
                }
            }
        }
    }
}
