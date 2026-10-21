package io.github.solcott.activitytracker.app.detail

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import io.github.solcott.activitytracker.atproto.runSuspendCatching
import io.github.solcott.activitytracker.data.segments.SegmentRepository
import io.github.solcott.activitytracker.lexicon.AtUri
import io.github.solcott.activitytracker.lexicon.app.ActivityRecord
import io.github.solcott.activitytracker.lexicon.app.EffortView
import io.github.solcott.activitytracker.lexicon.app.SegmentSport
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList

/**
 * Whether the viewer can cut a segment from [record]'s route: it's [own], its full track and their privacy zones were read, and its sport
 * has a group.
 */
internal fun canCreateSegment(own: Boolean, ownRoute: OwnRoute, record: ActivityRecord): Boolean =
    own && ownRoute.track != null && ownRoute.zonesRead && SegmentSport.of(record.sport, record.subSport) != null

/**
 * [activity]'s segment efforts, reloaded with [loadKey], in their own load: they're extra, so a slow or failed load never holds up the
 * page, and a failure shows no section.
 */
@Composable
internal fun rememberSegments(activity: AtUri, loadKey: Int, segments: SegmentRepository): ImmutableList<EffortView> {
    var efforts by remember { mutableStateOf<ImmutableList<EffortView>>(persistentListOf()) }
    LaunchedEffect(loadKey) {
        runSuspendCatching { segments.activitySegments(activity) }
            .onSuccess { efforts = it.toImmutableList() }
            .onFailure { efforts = persistentListOf() }
    }
    return efforts
}
