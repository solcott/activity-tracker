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
import io.github.solcott.activitytracker.lexicon.app.EffortView
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList

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
