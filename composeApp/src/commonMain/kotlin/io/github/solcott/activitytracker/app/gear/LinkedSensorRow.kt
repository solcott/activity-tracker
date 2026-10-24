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
import io.github.solcott.activitytracker.composeApp.resources.Res
import io.github.solcott.activitytracker.composeApp.resources.new_device_serial
import io.github.solcott.activitytracker.composeApp.resources.new_device_weak
import org.jetbrains.compose.resources.stringResource

/** A linked or ignored device: its maker and model, the end of its serial or that it may match other units, and an [action] button. */
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
            Text(deviceName(sensor.manufacturer, sensor.model), style = MaterialTheme.typography.bodyLarge)
            val detail =
                sensor.serialTail?.let { stringResource(Res.string.new_device_serial, it) }
                    ?: stringResource(Res.string.new_device_weak).takeIf { sensor.weak }
            detail?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        AppTextButton(onClick = onAction, Modifier.semantics { contentDescription = actionDescription }, enabled = enabled) { Text(action) }
    }
}
