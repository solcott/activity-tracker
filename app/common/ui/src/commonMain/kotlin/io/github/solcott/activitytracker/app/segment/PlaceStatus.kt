package io.github.solcott.activitytracker.app.segment

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.solcott.activitytracker.app.text.AppText
import io.github.solcott.activitytracker.app.ui.adaptive.AppTextButton
import io.github.solcott.activitytracker.app.ui.string

/** Who created a segment or dive site: "By @handle" ([PlaceTexts.createdBy]). */
@Composable
fun CreatedBy(handle: String, modifier: Modifier = Modifier) {
    Text(
        PlaceTexts.createdBy(handle).string(),
        modifier,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/**
 * A segment or dive site that isn't active: its status [label] and what it means ([help], both from [PlaceTexts]), and Open the original
 * when [offersOriginal]. Nothing for an active place, which has no [help].
 */
@Composable
fun PlaceStatus(
    label: AppText?,
    help: AppText?,
    offersOriginal: Boolean,
    onOpenOriginal: () -> Unit,
    modifier: Modifier = Modifier,
) {
    help ?: return
    Column(modifier.padding(top = 8.dp)) {
        label?.let { Text(it.string(), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.tertiary) }
        Text(help.string(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (offersOriginal) {
            AppTextButton(onClick = onOpenOriginal) { Text(PlaceTexts.openOriginal.string()) }
        }
    }
}
