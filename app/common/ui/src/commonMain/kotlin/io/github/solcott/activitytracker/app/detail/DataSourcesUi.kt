package io.github.solcott.activitytracker.app.detail

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import io.github.solcott.activitytracker.composeApp.resources.Res
import io.github.solcott.activitytracker.composeApp.resources.chart_source_from
import io.github.solcott.activitytracker.composeApp.resources.chart_source_then
import io.github.solcott.activitytracker.lexicon.app.MemberView
import kotlinx.collections.immutable.ImmutableList
import org.jetbrains.compose.resources.stringResource

/**
 * A card's caption on a combined activity: the devices its data came from, in time order ("From Edge 840", "From Forerunner, then Phone").
 * Each member is named by device, or by sport for a file that named none, since its start may be hidden from the viewer.
 */
@Composable
internal fun ChartSourceCaption(members: ImmutableList<MemberView>, modifier: Modifier = Modifier) {
    if (members.isEmpty()) return
    val names = members.map { memberName(it) }
    val joined = names.reduce { acc, name -> stringResource(Res.string.chart_source_then, acc, name) }
    Text(
        stringResource(Res.string.chart_source_from, joined),
        modifier,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
