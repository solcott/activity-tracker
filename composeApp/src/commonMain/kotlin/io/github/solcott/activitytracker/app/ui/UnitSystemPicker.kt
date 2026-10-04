package io.github.solcott.activitytracker.app.ui

import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.github.solcott.activitytracker.composeApp.resources.Res
import io.github.solcott.activitytracker.composeApp.resources.units_imperial
import io.github.solcott.activitytracker.composeApp.resources.units_metric
import io.github.solcott.activitytracker.data.units.UnitSystem
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
    SingleChoiceSegmentedButtonRow(modifier) {
        options.forEachIndexed { index, units ->
            SegmentedButton(
                selected = units == selected,
                onClick = { onSelect(units) },
                shape = SegmentedButtonDefaults.itemShape(index, options.size),
                enabled = enabled,
            ) {
                Text(stringResource(unitSystemLabel(units)))
            }
        }
    }
}

private fun unitSystemLabel(units: UnitSystem) =
    when (units) {
        UnitSystem.Metric -> Res.string.units_metric
        UnitSystem.Imperial -> Res.string.units_imperial
    }
