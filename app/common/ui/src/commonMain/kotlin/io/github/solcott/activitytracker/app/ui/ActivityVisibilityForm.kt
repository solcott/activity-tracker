package io.github.solcott.activitytracker.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.solcott.activitytracker.app.visibility.VisibilityEvent
import io.github.solcott.activitytracker.app.visibility.VisibilityFields
import io.github.solcott.activitytracker.app.visibility.VisibilityTexts
import io.github.solcott.activitytracker.composeApp.resources.Res
import io.github.solcott.activitytracker.composeApp.resources.activity_privacy_heading
import io.github.solcott.activitytracker.composeApp.resources.activity_visibility_heading
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/**
 * An activity form's Privacy section, set off by a hairline and its heading: who may see the activity ([label] names what it applies to,
 * one activity unless given), each of its detail categories ([DetailVisibilityPicker], folded under a summary until opened, offering only
 * the activity's visibility or narrower), then whatever else the form keeps private ([more], such as Edit's leaderboard choice), and the
 * section's note last. Changes go to the fields' sink. A session that can't choose shows Public, disabled, and the note says to sign in
 * again, or that the athlete's server doesn't support private activities yet.
 */
@Composable
fun ActivityVisibilityForm(
    fields: VisibilityFields,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    label: StringResource = Res.string.activity_visibility_heading,
    more: (@Composable ColumnScope.() -> Unit)? = null,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        HorizontalDivider(Modifier.padding(top = 8.dp), color = MaterialTheme.colorScheme.outlineVariant)
        Text(
            stringResource(Res.string.activity_privacy_heading),
            Modifier.padding(top = 8.dp, bottom = 4.dp).semantics { heading() },
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
        )
        VisibilityPicker(
            fields.activity,
            onSelect = { fields.eventSink(VisibilityEvent.ActivityChanged(it)) },
            approvesFollowers = fields.approvesFollowers,
            modifier = Modifier.fillMaxWidth(),
            enabled = enabled && fields.canChoose,
            label = stringResource(label),
        )
        DetailVisibilityPicker(fields, Modifier.fillMaxWidth(), enabled = enabled, collapsible = true)
        more?.invoke(this)
        Text(
            VisibilityTexts.activityNote(fields).string(),
            Modifier.padding(top = 4.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
