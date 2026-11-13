package io.github.solcott.activitytracker.app.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.solcott.activitytracker.app.text.AppText
import io.github.solcott.activitytracker.app.text.LabelText
import io.github.solcott.activitytracker.app.ui.string
import kotlinx.collections.immutable.ImmutableList

/**
 * [lines] as a bulleted list: the shared mark (`LabelText.bullet`), then each line, which wraps under itself rather than under the mark, as
 * the native `BulletList` draws it (dev/shared-display-text).
 */
@Composable
internal fun Bullets(lines: ImmutableList<AppText>, modifier: Modifier = Modifier) {
    val body = MaterialTheme.typography.bodyMedium
    val mark = LabelText.bullet.string()
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        lines.forEach { line ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(mark, style = body)
                Text(line.string(), style = body)
            }
        }
    }
}
