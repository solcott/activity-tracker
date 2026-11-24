package io.github.solcott.activitytracker.app.segment

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.slack.circuit.codegen.annotations.CircuitInject
import io.github.solcott.activitytracker.app.UiError
import io.github.solcott.activitytracker.app.report.ReportTexts
import io.github.solcott.activitytracker.app.report.ReportingHost
import io.github.solcott.activitytracker.app.ui.Avatar
import io.github.solcott.activitytracker.app.ui.ListScreen
import io.github.solcott.activitytracker.app.ui.RefreshStatus
import io.github.solcott.activitytracker.app.ui.adaptive.AppIcon
import io.github.solcott.activitytracker.app.ui.adaptive.AppIconButton
import io.github.solcott.activitytracker.app.ui.adaptive.AppMenu
import io.github.solcott.activitytracker.app.ui.adaptive.AppMenuItem
import io.github.solcott.activitytracker.app.ui.adaptive.AppProgressIndicator
import io.github.solcott.activitytracker.app.ui.errorItem
import io.github.solcott.activitytracker.app.ui.string
import io.github.solcott.activitytracker.composeApp.resources.Res
import io.github.solcott.activitytracker.composeApp.resources.detail_more
import io.github.solcott.activitytracker.composeApp.resources.trail_report_delete
import io.github.solcott.activitytracker.data.di.SessionScope
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import org.jetbrains.compose.resources.stringResource

/**
 * Every report on a segment (segments/trail-reports): the last 14 days, then older ones, each with who posted it and when, its tags, note
 * and photos, and a menu offering Report and Block on someone else's and Delete on the viewer's own.
 */
@CircuitInject(TrailReportsScreen::class, SessionScope::class)
@Composable
fun TrailReportsUi(state: TrailReportsState, modifier: Modifier = Modifier) {
    val retry = { state.eventSink(TrailReportsEvent.Retry) }
    ListScreen(
        title = state.name,
        onBack = { state.eventSink(TrailReportsEvent.Back) },
        content = state.content,
        errorKind = UiError.Kind.LoadTrailReports,
        onRetry = retry,
        modifier = modifier,
    ) { rows ->
        item(key = "refresh") { RefreshStatus(state.content, UiError.Kind.LoadTrailReports, retry) }
        errorItem(state.error)
        items(rows, key = { it.uri }) { row -> ReportRow(row, state) }
        if (state.hasMore || state.loadingMore) {
            item(key = "more") {
                // Asks for the next page once the end of the list shows.
                SideEffect(rows.size, state.loadingMore) { if (!state.loadingMore) state.eventSink(TrailReportsEvent.LoadMore) }
                Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) { AppProgressIndicator(small = true) }
            }
        }
    }
    ReportingHost(state.reporting)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ReportRow(row: TrailReportRow, state: TrailReportsState) {
    Column {
        row.heading?.let { heading ->
            Text(
                heading.string(),
                Modifier.padding(top = 16.dp, bottom = 4.dp),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Avatar(row.avatar, row.author, AVATAR_SIZE)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(row.author, style = MaterialTheme.typography.titleSmall)
                        Text(
                            row.age.string(),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    RowMenu(row, state)
                }
                if (row.tags.isNotEmpty()) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.tags.forEach { ConditionChip(it, closed = TrailReportTexts.isClosed(it)) }
                    }
                }
                row.note?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
                if (row.photos.isNotEmpty()) ReportPhotos(row.photos) { state.eventSink(TrailReportsEvent.OpenPhoto(row.uri, it + 1)) }
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
}

/** A report's photos in a row of small squares. */
@Composable
private fun ReportPhotos(photos: ImmutableList<PhotoThumb>, onOpen: (Int) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        photos.forEachIndexed { index, photo -> PhotoThumbTile(photo, onOpen = { onOpen(index) }, Modifier.size(ROW_PHOTO)) }
    }
}

/** The "…" menu: Delete on the viewer's own report, Report (when a labeler is set up) and Block on anyone else's. */
@Composable
private fun RowMenu(row: TrailReportRow, state: TrailReportsState) {
    var open by remember { mutableStateOf(false) }
    val send = state.eventSink
    val delete = stringResource(Res.string.trail_report_delete)
    val report = ReportTexts.menuEntry.string()
    val block = ReportTexts.block.string()
    val items =
        remember(row.uri, row.own, state.reporting.available, delete, report, block) {
            buildList {
                if (row.own) {
                    add(AppMenuItem(delete, AppIcon.Delete, destructive = true) { send(TrailReportsEvent.Delete(row.uri)) })
                } else {
                    if (state.reporting.available) add(AppMenuItem(report) { send(TrailReportsEvent.Report(row.uri)) })
                    add(AppMenuItem(block, destructive = true) { send(TrailReportsEvent.Block(row.uri)) })
                }
            }
                .toImmutableList()
        }
    Box {
        AppIconButton(AppIcon.More, stringResource(Res.string.detail_more), onClick = { open = true })
        AppMenu(expanded = open, onDismiss = { open = false }, items = items)
    }
}

private val AVATAR_SIZE = 40.dp
private val ROW_PHOTO = 72.dp
