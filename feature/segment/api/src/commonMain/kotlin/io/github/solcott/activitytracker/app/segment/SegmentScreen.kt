package io.github.solcott.activitytracker.app.segment

import androidx.compose.runtime.Immutable
import com.slack.circuit.runtime.CircuitUiState
import com.slack.circuit.runtime.screen.Screen
import com.slack.circuit.serialization.CircuitSerializable
import io.github.solcott.activitytracker.app.RouteMap
import io.github.solcott.activitytracker.app.chart.LineChart
import io.github.solcott.activitytracker.app.names.PlaceNameFields
import io.github.solcott.activitytracker.data.di.SessionScope
import io.github.solcott.activitytracker.data.units.UnitSystem
import io.github.solcott.activitytracker.lexicon.AtUri
import io.github.solcott.activitytracker.lexicon.app.EffortView
import io.github.solcott.activitytracker.lexicon.app.MayorEntry
import io.github.solcott.activitytracker.lexicon.app.SegmentKind
import io.github.solcott.activitytracker.lexicon.app.SegmentStatus
import io.github.solcott.activitytracker.lexicon.app.SegmentView
import io.github.solcott.uistate.ContentState
import kotlin.math.abs
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
    /** The segment's names (segments/segment-names): the sheet, a deletion being confirmed, the last failure, and their events. */
    val names: PlaceNameFields = PlaceNameFields(),
    /** The segment's terrain profile (segments/segment-elevation); null when the AppView sent none, or until loaded. */
    val profile: SegmentProfile? = null,
    val eventSink: (SegmentEvent) -> Unit = {},
) : CircuitUiState {
    /** [content]'s segment: the last loaded, or null before the first. */
    val segment: SegmentView?
        get() = content.data

    /** The segment's original name (its record's) when a community name replaced it, else null. */
    val originalName: String?
        get() = segment?.let { it.record.name.takeIf { original -> original != it.currentName } }

    /** Propose a name is offered: the AppView says the viewer may propose now. */
    val canPropose: Boolean
        get() = segment?.viewer?.canProposeName == true

    /** The viewer may vote ([SegmentView.viewer]'s flag); [PlaceNameFields.canVoteHere] says whether this session can send it. */
    val canVote: Boolean
        get() = segment?.viewer?.canVoteOnNames == true

    /** The segment's line as a route map, its start and end marked; null until loaded or for a line too short to draw. */
    val routeMap: RouteMap?
        get() = polyline?.let { RouteMap.of(it) }

    /** [profile] as a chart in [units], as both UIs draw it; null without a profile. */
    fun chart(units: UnitSystem): LineChart? = profile?.chart(units)

    /** A lift is matched but never ranked or renamed (segments/segment-lifts): no leaderboard, Mayor or names. */
    val isLift: Boolean
        get() = segment?.let { !SegmentKind.isRanked(it.record.kind) } == true

    /** The Leaderboard button shows: an active segment that isn't a lift. */
    val offersLeaderboard: Boolean
        get() = segment?.let { !isLift && SegmentStatus.of(it.status) == SegmentStatus.ACTIVE } == true
}

/**
 * A segment's terrain profile as the screen draws it (segments/segment-elevation): [heightsM] in meters, evenly spaced along its
 * [distanceMeters] from start to end, and each point's grade in percent over about 50 m, [gradesPercent], positive uphill.
 */
@Immutable
data class SegmentProfile(
    val heightsM: ImmutableList<Double>,
    val distanceMeters: Long,
    val gradesPercent: ImmutableList<Double>,
    /** The steepest absolute grade along the line, in percent, as the AppView measured it; null when it sent none. */
    val maxGradePercent: Double? = null,
) {
    /** How far apart the heights are, in meters. */
    val spacingMeters: Double
        get() = if (heightsM.size > 1) distanceMeters.toDouble() / (heightsM.size - 1) else 0.0

    /** The highest of [heightsM], in meters. */
    val highM: Double
        get() = heightsM.maxOrNull() ?: 0.0

    /** The lowest of [heightsM], in meters. */
    val lowM: Double
        get() = heightsM.minOrNull() ?: 0.0

    /** Each point's [GradeBand]. */
    val gradeBands: List<Int>
        get() = gradesPercent.map(GradeBand::of)
}

/** The five bands a profile is tinted by, from a point's absolute grade in percent: under 3, 3 to 6, 6 to 10, 10 to 15, and 15 or more. */
object GradeBand {
    /** Each band's lowest grade in percent, after the first. */
    val THRESHOLDS: ImmutableList<Double> = persistentListOf(3.0, 6.0, 10.0, 15.0)

    /** How many bands there are. */
    const val COUNT = 5

    /** The band of [gradePercent], whichever way the slope goes. */
    fun of(gradePercent: Double): Int = THRESHOLDS.count { abs(gradePercent) >= it }
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
