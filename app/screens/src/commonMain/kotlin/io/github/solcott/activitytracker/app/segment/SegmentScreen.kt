package io.github.solcott.activitytracker.app.segment

import androidx.compose.runtime.Immutable
import com.slack.circuit.runtime.CircuitUiState
import com.slack.circuit.runtime.screen.Screen
import com.slack.circuit.serialization.CircuitSerializable
import io.github.solcott.activitytracker.app.UiError
import io.github.solcott.activitytracker.data.di.SessionScope
import io.github.solcott.activitytracker.lexicon.AtUri
import io.github.solcott.activitytracker.lexicon.app.EffortView
import io.github.solcott.activitytracker.lexicon.app.SegmentView
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

/** The segment at [uri] (segments/segment-efforts): its line, name, length and sport group, and the viewer's own efforts on it. */
@CircuitSerializable(SessionScope::class) data class SegmentScreen(val uri: String) : Screen

@Immutable
data class SegmentState(
    /** Null while loading or when it failed. */
    val segment: SegmentView? = null,
    /** The segment's line at precision 5, as the maps draw activity routes; null until loaded. */
    val polyline: String? = null,
    /** The viewer's own efforts on it, fastest first; empty while loading, when they have none, or when they failed to load. */
    val efforts: ImmutableList<EffortView> = persistentListOf(),
    val isLoading: Boolean = true,
    val error: UiError? = null,
    /**
     * Whether the viewer starred it (segments/segment-favorites); null while unknown (not yet read, unreadable, or this session can't use
     * stars), when the star isn't offered.
     */
    val starred: Boolean? = null,
    /** Credit for the data the segment's line came from; null for an athlete's segment, or until loaded. */
    val credit: SegmentCredit? = null,
    val eventSink: (SegmentEvent) -> Unit = {},
) : CircuitUiState

/**
 * Credit for the data a segment's line came from, by its record's `source` (segments/segment-records). Every screen that draws a segment's
 * line shows it: an OpenStreetMap line may be shown only with OSM's attribution.
 */
@Immutable
sealed interface SegmentCredit {
    /** `osm`: "© OpenStreetMap contributors", linking to OSM's copyright page, with the data's [license]. */
    data class OpenStreetMap(val license: String) : SegmentCredit

    /** `agency`: public agency trail data, under [license]. */
    data class Agency(val license: String) : SegmentCredit

    /** Any other source: only its licence [id], since the source is free text any client writes. */
    data class License(val id: String) : SegmentCredit
}

sealed interface SegmentEvent {
    data object Back : SegmentEvent

    /** Loads the segment again after [SegmentState.error]. */
    data object Retry : SegmentEvent

    /** Stars the segment, or unstars it when [SegmentState.starred]. */
    data object ToggleStar : SegmentEvent

    /** Opens the activity an effort came from. */
    data class OpenActivity(val uri: AtUri) : SegmentEvent

    /** Opens the earlier segment a duplicate duplicates. */
    data object OpenOriginal : SegmentEvent

    /** Opens the segment's leaderboard; offered only for an active segment. */
    data object OpenLeaderboard : SegmentEvent
}
