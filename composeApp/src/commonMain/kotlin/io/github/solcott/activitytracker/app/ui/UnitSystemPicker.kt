package io.github.solcott.activitytracker.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.github.solcott.activitytracker.app.ui.adaptive.AppSegmentedControl
import io.github.solcott.activitytracker.composeApp.resources.Res
import io.github.solcott.activitytracker.composeApp.resources.units_imperial
import io.github.solcott.activitytracker.composeApp.resources.units_metric
import io.github.solcott.activitytracker.data.units.UnitSystem
import kotlinx.collections.immutable.persistentListOf
import org.jetbrains.compose.resources.stringResource

/** A Metric / Imperial segmented choice. */
@Composable
fun UnitSystemPicker(
    selected: UnitSystem,
    onSelect: (UnitSystem) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val options = UnitSystem.entries
    AppSegmentedControl(
        labels = persistentListOf(stringResource(Res.string.units_metric), stringResource(Res.string.units_imperial)),
        selected = options.indexOf(selected),
        onSelect = { onSelect(options[it]) },
        modifier = modifier,
        enabled = enabled,
    )
}
