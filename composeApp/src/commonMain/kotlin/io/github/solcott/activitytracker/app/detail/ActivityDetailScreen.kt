package io.github.solcott.activitytracker.app.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
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
import io.github.solcott.activitytracker.app.profile.ProfileScreen
import io.github.solcott.activitytracker.app.ui.Format
import io.github.solcott.activitytracker.app.ui.RoutePreview
import io.github.solcott.activitytracker.app.ui.StatsRow
import io.github.solcott.activitytracker.atproto.runSuspendCatching
import io.github.solcott.activitytracker.data.activity.ActivityRepository
import io.github.solcott.activitytracker.data.di.SessionScope
import io.github.solcott.activitytracker.data.feed.FeedRepository
import io.github.solcott.activitytracker.lexicon.AtUri
import io.github.solcott.activitytracker.lexicon.app.ActivityView
import io.github.solcott.activitytracker.lexicon.app.CommentView
import kotlinx.coroutines.launch

@CircuitSerializable(SessionScope::class) data class ActivityDetailScreen(val uri: String) : Screen

sealed interface ActivityDetailState : CircuitUiState {
    val eventSink: (ActivityDetailEvent) -> Unit

    data class Loading(override val eventSink: (ActivityDetailEvent) -> Unit) : ActivityDetailState

    data class Error(val message: String, override val eventSink: (ActivityDetailEvent) -> Unit) : ActivityDetailState

    data class Loaded(
        val activity: ActivityView,
        val comments: List<CommentView>,
        val draftComment: String,
        override val eventSink: (ActivityDetailEvent) -> Unit,
    ) : ActivityDetailState
}

sealed interface ActivityDetailEvent {
    data object Back : ActivityDetailEvent

    data object Retry : ActivityDetailEvent

    data object ToggleKudos : ActivityDetailEvent

    data object OpenAuthor : ActivityDetailEvent

    data class DraftChanged(val text: String) : ActivityDetailEvent

    data object SendComment : ActivityDetailEvent
}

@CircuitInject(ActivityDetailScreen::class, SessionScope::class)
@Composable
fun ActivityDetailPresenter(
    screen: ActivityDetailScreen,
    navigator: Navigator,
    feed: FeedRepository,
    activities: ActivityRepository,
): ActivityDetailState {
    var activity by remember { mutableStateOf<ActivityView?>(null) }
    var comments by remember { mutableStateOf(emptyList<CommentView>()) }
    var error by remember { mutableStateOf<String?>(null) }
    var draft by remember { mutableStateOf("") }
    var loadKey by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(loadKey) {
        error = null
        runSuspendCatching {
            val output = feed.activity(AtUri(screen.uri))
            activity = output.activity
            comments = output.comments
        }
            .onFailure { error = it.message ?: "Couldn't load activity" }
    }

    val sink: (ActivityDetailEvent) -> Unit = { event ->
        val current = activity
        when (event) {
            ActivityDetailEvent.Back -> {
                navigator.pop()
            }

            ActivityDetailEvent.Retry -> {
                loadKey++
            }

            ActivityDetailEvent.OpenAuthor -> {
                current?.let { navigator.goTo(ProfileScreen(it.author.did.value)) }
            }

            is ActivityDetailEvent.DraftChanged -> {
                draft = event.text
            }

            ActivityDetailEvent.ToggleKudos -> {
                if (current != null) {
                    val liked = current.viewer?.kudos != null
                    activity = current.copy(kudosCount = current.kudosCount + if (liked) -1 else 1)
                    scope.launch {
                        runSuspendCatching { if (liked) activities.removeKudos(current) else activities.giveKudos(current) }
                        loadKey++
                    }
                }
            }

            ActivityDetailEvent.SendComment -> {
                if (current != null && draft.isNotBlank()) {
                    val text = draft
                    draft = ""
                    scope.launch { runSuspendCatching { activities.comment(current, text) } }
                }
            }
        }
    }

    val loaded = activity
    return when {
        loaded != null -> ActivityDetailState.Loaded(loaded, comments, draft, sink)
        error != null -> ActivityDetailState.Error(error!!, sink)
        else -> ActivityDetailState.Loading(sink)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@CircuitInject(ActivityDetailScreen::class, SessionScope::class)
@Composable
fun ActivityDetailUi(state: ActivityDetailState, modifier: Modifier = Modifier) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("Activity") },
                navigationIcon = {
                    IconButton(onClick = { state.eventSink(ActivityDetailEvent.Back) }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
            )
        },
    ) { padding ->
        when (state) {
            is ActivityDetailState.Loading -> {
                Column(
                    Modifier.padding(padding).fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    CircularProgressIndicator(Modifier.padding(32.dp))
                }
            }

            is ActivityDetailState.Error -> {
                Column(Modifier.padding(padding).padding(16.dp)) {
                    Text(state.message, color = MaterialTheme.colorScheme.error)
                    TextButton(onClick = { state.eventSink(ActivityDetailEvent.Retry) }) { Text("Retry") }
                }
            }

            is ActivityDetailState.Loaded -> {
                LoadedContent(state, Modifier.padding(padding))
            }
        }
    }
}

@Composable
private fun LoadedContent(state: ActivityDetailState.Loaded, modifier: Modifier) {
    val record = state.activity.record
    LazyColumn(
        modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            TextButton(onClick = { state.eventSink(ActivityDetailEvent.OpenAuthor) }) {
                Text(state.activity.author.displayName ?: state.activity.author.handle.value)
            }
            Text(record.title ?: record.sport, style = MaterialTheme.typography.headlineSmall)
            record.description?.let { Text(it) }
        }
        item {
            StatsRow(
                "Distance" to Format.distance(record.distance?.toDouble()),
                "Moving time" to Format.duration(record.movingTime ?: record.elapsedTime),
                "Elevation" to Format.elevation(record.elevationGain?.toDouble()),
            )
        }
        record.polyline?.let { item { RoutePreview(it, height = 240.dp) } }
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { state.eventSink(ActivityDetailEvent.ToggleKudos) }) {
                    Text(
                        if (state.activity.viewer?.kudos != null) {
                            "Kudos given (${state.activity.kudosCount})"
                        } else {
                            "Give kudos (${state.activity.kudosCount})"
                        }
                    )
                }
            }
            HorizontalDivider()
        }
        items(state.comments, key = { it.uri.value }) { comment ->
            Column {
                Text(
                    comment.author.displayName ?: comment.author.handle.value,
                    style = MaterialTheme.typography.labelLarge,
                )
                Text(comment.text)
            }
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = state.draftComment,
                    onValueChange = { state.eventSink(ActivityDetailEvent.DraftChanged(it)) },
                    placeholder = { Text("Add a comment") },
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = { state.eventSink(ActivityDetailEvent.SendComment) }) { Icon(Icons.AutoMirrored.Filled.Send, "Send") }
            }
        }
    }
}
