package io.github.solcott.activitytracker.app.detail

import androidx.compose.runtime.Composable
import io.github.solcott.activitytracker.data.outcomeFlow
import io.github.solcott.activitytracker.data.segments.SegmentRepository
import io.github.solcott.activitytracker.lexicon.AtUri
import io.github.solcott.activitytracker.lexicon.app.ActivityRecord
import io.github.solcott.activitytracker.lexicon.app.SegmentSport
import io.github.solcott.uistate.circuit.produceRetainedContentState
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

/**
 * Whether the viewer can cut a segment from [record]'s route: it's [own], its full track and their privacy zones were read, and its sport
 * has a group.
 */
internal fun canCreateSegment(own: Boolean, ownRoute: OwnRoute, record: ActivityRecord): Boolean =
    own && ownRoute.track != null && ownRoute.zonesRead && SegmentSport.of(record.sport, record.subSport) != null

/**
 * [activity]'s segment efforts, grouped by overlapping spans ([groupEfforts]), reloaded with [loadKey] and retained with the page, in their
 * own load: they're extra, so a slow or failed load never holds up the page, and a failure keeps what was shown, quietly: none on a first
 * load, so no section.
 */
@Composable
internal fun rememberSegments(activity: AtUri, loadKey: Int, segments: SegmentRepository): ImmutableList<SegmentEffortGroup> =
    produceRetainedContentState<ImmutableList<SegmentEffortGroup>>(persistentListOf(), loadKey) {
            outcomeFlow { groupEfforts(segments.activitySegments(activity)) }
        }
        .data
