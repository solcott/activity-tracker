package io.github.solcott.activitytracker.app.gear

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.plus
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.slack.circuit.codegen.annotations.CircuitInject
import io.github.solcott.activitytracker.app.UiError
import io.github.solcott.activitytracker.app.ui.ContentStateLayout
import io.github.solcott.activitytracker.app.ui.RefreshStatus
import io.github.solcott.activitytracker.app.ui.adaptive.AppButton
import io.github.solcott.activitytracker.app.ui.adaptive.AppIcon
import io.github.solcott.activitytracker.app.ui.adaptive.AppIconButton
import io.github.solcott.activitytracker.app.ui.adaptive.AppScaffold
import io.github.solcott.activitytracker.app.ui.adaptive.AppTextButton
import io.github.solcott.activitytracker.app.ui.adaptive.AppTopBar
import io.github.solcott.activitytracker.app.ui.adaptive.ReadableList
import io.github.solcott.activitytracker.app.ui.adaptive.rememberAppTopBarScrollBehavior
import io.github.solcott.activitytracker.app.ui.visibilityLabel
import io.github.solcott.activitytracker.composeApp.resources.Res
import io.github.solcott.activitytracker.composeApp.resources.gear_add
import io.github.solcott.activitytracker.composeApp.resources.gear_collection_count
import io.github.solcott.activitytracker.composeApp.resources.gear_collection_new
import io.github.solcott.activitytracker.composeApp.resources.gear_collections
import io.github.solcott.activitytracker.composeApp.resources.gear_collections_help
import io.github.solcott.activitytracker.composeApp.resources.gear_empty
import io.github.solcott.activitytracker.composeApp.resources.gear_retired
import io.github.solcott.activitytracker.composeApp.resources.gear_title
import io.github.solcott.activitytracker.data.di.SessionScope
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/** The athlete's gear: a section per type, then Retired. Each row opens the item; Add opens a new one. */
@CircuitInject(GearListScreen::class, SessionScope::class)
@Composable
fun GearListUi(state: GearListState, modifier: Modifier = Modifier) {
    val scrollBehavior = rememberAppTopBarScrollBehavior()
    AppScaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            AppTopBar(
                title = stringResource(Res.string.gear_title),
                onBack = { state.eventSink(GearListEvent.Back) },
                scrollBehavior = scrollBehavior,
                actions = { AppIconButton(AppIcon.Add, stringResource(Res.string.gear_add), { state.eventSink(GearListEvent.Add) }) },
            )
        },
    ) { padding ->
        ContentStateLayout(state.content, UiError.Kind.LoadGear, { state.eventSink(GearListEvent.Retry) }, contentPadding = padding) { lists
            ->
            val listState = rememberLazyListState()
            ReadableList(listState, Modifier.fillMaxSize(), padding, minSidePadding = 0.dp) { listPadding ->
                LazyColumn(Modifier.fillMaxSize(), state = listState, contentPadding = listPadding + PaddingValues(vertical = 8.dp)) {
                    // Over the loaded list: a spinner while it reloads, or why the last reload failed with Retry.
                    item(key = "refresh") {
                        RefreshStatus(state.content, UiError.Kind.LoadGear, onRetry = { state.eventSink(GearListEvent.Retry) })
                    }
                    if (lists.isEmpty) {
                        item(key = "empty") {
                            Column(
                                Modifier.fillMaxWidth().padding(32.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(16.dp),
                            ) {
                                Text(stringResource(Res.string.gear_empty))
                                AppButton(onClick = { state.eventSink(GearListEvent.Add) }) { Text(stringResource(Res.string.gear_add)) }
                            }
                        }
                    }
                    if (!lists.isEmpty) collections(lists.collections, state.eventSink)
                    lists.active.forEach { group -> section(group.type, group.type, group.items, state.eventSink) }
                    if (lists.retired.isNotEmpty()) section("retired", null, lists.retired, state.eventSink)
                }
            }
        }
    }
}

/** The athlete's collections, each with its item count, and New collection. */
private fun LazyListScope.collections(rows: List<GearCollectionRow>, eventSink: (GearListEvent) -> Unit) {
    item(key = "header/collections") {
        Row(
            Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                stringResource(Res.string.gear_collections),
                Modifier.weight(1f),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
            )
            AppTextButton(onClick = { eventSink(GearListEvent.AddCollection) }) { Text(stringResource(Res.string.gear_collection_new)) }
        }
    }
    if (rows.isEmpty()) {
        item(key = "collections/help") {
            Text(
                stringResource(Res.string.gear_collections_help),
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    items(rows, key = { "collections/${it.rkey}" }) { row ->
        Column(
            Modifier.fillMaxWidth()
                .clickable(role = Role.Button) { eventSink(GearListEvent.OpenCollection(row.rkey)) }
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Text(row.name, style = MaterialTheme.typography.titleMedium)
            Text(
                pluralStringResource(Res.plurals.gear_collection_count, row.count, row.count),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
}

/** A header ([type]'s name, or Retired when it's null), then a row per item. */
private fun LazyListScope.section(key: String, type: String?, rows: List<GearRow>, eventSink: (GearListEvent) -> Unit) {
    item(key = "header/$key") {
        Text(
            if (type == null) stringResource(Res.string.gear_retired) else gearTypeLabel(type),
            Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
        )
    }
    items(rows, key = { "$key/${it.rkey}" }) { row ->
        GearRowItem(row, onOpen = { eventSink(GearListEvent.Open(row.rkey)) })
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
}

/** One item: its photo, name, kind and who may see it, and Retired when [showRetired] and it is; [trailing] goes at the end. */
@Composable
internal fun GearRowItem(
    row: GearRow,
    onOpen: (() -> Unit)?,
    modifier: Modifier = Modifier,
    showRetired: Boolean = false,
    trailing: @Composable () -> Unit = {},
) {
    Row(
        modifier
            .fillMaxWidth()
            .then(if (onOpen != null) Modifier.clickable(onClick = onOpen) else Modifier)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.size(48.dp).clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surfaceVariant)) {
            row.photoUrl?.let { AsyncImage(it, contentDescription = null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
        }
        Column(Modifier.weight(1f)) {
            Text(row.name, style = MaterialTheme.typography.titleMedium)
            val detail = "${row.kind?.let { gearKindLabel(row.type, it) } ?: gearTypeLabel(row.type)} · ${visibilityLabel(row.visibility)}"
            Text(
                if (showRetired && row.retired) "$detail · ${stringResource(Res.string.gear_retired)}" else detail,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        row.totals?.let { Text(gearMainTotalLabel(row.mainTotal, it), style = MaterialTheme.typography.bodyMedium) }
        trailing()
    }
}
