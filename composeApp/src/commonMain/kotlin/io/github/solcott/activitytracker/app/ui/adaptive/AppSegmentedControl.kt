package io.github.solcott.activitytracker.app.ui.adaptive

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.solcott.activitytracker.app.ui.theme.LocalUiPlatform
import io.github.solcott.activitytracker.app.ui.theme.UiPlatform
import kotlinx.collections.immutable.ImmutableList

/**
 * A single choice among a few [labels]: Material's segmented buttons, or a `UISegmentedControl`-style track on iOS. [selected] is an index
 * into [labels], or -1 for none.
 */
@Composable
fun AppSegmentedControl(
    labels: ImmutableList<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    if (LocalUiPlatform.current == UiPlatform.Ios) {
        IosSegmentedControl(labels, selected, onSelect, modifier, enabled)
    } else {
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
}

@Composable
private fun IosSegmentedControl(
    labels: ImmutableList<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val dark = isSystemInDarkTheme()
    // UIKit's tertiarySystemFill track and its raised thumb.
    val track = if (dark) Color(0x3D767680) else Color(0x1F767680)
    val thumb = if (dark) Color(0xFF636366) else Color.White
    Row(
        modifier
            .height(IOS_HEIGHT)
            .alpha(if (enabled) 1f else DISABLED_ALPHA)
            .background(track, RoundedCornerShape(IOS_RADIUS))
            .padding(2.dp)
    ) {
        labels.forEachIndexed { index, label ->
            val isSelected = index == selected
            Box(
                Modifier.weight(1f)
                    .fillMaxHeight()
                    .then(
                        if (isSelected) {
                            Modifier.shadow(2.dp, RoundedCornerShape(IOS_THUMB_RADIUS))
                                .background(thumb, RoundedCornerShape(IOS_THUMB_RADIUS))
                        } else {
                            Modifier
                        }
                    )
                    .clickable(enabled = enabled && !isSelected, role = Role.RadioButton) { onSelect(index) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label,
                    style =
                        MaterialTheme.typography.labelMedium.copy(fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 6.dp),
                )
            }
        }
    }
}

private val IOS_HEIGHT = 32.dp
private val IOS_RADIUS = 9.dp
private val IOS_THUMB_RADIUS = 7.dp
private const val DISABLED_ALPHA = 0.4f
