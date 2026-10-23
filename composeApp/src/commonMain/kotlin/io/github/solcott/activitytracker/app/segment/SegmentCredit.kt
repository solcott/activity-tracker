package io.github.solcott.activitytracker.app.segment

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalUriHandler
import io.github.solcott.activitytracker.app.ui.adaptive.AppTextButton
import io.github.solcott.activitytracker.composeApp.resources.Res
import io.github.solcott.activitytracker.composeApp.resources.segment_credit_agency
import io.github.solcott.activitytracker.composeApp.resources.segment_credit_agency_named
import io.github.solcott.activitytracker.composeApp.resources.segment_credit_license
import io.github.solcott.activitytracker.composeApp.resources.segment_credit_osm
import org.jetbrains.compose.resources.stringResource

/** Where the segment's line came from (segments/segment-records): OSM's attribution, linking to its copyright page, or the licence. */
@Composable
internal fun Credit(credit: SegmentCredit) {
    val license =
        when (credit) {
            is SegmentCredit.OpenStreetMap -> {
                val uriHandler = LocalUriHandler.current
                AppTextButton(onClick = { uriHandler.openUri(OSM_COPYRIGHT_URL) }) { Text(stringResource(Res.string.segment_credit_osm)) }
                credit.license
            }
            is SegmentCredit.Agency -> {
                val text =
                    credit.attribution?.let { stringResource(Res.string.segment_credit_agency_named, it) }
                        ?: stringResource(Res.string.segment_credit_agency)
                CreditText(text)
                credit.license
            }
            is SegmentCredit.License -> credit.id
        }
    if (license.isNotBlank()) CreditText(stringResource(Res.string.segment_credit_license, license))
}

@Composable
private fun CreditText(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

/** OSM's attribution guideline asks for a link here. */
private const val OSM_COPYRIGHT_URL = "https://www.openstreetmap.org/copyright"
