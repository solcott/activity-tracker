package io.github.solcott.activitytracker.app.segment

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.solcott.activitytracker.app.ui.adaptive.AppTextButton
import io.github.solcott.activitytracker.app.ui.handleLabel
import io.github.solcott.activitytracker.composeApp.resources.Res
import io.github.solcott.activitytracker.composeApp.resources.segment_created_by
import io.github.solcott.activitytracker.composeApp.resources.segment_open_original
import io.github.solcott.activitytracker.lexicon.app.SegmentStatus
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/** Who created a segment or dive site: "Created by @handle". */
@Composable
fun CreatedBy(handle: String, modifier: Modifier = Modifier) {
    Text(
        stringResource(Res.string.segment_created_by, handleLabel(handle)),
        modifier,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/**
 * A segment or dive site that isn't active: its [status] label and what it means, from [proposedHelp], [duplicateHelp] or [archivedHelp],
 * and for a duplicate with an original ([hasOriginal]) a link to it. Nothing for an active place.
 */
@Composable
fun PlaceStatus(
    status: String?,
    hasOriginal: Boolean,
    proposedHelp: StringResource,
    duplicateHelp: StringResource,
    archivedHelp: StringResource,
    onOpenOriginal: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val known = SegmentStatus.of(status)
    val help =
        when (known) {
            SegmentStatus.PROPOSED -> proposedHelp
            SegmentStatus.DUPLICATE -> duplicateHelp
            SegmentStatus.ARCHIVED -> archivedHelp
            else -> return
        }
    Column(modifier.padding(top = 8.dp)) {
        segmentStatusLabel(status)?.let {
            Text(it, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.tertiary)
        }
        Text(stringResource(help), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (known == SegmentStatus.DUPLICATE && hasOriginal) {
            AppTextButton(onClick = onOpenOriginal) { Text(stringResource(Res.string.segment_open_original)) }
        }
    }
}
