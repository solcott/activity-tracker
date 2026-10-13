package io.github.solcott.activitytracker.app.ui.adaptive

import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import kotlinx.collections.immutable.ImmutableList

@Composable
actual fun AppSegmentedControl(
    labels: ImmutableList<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier,
    enabled: Boolean,
) {
    SingleChoiceSegmentedButtonRow(modifier) {
        labels.forEachIndexed { index, label ->
            SegmentedButton(
                selected = index == selected,
                onClick = { onSelect(index) },
                shape = SegmentedButtonDefaults.itemShape(index, labels.size),
                enabled = enabled,
            ) {
                Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}
