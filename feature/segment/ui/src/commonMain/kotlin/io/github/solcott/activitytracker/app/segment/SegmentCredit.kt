package io.github.solcott.activitytracker.app.segment

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalUriHandler
import io.github.solcott.activitytracker.app.ui.adaptive.AppTextButton
import io.github.solcott.activitytracker.app.ui.string

/**
 * Where the segment's line came from (segments/segment-records): OSM's attribution, linking to its copyright page, or the agency, then the
 * licence ([SegmentPageTexts]).
 */
@Composable
internal fun Credit(credit: SegmentCredit) {
    SegmentPageTexts.osmCredit(credit)?.let { osm ->
        val uriHandler = LocalUriHandler.current
        AppTextButton(onClick = { uriHandler.openUri(PlaceTexts.OSM_COPYRIGHT_URL) }) { Text(osm.string()) }
    }
    SegmentPageTexts.agencyCredit(credit)?.let { CreditText(it.string()) }
    SegmentPageTexts.license(credit)?.let { CreditText(it.string()) }
}

@Composable
private fun CreditText(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}
