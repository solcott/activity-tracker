package io.github.solcott.activitytracker.app.segment

import androidx.compose.runtime.Immutable
import com.slack.circuit.runtime.CircuitUiState
import com.slack.circuit.runtime.screen.Screen
import com.slack.circuit.serialization.CircuitSerializable
import io.github.solcott.activitytracker.data.di.SessionScope
import io.github.solcott.activitytracker.lexicon.AtUri
import io.github.solcott.activitytracker.lexicon.app.EffortView
import io.github.solcott.activitytracker.lexicon.app.SegmentView
import io.github.solcott.uistate.ContentState
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

/** The segment at [uri] (segments/segment-efforts): its line, name, length and sport group, and the viewer's own efforts on it. */
@CircuitSerializable(SessionScope::class) data class SegmentScreen(val uri: String) : Screen

@Immutable
data class SegmentState(
    /** The segment, with whether it's loading or failed to; null until it first loads. */
    val content: ContentState<SegmentView?> = ContentState(null),
    /** The segment's line at precision 5, as the maps draw activity routes; null until loaded. */
    val polyline: String? = null,
    /** The viewer's own efforts on it, fastest first; empty while loading, when they have none, or when they failed to load. */
    val efforts: ImmutableList<EffortView> = persistentListOf(),
    /**
     * Whether the viewer starred it (segments/segment-favorites); null while unknown (not yet read, unreadable, or this session can't use
     * stars), when the star isn't offered.
     */
    val starred: Boolean? = null,
    val eventSink: (SegmentEvent) -> Unit = {},
) : CircuitUiState {
    /** [content]'s segment: the last loaded, or null before the first. */
    val segment: SegmentView?
        get() = content.data
}

sealed interface SegmentEvent {
    data object Back : SegmentEvent

    /** Loads the segment again after it failed to load ([SegmentState.content]). */
    data object Retry : SegmentEvent

    /** Stars the segment, or unstars it when [SegmentState.starred]. */
    data object ToggleStar : SegmentEvent

    /** Opens the activity an effort came from. */
    data class OpenActivity(val uri: AtUri) : SegmentEvent

    /** Opens the earlier segment a duplicate duplicates. */
    data object OpenOriginal : SegmentEvent
}
