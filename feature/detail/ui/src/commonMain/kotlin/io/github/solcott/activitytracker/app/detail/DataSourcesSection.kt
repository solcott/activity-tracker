package io.github.solcott.activitytracker.app.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.solcott.activitytracker.app.ui.ErrorText
import io.github.solcott.activitytracker.app.ui.adaptive.AppIcon
import io.github.solcott.activitytracker.app.ui.adaptive.AppMenu
import io.github.solcott.activitytracker.app.ui.adaptive.AppMenuItem
import io.github.solcott.activitytracker.app.ui.adaptive.AppProgressIndicator
import io.github.solcott.activitytracker.app.ui.adaptive.AppSwitch
import io.github.solcott.activitytracker.app.ui.adaptive.AppTextButton
import io.github.solcott.activitytracker.app.ui.adaptive.PlatformIcon
import io.github.solcott.activitytracker.app.ui.string
import io.github.solcott.activitytracker.data.activity.DataKind
import io.github.solcott.activitytracker.lexicon.app.MemberView
import kotlinx.collections.immutable.toImmutableList

/**
 * Where each kind of the owner's combined activity's data comes from (`feed/combined-sources`): one row per kind, switched off when it's
 * left out, with a menu of the members that recorded it when more than one did. Shown in the owner's combination editor.
 */
@Composable
fun DataSourcesSection(sources: DataSources, eventSink: (ActivityDetailEvent) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(CombinationTexts.dataSources.string(), style = MaterialTheme.typography.labelLarge)
        if (sources.isLoading) AppProgressIndicator(small = true)
        sources.error?.let { error ->
            ErrorText(error, style = MaterialTheme.typography.bodySmall)
            AppTextButton(onClick = { eventSink(ActivityDetailEvent.RetrySources) }) { Text(CombinationTexts.retry.string()) }
        }
        sources.kinds.forEach { row -> SourceRow(row, saving = sources.saving, eventSink) }
    }
}

@Composable
private fun SourceRow(row: DataSourceRow, saving: DataKind?, eventSink: (ActivityDetailEvent) -> Unit) {
    val label = CombinationTexts.kind(row.kind).string()
    val idle = saving == null
    val chosen = row.chosen
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        when {
            !row.included ->
                Text(
                    CombinationTexts.leftOut.string(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            chosen != null && CombinationTexts.offersMenu(row) -> SourceMenu(row, chosen, enabled = idle, eventSink)
            chosen != null ->
                Text(
                    CombinationTexts.from(chosen).string(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
        }
        if (saving == row.kind) AppProgressIndicator(small = true)
        val include = CombinationTexts.include(row).string()
        AppSwitch(
            checked = row.included,
            onCheckedChange = { eventSink(CombinationTexts.includeEvent(row, it)) },
            modifier = Modifier.semantics { contentDescription = include },
            enabled = idle,
        )
    }
}

/** The member [row]'s kind comes from, as a compact trigger opening a menu of the members that recorded it. */
@Composable
private fun SourceMenu(row: DataSourceRow, chosen: MemberView, enabled: Boolean, eventSink: (ActivityDetailEvent) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val shown = CombinationTexts.sourceName(chosen).string()
    val description = CombinationTexts.comesFrom(row, chosen).string()
    Box {
        AppTextButton(
            onClick = { expanded = true },
            modifier = Modifier.semantics { contentDescription = description },
            enabled = enabled,
        ) {
            Text(shown, style = MaterialTheme.typography.bodyMedium)
            PlatformIcon(AppIcon.DropDown, null)
        }
        val items =
            row.offered
                .map { member ->
                    AppMenuItem(CombinationTexts.sourceName(member).string(), checked = member.uri == chosen.uri) {
                        CombinationTexts.chooseEvent(row, member)?.let(eventSink)
                    }
                }
                .toImmutableList()
        AppMenu(expanded = expanded, onDismiss = { expanded = false }, items = items)
    }
}
