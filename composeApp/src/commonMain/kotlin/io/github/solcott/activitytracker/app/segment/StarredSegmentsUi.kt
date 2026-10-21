package io.github.solcott.activitytracker.app.segment

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
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
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import com.slack.circuit.codegen.annotations.CircuitInject
import io.github.solcott.activitytracker.app.ui.Format
import io.github.solcott.activitytracker.app.ui.adaptive.AppIcon
import io.github.solcott.activitytracker.app.ui.adaptive.AppIconButton
import io.github.solcott.activitytracker.app.ui.adaptive.AppProgressIndicator
import io.github.solcott.activitytracker.app.ui.adaptive.AppScaffold
import io.github.solcott.activitytracker.app.ui.adaptive.AppTextButton
import io.github.solcott.activitytracker.app.ui.adaptive.AppTopBar
import io.github.solcott.activitytracker.app.ui.adaptive.ReadableList
import io.github.solcott.activitytracker.app.ui.adaptive.rememberAppTopBarScrollBehavior
import io.github.solcott.activitytracker.app.ui.errorText
import io.github.solcott.activitytracker.composeApp.resources.Res
import io.github.solcott.activitytracker.composeApp.resources.action_retry
import io.github.solcott.activitytracker.composeApp.resources.segment_unstar
import io.github.solcott.activitytracker.composeApp.resources.starred_segment_unavailable
import io.github.solcott.activitytracker.composeApp.resources.starred_segments_empty
import io.github.solcott.activitytracker.composeApp.resources.starred_segments_title
import io.github.solcott.activitytracker.data.di.SessionScope
import org.jetbrains.compose.resources.stringResource

/** The athlete's starred segments, newest star first: each opens its segment, and its star unstars it. */
@CircuitInject(StarredSegmentsScreen::class, SessionScope::class)
@Composable
fun StarredSegmentsUi(state: StarredSegmentsState, modifier: Modifier = Modifier) {
    val listState = rememberLazyListState()
    // Ask for the next page once the last row is on screen.
    val atEnd by remember {
        derivedStateOf {
            val info = listState.layoutInfo
            info.totalItemsCount > 0 && (info.visibleItemsInfo.lastOrNull()?.index ?: 0) >= info.totalItemsCount - 1
        }
    }
    SideEffect(atEnd, state.canLoadMore, state.isLoading) {
        if (atEnd && state.canLoadMore && !state.isLoading) state.eventSink(StarredSegmentsEvent.LoadMore)
    }

    val scrollBehavior = rememberAppTopBarScrollBehavior()
    AppScaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            AppTopBar(
                title = stringResource(Res.string.starred_segments_title),
                onBack = { state.eventSink(StarredSegmentsEvent.Back) },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { padding ->
        ReadableList(listState, Modifier.fillMaxSize(), padding, minSidePadding = 0.dp) { listPadding ->
            LazyColumn(Modifier.fillMaxSize(), state = listState, contentPadding = listPadding + PaddingValues(vertical = 8.dp)) {
                items(state.segments, key = { it.uri.value }) { starred ->
                    StarredRow(
                        starred,
                        removing = starred.uri in state.removing,
                        onOpen = { state.eventSink(StarredSegmentsEvent.Open(starred.uri)) },
                        onUnstar = { state.eventSink(StarredSegmentsEvent.Unstar(starred.uri)) },
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
                footer(state)
            }
        }
    }
}

@Composable
private fun StarredRow(starred: StarredSegment, removing: Boolean, onOpen: () -> Unit, onUnstar: () -> Unit) {
    val segment = starred.segment
    Row(
        Modifier.fillMaxWidth()
            .clickable(enabled = segment != null, onClick = onOpen)
            .padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            if (segment != null) {
                Text(segment.record.name, style = MaterialTheme.typography.titleMedium)
                Text(
                    "${Format.distance(segment.distanceMeters.toDouble())} · ${segmentSportLabel(segment.record.sport)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Text(
                    stringResource(Res.string.starred_segment_unavailable),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        AppIconButton(AppIcon.Star, stringResource(Res.string.segment_unstar), onClick = onUnstar, enabled = !removing, filled = true)
    }
}

/** Error with retry, a loading spinner, or the empty state, below the segments. */
private fun LazyListScope.footer(state: StarredSegmentsState) {
    state.error?.let { error ->
        item {
            Column(Modifier.fillMaxWidth().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(errorText(error), color = MaterialTheme.colorScheme.error)
                AppTextButton(onClick = { state.eventSink(StarredSegmentsEvent.Retry) }) { Text(stringResource(Res.string.action_retry)) }
            }
        }
    }
    if (state.isLoading) {
        item { Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) { AppProgressIndicator() } }
    } else if (state.segments.isEmpty() && state.error == null) {
        item {
            Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                Text(stringResource(Res.string.starred_segments_empty))
            }
        }
    }
}
