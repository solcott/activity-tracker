package io.github.solcott.activitytracker.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.solcott.activitytracker.app.record.GearTimePreview
import kotlinx.collections.immutable.ImmutableList

/** A bar per rear gear, as tall as the time spent in it, labelled by gear number. */
@Composable
fun GearTimeChart(
    gears: ImmutableList<GearTimePreview>,
    description: String,
    modifier: Modifier = Modifier,
    height: Dp = 72.dp,
) {
    val longest = gears.maxOfOrNull { it.seconds }?.takeIf { it > 0 } ?: return
    val bar = MaterialTheme.colorScheme.primary
    Row(
        modifier.fillMaxWidth().clearAndSetSemantics { contentDescription = description },
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        gears.forEach { gear ->
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.height(height), contentAlignment = Alignment.BottomCenter) {
                    Box(
                        Modifier.fillMaxWidth()
                            .height(height * (gear.seconds.toFloat() / longest).coerceAtLeast(MIN_BAR))
                            .background(bar, RoundedCornerShape(topStart = 2.dp, topEnd = 2.dp))
                    )
                }
                Text(gear.gear.toString(), style = MaterialTheme.typography.labelSmall, maxLines = 1)
            }
        }
    }
}

/** A gear used for a moment still shows. */
private const val MIN_BAR = 0.02f
