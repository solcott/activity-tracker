package io.github.solcott.activitytracker.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.solcott.activitytracker.app.visibility.VisibilityEvent
import io.github.solcott.activitytracker.app.visibility.VisibilityFields
import io.github.solcott.activitytracker.composeApp.resources.Res
import io.github.solcott.activitytracker.composeApp.resources.activity_visibility_heading
import io.github.solcott.activitytracker.composeApp.resources.activity_visibility_help
import io.github.solcott.activitytracker.composeApp.resources.activity_visibility_sign_in_again
import org.jetbrains.compose.resources.stringResource

/**
 * Who may see an activity and each of its detail categories ([DetailVisibilityPicker]), which offers only the activity's visibility or
 * narrower. Changes go to the fields' sink. A session that can't choose shows Public, disabled, and says to sign in again.
 */
@Composable
fun ActivityVisibilityForm(
    fields: VisibilityFields,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val canChoose = enabled && fields.canChoose
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(Res.string.activity_visibility_heading), style = MaterialTheme.typography.labelLarge)
        VisibilityPicker(
            fields.activity,
            onSelect = { fields.eventSink(VisibilityEvent.ActivityChanged(it)) },
            approvesFollowers = fields.approvesFollowers,
            modifier = Modifier.fillMaxWidth(),
            enabled = canChoose,
        )
        Note(stringResource(if (fields.canChoose) Res.string.activity_visibility_help else Res.string.activity_visibility_sign_in_again))
        DetailVisibilityPicker(fields, Modifier.fillMaxWidth(), enabled = enabled)
    }
}

@Composable
private fun Note(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}
