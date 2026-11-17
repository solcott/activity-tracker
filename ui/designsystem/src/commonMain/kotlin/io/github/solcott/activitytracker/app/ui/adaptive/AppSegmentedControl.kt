package io.github.solcott.activitytracker.app.ui.adaptive

import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import kotlinx.collections.immutable.ImmutableList

/** A single choice among a few [labels]: Material's segmented buttons. [selected] is an index into [labels], or -1 for none. */
@Composable
fun AppSegmentedControl(
    labels: ImmutableList<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    SingleChoiceSegmentedButtonRow(modifier) {
        labels.forEachIndexed { index, label ->
            SegmentedButton(
                selected = index == selected,
                onClick = { onSelect(index) },
                shape = SegmentedButtonDefaults.itemShape(index, labels.size),
                // Each choice takes width in proportion to its label, so a long one ("Trails & roads") isn't cut off beside short ones.
                modifier = Modifier.weight((label.length + CHECK_WEIGHT).toFloat()),
                enabled = enabled,
            ) {
                Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/** Room for the selected choice's check, in characters, given to every choice so the widths stay put as the selection moves. */
private const val CHECK_WEIGHT = 4
