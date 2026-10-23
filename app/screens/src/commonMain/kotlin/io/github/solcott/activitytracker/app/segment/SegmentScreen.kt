package io.github.solcott.activitytracker.app.segment

import androidx.compose.runtime.Immutable
import com.slack.circuit.runtime.CircuitUiState
import com.slack.circuit.runtime.screen.Screen
import com.slack.circuit.serialization.CircuitSerializable
import io.github.solcott.activitytracker.data.di.SessionScope
import io.github.solcott.activitytracker.lexicon.AtUri
import io.github.solcott.activitytracker.lexicon.app.EffortView
import io.github.solcott.activitytracker.lexicon.app.MayorEntry
import io.github.solcott.activitytracker.lexicon.app.SegmentView
import io.github.solcott.uistate.ContentState
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

/**
 * The segment at [uri] (segments/segment-efforts): its line, name, length and sport group, and the viewer's own efforts on it. [board] is
 * the board type (a `SegmentBoard` value) its Mayor card and boards open on: the type of the activity it was opened from, or null for the
 * AppView's default.
 */
@CircuitSerializable(SessionScope::class) data class SegmentScreen(val uri: String, val board: String? = null) : Screen

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
    /** Credit for the data the segment's line came from; null for an athlete's segment, or until loaded. */
    val credit: SegmentCredit? = null,
    /**
     * The segment's Mayor for the last 90 days and the viewer's own count (segments/segment-mayors); null when not shown: the segment isn't
     * active, or the board hasn't loaded or failed to.
     */
    val mayor: MayorSummary? = null,
    val eventSink: (SegmentEvent) -> Unit = {},
) : CircuitUiState {
    /** [content]'s segment: the last loaded, or null before the first. */
    val segment: SegmentView?
        get() = content.data
}

/** An active segment's Mayors board in brief: its [leader], null when it has no Mayor yet, and the [viewer]'s own entry when on it. */
@Immutable data class MayorSummary(val leader: MayorEntry?, val viewer: MayorEntry?)

/**
 * Credit for the data a segment's line came from, by its record's `source` (segments/segment-records). Every screen that draws a segment's
 * line shows it: an OpenStreetMap line may be shown only with OSM's attribution.
 */
@Immutable
sealed interface SegmentCredit {
    /** `osm`: "© OpenStreetMap contributors", linking to OSM's copyright page, with the data's [license]. */
    data class OpenStreetMap(val license: String) : SegmentCredit

    /** `agency`: trail data from the agency [attribution] names, or public agency trail data when it names none, under [license]. */
    data class Agency(val license: String, val attribution: String? = null) : SegmentCredit

    /** Any other source: only its licence [id], since the source is free text any client writes. */
    data class License(val id: String) : SegmentCredit
}

sealed interface SegmentEvent {
    data object Back : SegmentEvent

    /** Loads the segment again after it failed to load ([SegmentState.content]). */
    data object Retry : SegmentEvent

    /** Stars the segment, or unstars it when [SegmentState.starred]. */
    data object ToggleStar : SegmentEvent

    /** Opens the segment's line full screen (segments/segment-map-viewer). */
    data object OpenMap : SegmentEvent

    /** Opens the activity an effort came from. */
    data class OpenActivity(val uri: AtUri) : SegmentEvent

    /** Opens the earlier segment a duplicate duplicates. */
    data object OpenOriginal : SegmentEvent

    /** Opens the segment's leaderboard; offered only for an active segment. */
    data object OpenLeaderboard : SegmentEvent

    /** Opens the segment's Mayors board (segments/segment-mayors); offered only for an active segment. */
    data object OpenMayors : SegmentEvent
}
