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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.slack.circuit.codegen.annotations.CircuitInject
import com.slack.circuit.runtime.CircuitUiState
import com.slack.circuit.runtime.Navigator
import com.slack.circuit.runtime.screen.Screen
import com.slack.circuit.serialization.CircuitSerializable
import io.github.solcott.activitytracker.app.detail.ActivityDetailScreen
import io.github.solcott.activitytracker.app.profile.ProfileScreen
import io.github.solcott.activitytracker.app.record.ManualActivityScreen
import io.github.solcott.activitytracker.app.record.RecordScreen
import io.github.solcott.activitytracker.app.ui.ActivityCard
import io.github.solcott.activitytracker.atproto.runSuspendCatching
import io.github.solcott.activitytracker.data.activity.ActivityRepository
import io.github.solcott.activitytracker.data.di.SessionScope
import io.github.solcott.activitytracker.data.feed.FeedRepository
import io.github.solcott.activitytracker.data.profile.ProfileRepository
import io.github.solcott.activitytracker.lexicon.app.ActivityView
import kotlinx.coroutines.launch

@CircuitSerializable(SessionScope::class) data object FeedScreen : Screen

data class FeedState(
    val activities: List<ActivityView>,
    val refreshing: Boolean,
    val error: String?,
    val pendingWrites: Int,
    val eventSink: (FeedEvent) -> Unit,
) : CircuitUiState

sealed interface FeedEvent {
    data object Refresh : FeedEvent

    data class Open(val activity: ActivityView) : FeedEvent

    data class OpenAuthor(val activity: ActivityView) : FeedEvent

    data class ToggleKudos(val activity: ActivityView) : FeedEvent

    data object Record : FeedEvent

    data object AddManual : FeedEvent

    data object OpenMyProfile : FeedEvent
}

@CircuitInject(FeedScreen::class, SessionScope::class)
@Composable
fun FeedPresenter(
    navigator: Navigator,
    feed: FeedRepository,
    activities: ActivityRepository,
    profiles: ProfileRepository,
): FeedState {
    val items by feed.timeline().collectAsState(emptyList())
    val pending by activities.pendingWrites.collectAsState(0)
    var refreshing by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var refreshKey by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(refreshKey) {
        refreshing = true
        error =
            runSuspendCatching {
                activities.sync()
                feed.refreshTimeline()
            }
                .exceptionOrNull()
                ?.let { it.message ?: "Couldn't load your feed" }
        refreshing = false
    }

    return FeedState(items, refreshing, error, pending) { event ->
        when (event) {
            FeedEvent.Refresh -> {
                refreshKey++
            }

            is FeedEvent.Open -> {
                navigator.goTo(ActivityDetailScreen(event.activity.uri.value))
            }

            is FeedEvent.OpenAuthor -> {
                navigator.goTo(ProfileScreen(event.activity.author.did.value))
            }

            is FeedEvent.ToggleKudos -> {
                scope.launch {
                    runSuspendCatching {
                        if (event.activity.viewer?.kudos == null) {
                            activities.giveKudos(event.activity)
                        } else {
                            activities.removeKudos(event.activity)
                        }
                    }
                }
            }

            FeedEvent.Record -> {
                navigator.goTo(RecordScreen)
            }

            FeedEvent.AddManual -> {
                navigator.goTo(ManualActivityScreen)
            }

            FeedEvent.OpenMyProfile -> {
                navigator.goTo(ProfileScreen(profiles.did.value))
            }
        }
    }
}

@CircuitInject(FeedScreen::class, SessionScope::class)
@Composable
fun FeedUi(state: FeedState, modifier: Modifier = Modifier) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("Feed") },
                actions = {
                    IconButton(onClick = { state.eventSink(FeedEvent.Refresh) }) { Icon(Icons.Default.Refresh, "Refresh") }
                    IconButton(onClick = { state.eventSink(FeedEvent.AddManual) }) { Icon(Icons.Default.Edit, "Add manual activity") }
                    IconButton(onClick = { state.eventSink(FeedEvent.OpenMyProfile) }) { Icon(Icons.Default.Person, "My profile") }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { state.eventSink(FeedEvent.Record) },
                icon = { Icon(Icons.Default.Add, null) },
                text = { Text("Record") },
            )
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
                            "Uploading ${state.pendingWrites} change(s)…",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
                state.error?.let { item { Text(it, color = MaterialTheme.colorScheme.error) } }
                items(state.activities, key = { it.uri.value }) { activity ->
                    ActivityCard(
                        activity = activity,
                        onClick = { state.eventSink(FeedEvent.Open(activity)) },
                        onAuthorClick = { state.eventSink(FeedEvent.OpenAuthor(activity)) },
                        onKudos = { state.eventSink(FeedEvent.ToggleKudos(activity)) },
                    )
                }
                if (state.activities.isEmpty() && !state.refreshing) {
                    item {
                        Box(
                            Modifier.fillMaxSize().padding(32.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text("No activities yet. Record one or follow other athletes.")
                        }
                    }
                }
            }
        }
    }
}
