package io.github.solcott.activitytracker.app.segment

import androidx.compose.foundation.clickable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.Role
import io.github.solcott.activitytracker.app.ui.adaptive.HighlightPlacement
import io.github.solcott.activitytracker.app.ui.adaptive.highlight
import io.github.solcott.activitytracker.app.ui.adaptive.highlightInset
import io.github.solcott.activitytracker.app.ui.string

/**
 * Where the segment's line came from (segments/segment-records): OSM's attribution, linking to its copyright page, or the agency's, then
 * the licence; what it says is `SegmentCreditTexts`, which the native pages ask too.
 */
@Composable
internal fun Credit(credit: SegmentCredit) {
    val links = SegmentCreditTexts.links(credit)
    SegmentCreditTexts.lines(credit).forEachIndexed { index, line ->
        if (index == 0 && links) {
            val uriHandler = LocalUriHandler.current
            // A link in the credit's lines: its text lines up with theirs, and its highlight reaches past them.
            Text(
                line.string(),
                Modifier.highlight(placement = HighlightPlacement.Bleed)
                    .clickable(role = Role.Button) { uriHandler.openUri(PlaceTexts.OSM_COPYRIGHT_URL) }
                    .highlightInset(compact = true),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
        } else {
            CreditText(line.string())
        }
    }
}

@Composable
private fun CreditText(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}
