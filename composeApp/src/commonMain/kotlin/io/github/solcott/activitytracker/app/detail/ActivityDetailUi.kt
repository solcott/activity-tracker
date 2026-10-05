package io.github.solcott.activitytracker.app.detail

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
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
import io.github.solcott.activitytracker.app.ui.ActivityMap
import io.github.solcott.activitytracker.app.ui.Format
import io.github.solcott.activitytracker.app.ui.Stat
import io.github.solcott.activitytracker.app.ui.StatsRow
import io.github.solcott.activitytracker.app.ui.errorText
import io.github.solcott.activitytracker.app.ui.handleLabel
import io.github.solcott.activitytracker.app.ui.sportLabel
import io.github.solcott.activitytracker.composeApp.resources.Res
import io.github.solcott.activitytracker.composeApp.resources.action_back
import io.github.solcott.activitytracker.composeApp.resources.action_retry
import io.github.solcott.activitytracker.composeApp.resources.detail_comment_placeholder
import io.github.solcott.activitytracker.composeApp.resources.detail_give_kudos
import io.github.solcott.activitytracker.composeApp.resources.detail_hide_kudos
import io.github.solcott.activitytracker.composeApp.resources.detail_kudos_given
import io.github.solcott.activitytracker.composeApp.resources.detail_no_kudos
import io.github.solcott.activitytracker.composeApp.resources.detail_send_comment
import io.github.solcott.activitytracker.composeApp.resources.detail_show_kudos
import io.github.solcott.activitytracker.composeApp.resources.detail_title
import io.github.solcott.activitytracker.composeApp.resources.kudos_count
import io.github.solcott.activitytracker.composeApp.resources.stat_distance
import io.github.solcott.activitytracker.composeApp.resources.stat_elevation
import io.github.solcott.activitytracker.composeApp.resources.stat_moving_time
import io.github.solcott.activitytracker.composeApp.resources.stat_temperature
import io.github.solcott.activitytracker.data.di.SessionScope
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

@CircuitInject(ActivityDetailScreen::class, SessionScope::class)
@Composable
fun ActivityDetailUi(state: ActivityDetailState, modifier: Modifier = Modifier) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(Res.string.detail_title)) },
                navigationIcon = {
                    IconButton(onClick = { state.eventSink(ActivityDetailEvent.Back) }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(Res.string.action_back))
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
                    Text(errorText(state.error), color = MaterialTheme.colorScheme.error)
                    TextButton(onClick = { state.eventSink(ActivityDetailEvent.Retry) }) { Text(stringResource(Res.string.action_retry)) }
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
            Text(record.title ?: sportLabel(record.sport), style = MaterialTheme.typography.headlineSmall)
            record.description?.let { Text(it) }
        }
        item {
            StatsRow(
                persistentListOf(
                    Stat(stringResource(Res.string.stat_distance), Format.distance(record.distance?.toDouble())),
                    Stat(stringResource(Res.string.stat_moving_time), Format.duration(record.movingTime ?: record.elapsedTime)),
                    Stat(stringResource(Res.string.stat_elevation), Format.elevation(record.elevationGain?.toDouble())),
                )
            )
        }
        item {
            val rate = Format.rateStat(record.sport, record.distance?.toDouble(), record.movingTime ?: record.elapsedTime)
            val temperature = record.avgTemperature?.let { Stat(stringResource(Res.string.stat_temperature), Format.temperature(it)) }
            StatsRow(listOfNotNull(rate, temperature).toImmutableList())
        }
        record.polyline?.let { item { ActivityMap(it) } }
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                KudosButton(state, Modifier.weight(1f))
                // Hiding stays available if the last kudos goes away while the list is open.
                if (state.activity.kudosCount > 0 || state.kudosList != null) {
                    TextButton(onClick = { state.eventSink(ActivityDetailEvent.ToggleKudosList) }) {
                        Text(stringResource(if (state.kudosList != null) Res.string.detail_hide_kudos else Res.string.detail_show_kudos))
                    }
                }
            }
        }
        state.kudosList?.let { kudosList(it, state.eventSink) }
        item { HorizontalDivider() }
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
                    placeholder = { Text(stringResource(Res.string.detail_comment_placeholder)) },
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = { state.eventSink(ActivityDetailEvent.SendComment) }) {
                    Icon(Icons.AutoMirrored.Filled.Send, stringResource(Res.string.detail_send_comment))
                }
            }
        }
    }
}

@Composable
private fun KudosButton(state: ActivityDetailState.Loaded, modifier: Modifier = Modifier) {
    val count = state.activity.kudosCount
    if (state.isOwn) {
        Text(pluralStringResource(Res.plurals.kudos_count, count.toInt(), count), modifier.padding(vertical = 12.dp))
    } else {
        Box(modifier) {
            TextButton(onClick = { state.eventSink(ActivityDetailEvent.ToggleKudos) }) {
                Text(
                    stringResource(
                        if (state.activity.viewer?.kudos != null) Res.string.detail_kudos_given else Res.string.detail_give_kudos,
                        count,
                    )
                )
            }
        }
    }
}

/** Who gave kudos, under the kudos row: a spinner, an error, the empty state, or one tappable row per athlete. */
private fun LazyListScope.kudosList(list: KudosList, eventSink: (ActivityDetailEvent) -> Unit) {
    val error = list.error
    when {
        list.isLoading -> {
            item { Box(Modifier.fillMaxWidth().padding(8.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() } }
        }

        error != null -> {
            item { Text(errorText(error), color = MaterialTheme.colorScheme.error) }
        }

        list.athletes.isEmpty() -> {
            item { Text(stringResource(Res.string.detail_no_kudos), style = MaterialTheme.typography.bodyMedium) }
        }

        else -> {
            items(list.athletes, key = { "kudos:${it.did.value}" }) { athlete ->
                Column(
                    Modifier.fillMaxWidth().clickable { eventSink(ActivityDetailEvent.OpenProfile(athlete.did)) }.padding(vertical = 4.dp)
                ) {
                    Text(athlete.displayName ?: athlete.handle.value, style = MaterialTheme.typography.titleSmall)
                    Text(handleLabel(athlete.handle.value), style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}
