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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import com.slack.circuit.codegen.annotations.CircuitInject
import io.github.solcott.activitytracker.app.UiError
import io.github.solcott.activitytracker.app.ui.Format
import io.github.solcott.activitytracker.app.ui.LoadMoreAtEnd
import io.github.solcott.activitytracker.app.ui.adaptive.AppIcon
import io.github.solcott.activitytracker.app.ui.adaptive.AppIconButton
import io.github.solcott.activitytracker.app.ui.adaptive.AppScaffold
import io.github.solcott.activitytracker.app.ui.adaptive.AppTopBar
import io.github.solcott.activitytracker.app.ui.adaptive.ReadableList
import io.github.solcott.activitytracker.app.ui.adaptive.rememberAppTopBarScrollBehavior
import io.github.solcott.activitytracker.app.ui.errorText
import io.github.solcott.activitytracker.app.ui.pagedFooter
import io.github.solcott.activitytracker.app.ui.pagedRefreshStatus
import io.github.solcott.activitytracker.composeApp.resources.Res
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
    LoadMoreAtEnd(listState, state.segments.canLoadMore) { state.eventSink(StarredSegmentsEvent.LoadMore) }

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
                if (state.needsSignIn) {
                    item(key = "sign-in") {
                        Text(
                            errorText(UiError(UiError.Kind.SignInAgain)),
                            Modifier.fillMaxWidth().padding(16.dp),
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
                state.actionError?.let { error ->
                    item(key = "action-error") {
                        Text(errorText(error), Modifier.fillMaxWidth().padding(16.dp), color = MaterialTheme.colorScheme.error)
                    }
                }
                pagedRefreshStatus(state.segments, UiError.Kind.LoadStarredSegments) { state.eventSink(StarredSegmentsEvent.Retry) }
                items(state.segments.items, key = { it.uri.value }) { starred ->
                    StarredRow(
                        starred,
                        removing = starred.uri in state.removing,
                        onOpen = { state.eventSink(StarredSegmentsEvent.Open(starred.uri)) },
                        onUnstar = { state.eventSink(StarredSegmentsEvent.Unstar(starred.uri)) },
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
                if (!state.needsSignIn) {
                    pagedFooter(
                        state.segments,
                        UiError.Kind.LoadStarredSegments,
                        onRetry = { state.eventSink(StarredSegmentsEvent.Retry) },
                    ) {
                        Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                            Text(stringResource(Res.string.starred_segments_empty))
                        }
                    }
                }
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
                Text(segment.currentName, style = MaterialTheme.typography.titleMedium)
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
