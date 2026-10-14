package io.github.solcott.activitytracker.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.solcott.activitytracker.app.visibility.VisibilityFields
import io.github.solcott.activitytracker.composeApp.resources.Res
import io.github.solcott.activitytracker.composeApp.resources.activity_visibility_heading
import io.github.solcott.activitytracker.composeApp.resources.activity_visibility_help
import io.github.solcott.activitytracker.composeApp.resources.activity_visibility_sign_in_again
import io.github.solcott.activitytracker.composeApp.resources.charts_visibility_heading
import io.github.solcott.activitytracker.composeApp.resources.charts_visibility_help
import org.jetbrains.compose.resources.stringResource

/**
 * Who may see an activity and, when it has any ([hasCharts]), its charts: the charts picker offers only the activity's visibility or
 * narrower. A session that can't choose shows Public, disabled, and says to sign in again.
 */
@Composable
fun ActivityVisibilityForm(
    fields: VisibilityFields,
    hasCharts: Boolean,
    onActivityChange: (String) -> Unit,
    onChartsChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val canChoose = enabled && fields.canChoose
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(Res.string.activity_visibility_heading), style = MaterialTheme.typography.labelLarge)
        VisibilityPicker(
            fields.activity,
            onSelect = onActivityChange,
            approvesFollowers = fields.approvesFollowers,
            modifier = Modifier.fillMaxWidth(),
            enabled = canChoose,
        )
        Note(stringResource(if (fields.canChoose) Res.string.activity_visibility_help else Res.string.activity_visibility_sign_in_again))
        if (hasCharts) {
            Text(stringResource(Res.string.charts_visibility_heading), style = MaterialTheme.typography.labelLarge)
            VisibilityPicker(
                fields.charts,
                onSelect = onChartsChange,
                approvesFollowers = fields.approvesFollowers,
                modifier = Modifier.fillMaxWidth(),
                enabled = canChoose,
                options = fields.chartsOptions,
            )
            Note(stringResource(Res.string.charts_visibility_help))
        }
    }
}

@Composable
private fun Note(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}
