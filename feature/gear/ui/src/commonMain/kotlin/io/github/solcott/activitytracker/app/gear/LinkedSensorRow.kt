package io.github.solcott.activitytracker.app.gear

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.solcott.activitytracker.app.ui.adaptive.AppTextButton
import io.github.solcott.activitytracker.app.ui.string

/**
 * A linked or ignored device: its maker and model, the end of its serial or that it may match other units ([LinkedSensorTexts]), and an
 * [action] button read aloud as [actionDescription].
 */
@Composable
internal fun LinkedSensorRow(
    sensor: LinkedSensor,
    action: String,
    actionDescription: String,
    onAction: () -> Unit,
    enabled: Boolean = true,
) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(LinkedSensorTexts.name(sensor).string(), style = MaterialTheme.typography.bodyLarge)
            LinkedSensorTexts.detail(sensor)?.let {
                Text(it.string(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        AppTextButton(onClick = onAction, Modifier.semantics { contentDescription = actionDescription }, enabled = enabled) { Text(action) }
    }
}
