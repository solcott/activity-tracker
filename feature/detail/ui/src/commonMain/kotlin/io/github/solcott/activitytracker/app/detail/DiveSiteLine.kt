package io.github.solcott.activitytracker.app.detail

import androidx.compose.foundation.clickable
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import io.github.solcott.activitytracker.app.ui.DiveSite
import io.github.solcott.activitytracker.app.ui.adaptive.handCursor
import io.github.solcott.activitytracker.app.ui.string

/**
 * The dive's site on its page (dive-sites/site-visits): the site it visits, by its current name and its status unless active, opening the
 * site's page; or the logbook's own site text when it visits none the viewer may see; nothing for neither.
 */
@Composable
internal fun DiveSiteLine(state: ActivityDetailPage, modifier: Modifier = Modifier) {
    val site = ActivityPageTexts.diveSite(state.activity)?.string() ?: return
    if (ActivityPageTexts.opensDiveSite(state.activity)) {
        DiveSite(site, modifier.clickable(role = Role.Button) { state.eventSink(ActivityDetailEvent.OpenDiveSite) }.handCursor())
    } else {
        DiveSite(site, modifier)
    }
}
