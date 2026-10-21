package io.github.solcott.activitytracker.data.segments

import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.github.solcott.activitytracker.data.di.SessionClients
import io.github.solcott.activitytracker.data.di.SessionScope
import io.github.solcott.activitytracker.lexicon.AtUri
import io.github.solcott.activitytracker.lexicon.app.EffortView
import io.github.solcott.activitytracker.lexicon.app.GetActivitySegments
import io.github.solcott.activitytracker.lexicon.app.GetSegment
import io.github.solcott.activitytracker.lexicon.app.GetSegmentEfforts
import io.github.solcott.activitytracker.lexicon.app.SearchSegments
import io.github.solcott.activitytracker.lexicon.app.SegmentView

/** Segments and the efforts on them, read from the AppView (segments/segment-records, segments/segment-efforts). */
@SingleIn(SessionScope::class)
@Inject
class SegmentRepository(private val clients: SessionClients) {
    /** The segment at [uri], with the signed-in athlete's best effort on it. */
    suspend fun segment(uri: AtUri): SegmentView = clients.appView.query(GetSegment.method, GetSegment.Params(uri)).segment

    /** The efforts on [activity] the signed-in athlete may see, in the order they began. */
    suspend fun activitySegments(activity: AtUri): List<EffortView> =
        clients.appView.query(GetActivitySegments.method, GetActivitySegments.Params(activity)).efforts

    /** One page of [actor]'s efforts on [segment], fastest first; the signed-in athlete's own when [actor] is null. */
    suspend fun efforts(segment: AtUri, actor: String? = null, cursor: String? = null): GetSegmentEfforts.Output =
        clients.appView.query(GetSegmentEfforts.method, GetSegmentEfforts.Params(segment, actor ?: clients.did.value, cursor = cursor))

    /** One page of segments whose line passes through [params]' box. */
    suspend fun search(params: SearchSegments.Params): SearchSegments.Output = clients.appView.query(SearchSegments.method, params)
}
