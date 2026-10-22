package io.github.solcott.activitytracker.app.detail

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import io.github.solcott.activitytracker.app.segment.EffortRow
import io.github.solcott.activitytracker.app.ui.adaptive.AppCard
import io.github.solcott.activitytracker.composeApp.resources.Res
import io.github.solcott.activitytracker.composeApp.resources.detail_section_segments
import org.jetbrains.compose.resources.stringResource

/** The activity's segment efforts in a card after the laps, each opening its segment; nothing when it has none the viewer may see. */
internal fun LazyListScope.segments(state: ActivityDetailPage) {
    if (state.segments.isEmpty()) return
    item(key = "segments") {
        AppCard {
            Column(Modifier.padding(CARD_PADDING)) {
                Text(stringResource(Res.string.detail_section_segments), style = MaterialTheme.typography.titleMedium)
                state.segments.forEach { effort ->
                    EffortRow(
                        effort,
                        effort.segment.name,
                        onClick = { state.eventSink(ActivityDetailEvent.SegmentOpened(effort.segment.uri, effort.eBike == true)) },
                        showStatus = true,
                    )
                }
            }
        }
    }
}
