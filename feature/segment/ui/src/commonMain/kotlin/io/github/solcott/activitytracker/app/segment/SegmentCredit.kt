package io.github.solcott.activitytracker.app.segment

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalUriHandler
import io.github.solcott.activitytracker.app.ui.adaptive.AppTextButton
import io.github.solcott.activitytracker.app.ui.string

/**
 * Where the segment's line came from (segments/segment-records): OSM's attribution, linking to its copyright page, or the agency's, then the
 * licence; what it says is `SegmentCreditTexts`, which the native pages ask too.
 */
@Composable
internal fun Credit(credit: SegmentCredit) {
    val links = SegmentCreditTexts.links(credit)
    SegmentCreditTexts.lines(credit).forEachIndexed { index, line ->
        if (index == 0 && links) {
            val uriHandler = LocalUriHandler.current
            AppTextButton(onClick = { uriHandler.openUri(PlaceTexts.OSM_COPYRIGHT_URL) }) { Text(line.string()) }
        } else {
            CreditText(line.string())
        }
    }
}

@Composable
private fun CreditText(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}
