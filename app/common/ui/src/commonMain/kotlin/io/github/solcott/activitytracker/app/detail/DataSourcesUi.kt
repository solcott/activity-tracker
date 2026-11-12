package io.github.solcott.activitytracker.app.detail

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.github.solcott.activitytracker.app.ui.string
import io.github.solcott.activitytracker.lexicon.app.MemberView
import kotlinx.collections.immutable.ImmutableList

/**
 * A card's caption on a combined activity: the devices its data came from, in time order ([RecordingTexts.sourceCaption]). Each member is
 * named by device, or by sport for a file that named none, since its start may be hidden from the viewer.
 */
@Composable
internal fun ChartSourceCaption(members: ImmutableList<MemberView>, modifier: Modifier = Modifier) {
    val caption = RecordingTexts.sourceCaption(members) ?: return
    Text(
        caption.string(),
        modifier,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
