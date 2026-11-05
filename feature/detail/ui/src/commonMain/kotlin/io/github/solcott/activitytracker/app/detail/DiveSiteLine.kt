package io.github.solcott.activitytracker.app.detail

import androidx.compose.foundation.clickable
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import io.github.solcott.activitytracker.app.segment.segmentStatusLabel
import io.github.solcott.activitytracker.app.ui.DiveSite
import io.github.solcott.activitytracker.app.ui.adaptive.handCursor

/**
 * The dive's site on its page (dive-sites/site-visits): the site it visits, by its current name and its status unless active, opening the
 * site's page; or the logbook's own site text when it visits none the viewer may see; nothing for neither.
 */
@Composable
internal fun DiveSiteLine(state: ActivityDetailPage, modifier: Modifier = Modifier) {
    val visited = state.activity.diveSite
    if (visited != null) {
        val status = segmentStatusLabel(visited.status)
        DiveSite(
            if (status == null) visited.name else "${visited.name} · $status",
            modifier.clickable(role = Role.Button) { state.eventSink(ActivityDetailEvent.OpenDiveSite) }.handCursor(),
        )
    } else {
        state.activity.record.dive?.site?.let { DiveSite(it, modifier) }
    }
}
