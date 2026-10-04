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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.slack.circuit.codegen.annotations.CircuitInject
import io.github.solcott.activitytracker.app.ui.Format
import io.github.solcott.activitytracker.app.ui.RoutePreview
import io.github.solcott.activitytracker.app.ui.StatsRow
import io.github.solcott.activitytracker.data.di.SessionScope

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
private fun LoadedContent(state: ActivityDetailState.Loaded, modifier: Modifier = Modifier) {
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
