package io.github.solcott.activitytracker.app.segment

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import io.github.solcott.activitytracker.app.ui.adaptive.AppTextButton
import io.github.solcott.activitytracker.app.ui.string
import kotlinx.collections.immutable.ImmutableList

/**
 * The Trail details section (segments/trail-attributes): a heading and one row per [rows] entry, its label beside what the data says. A
 * Website row is a link that opens its URL in the system browser, only after [TrailTexts.website] checks its scheme again. Nothing when
 * [rows] is empty.
 */
@Composable
internal fun TrailDetails(rows: ImmutableList<TrailDetail>, modifier: Modifier = Modifier) {
    if (rows.isEmpty()) return
    val uriHandler = LocalUriHandler.current
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(TrailTexts.title.string(), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 4.dp))
        for (row in rows) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    row.label.string(),
                    Modifier.width(LABEL_WIDTH),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                val link = TrailTexts.website(row.link)
                if (link != null) {
                    AppTextButton(onClick = { uriHandler.openUri(link) }) { Text(row.value.string()) }
                } else {
                    Text(row.value.string(), Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

/** The labels' column, wide enough for the longest ("Maintained by", "Typical grade") on one line. */
private val LABEL_WIDTH = 128.dp
