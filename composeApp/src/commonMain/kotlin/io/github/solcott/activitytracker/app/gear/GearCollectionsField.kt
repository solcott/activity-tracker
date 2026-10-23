package io.github.solcott.activitytracker.app.gear

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.solcott.activitytracker.app.ui.adaptive.AppFilterChip
import io.github.solcott.activitytracker.app.ui.adaptive.AppIcon
import io.github.solcott.activitytracker.app.ui.adaptive.AppMenu
import io.github.solcott.activitytracker.app.ui.adaptive.AppMenuItem
import io.github.solcott.activitytracker.app.ui.adaptive.AppTextButton
import io.github.solcott.activitytracker.app.ui.adaptive.PlatformIcon
import io.github.solcott.activitytracker.composeApp.resources.Res
import io.github.solcott.activitytracker.composeApp.resources.gear_collections
import io.github.solcott.activitytracker.composeApp.resources.gear_collections_add_to
import io.github.solcott.activitytracker.composeApp.resources.gear_collections_remove_from
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import org.jetbrains.compose.resources.stringResource

/**
 * The collections the item is in, as chips that take it out, and a menu of the athlete's other collections to add it to. Hidden when the
 * athlete has none.
 */
@Composable
internal fun GearCollectionsField(
    collections: ImmutableList<GearCollectionChoice>,
    enabled: Boolean,
    onToggle: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (collections.isEmpty()) return
    var adding by remember { mutableStateOf(false) }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(stringResource(Res.string.gear_collections), style = MaterialTheme.typography.titleSmall)
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            itemVerticalAlignment = Alignment.CenterVertically,
        ) {
            collections
                .filter { it.member }
                .forEach { collection ->
                    AppFilterChip(
                        selected = true,
                        onClick = { onToggle(collection.rkey) },
                        label = { Text(collection.name) },
                        enabled = enabled,
                        trailingIcon = {
                            PlatformIcon(AppIcon.Close, stringResource(Res.string.gear_collections_remove_from, collection.name))
                        },
                    )
                }
            val others = collections.filter { !it.member }
            if (others.isNotEmpty()) {
                Box {
                    AppTextButton(onClick = { adding = true }, enabled = enabled) {
                        Text(stringResource(Res.string.gear_collections_add_to))
                    }
                    AppMenu(
                        expanded = adding,
                        onDismiss = { adding = false },
                        items = others.map { AppMenuItem(it.name) { onToggle(it.rkey) } }.toImmutableList(),
                    )
                }
            }
        }
    }
}
