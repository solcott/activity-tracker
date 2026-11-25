package io.github.solcott.activitytracker.app.ui

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import io.github.solcott.activitytracker.app.MapSegmentLine
import io.github.solcott.activitytracker.app.ui.adaptive.AppIcon
import io.github.solcott.activitytracker.app.ui.adaptive.PlatformIcon
import io.github.solcott.activitytracker.composeApp.resources.Res
import io.github.solcott.activitytracker.composeApp.resources.trail_condition_closed
import io.github.solcott.activitytracker.location.GeoPoint
import io.github.solcott.activitytracker.location.TrackMath
import org.jetbrains.compose.resources.stringResource

/**
 * The label a map of segments puts on a line whose riders say it's closed (segments/trail-reports): "Closed" with the closed icon, in the
 * error colours, so it reads without colour too.
 */
@Composable
fun ClosedMapLabel(modifier: Modifier = Modifier) {
    Surface(
        modifier.testTag(CLOSED_MAP_LABEL_TAG).border(1.dp, MaterialTheme.colorScheme.error, LABEL_SHAPE),
        shape = LABEL_SHAPE,
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
    ) {
        Row(
            Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            PlatformIcon(AppIcon.Closed, contentDescription = null, size = 14.dp)
            Text(stringResource(Res.string.trail_condition_closed), style = MaterialTheme.typography.labelMedium)
        }
    }
}

/** Where a closed [line]'s label sits: the middle point of its line, or null when it isn't closed or its line doesn't decode. */
internal fun closedLabelAt(line: MapSegmentLine): GeoPoint? =
    line.takeIf { it.closed }?.let { TrackMath.decodePolylineOrNull(it.polyline) }?.takeIf { it.isNotEmpty() }?.let { it[it.size / 2] }

private val LABEL_SHAPE = RoundedCornerShape(6.dp)

/** Where a test finds a map's Closed labels. */
const val CLOSED_MAP_LABEL_TAG = "map-closed-label"
