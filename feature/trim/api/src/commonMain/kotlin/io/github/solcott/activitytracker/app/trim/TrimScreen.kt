package io.github.solcott.activitytracker.app.trim

import androidx.compose.runtime.Immutable
import com.slack.circuit.runtime.CircuitUiState
import com.slack.circuit.runtime.screen.Screen
import com.slack.circuit.serialization.CircuitSerializable
import io.github.solcott.activitytracker.app.RouteMap
import io.github.solcott.activitytracker.app.UiError
import io.github.solcott.activitytracker.data.activity.trim.KeptSpan
import io.github.solcott.activitytracker.data.activity.trim.TrimSuggestion
import io.github.solcott.activitytracker.data.activity.trim.TrimTotals
import io.github.solcott.activitytracker.data.di.SessionScope
import io.github.solcott.activitytracker.data.units.UnitSystem
import io.github.solcott.activitytracker.textfield.TextFieldState
import io.github.solcott.uistate.ContentState
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

/**
 * Trims the start or end of an activity (feed/activity-trim): the signed-in athlete's saved activity at [uri], or the unsaved one a
 * recording or an import offered under [draftId] (`TrimDrafts`), whose kept span goes back there instead of being written.
 */
@CircuitSerializable(SessionScope::class) data class TrimScreen(val uri: String? = null, val draftId: String? = null) : Screen

/**
 * The profile the handles move along, thinned for drawing: a value at each of [time] (seconds from the start), the elevation in meters when
 * [elevation], else the speed in meters per second, and the meters from the start at each when the activity has distance.
 */
@Immutable
data class TrimProfile(
    val time: ImmutableList<Int>,
    val values: ImmutableList<Double?>,
    val elevation: Boolean,
    val distance: ImmutableList<Double>?,
)

/** An effort the activity has on a segment: [name], from [from] to [to] seconds into the activity. */
@Immutable data class TrimEffort(val name: String, val from: Int, val to: Int)

/**
 * The trim being chosen: the kept span [from] to [to] (seconds since the start, of [elapsed] in all), chosen [byDistance] or by time, and
 * what it shows. A [manual] entry has no samples, so only its times move.
 */
@Immutable
data class TrimEditor(
    val elapsed: Int,
    val from: Int,
    val to: Int,
    val byDistance: Boolean,
    /** Distance can be chosen: the activity's streams or route say how far the athlete went. */
    val offersDistance: Boolean,
    val manual: Boolean,
    /** When the activity started, and the UTC offset its clock reads in (null: the device's). */
    val startedAtEpochSeconds: Long,
    val utcOffsetMinutes: Int?,
    /** Meters from the start at [from] and [to], and in all; null without distance. */
    val fromMeters: Double?,
    val toMeters: Double?,
    val totalMeters: Double?,
    /** The sport token, which says whether the average reads as a speed or a pace. */
    val sport: String,
    val before: TrimTotals,
    val after: TrimTotals,
    /** Null for a manual entry. */
    val profile: TrimProfile?,
    /** The athlete's whole route (an encoded polyline), and the kept part of it; null without GPS. */
    val polyline: String?,
    val kept: String?,
    val efforts: ImmutableList<TrimEffort> = persistentListOf(),
    /** What the app suggests cutting, null when nothing looks like junk; [suggestionApplied] while the span is its. */
    val suggestion: TrimSuggestion? = null,
) {
    val span: KeptSpan
        get() = KeptSpan(from, to)

    /** Seconds cut from the start and from the end. */
    val cutStart: Int
        get() = from

    val cutEnd: Int
        get() = elapsed - to

    /** Meters cut from the start and from the end; null without distance. */
    val cutStartMeters: Double?
        get() = fromMeters

    val cutEndMeters: Double?
        get() = if (totalMeters != null && toMeters != null) totalMeters - toMeters else null

    /** Something is cut, so the trim can be saved. */
    val changed: Boolean
        get() = from > 0 || to < elapsed

    val suggestionApplied: Boolean
        get() = suggestion != null && suggestion.span == span

    /** The efforts the kept span cuts into, which the trim removes. */
    val removedEfforts: List<TrimEffort>
        get() = efforts.filter { it.from < from || it.to > to }

    /** The map: the whole route dashed, the kept part over it; null without GPS. */
    val routeMap: RouteMap?
        get() = polyline?.let { RouteMap.of(it, highlight = kept) }
}

/** The exact value being typed for one end: the [start] or the end, in [field]. */
@Immutable data class TrimExactValue(val start: Boolean, val field: TextFieldState, val invalid: Boolean = false)

@Immutable
data class TrimState(
    /** The activity loading, failed, or loaded. */
    val content: ContentState<Unit>,
    /** Null until loaded, and when the activity can't be trimmed yet ([notSent]). */
    val editor: TrimEditor?,
    /** The activity is unsaved: Done keeps the span for it, with nothing written. */
    val draft: Boolean,
    /** The activity still has writes queued to be sent, so it can't be trimmed until they are. */
    val notSent: Boolean = false,
    /** The exact value sheet, while it's open. */
    val exact: TrimExactValue? = null,
    /** Asking before a saved activity's trim is written. */
    val confirming: Boolean = false,
    val saving: Boolean = false,
    /** Why a save failed. */
    val error: UiError? = null,
    val eventSink: (TrimEvent) -> Unit,
) : CircuitUiState {
    val canSave: Boolean
        get() = editor != null && !saving && (editor.changed || draft)
}

sealed interface TrimEvent {
    data object Back : TrimEvent

    data object Retry : TrimEvent

    /** The handles moved to [from] and [to] seconds; clamped to the activity, a minute apart, and snapped to samples by distance. */
    data class Moved(val from: Int, val to: Int) : TrimEvent

    /** Chooses by distance, or by time. */
    data class ModeChanged(val byDistance: Boolean) : TrimEvent

    data object ApplySuggestion : TrimEvent

    /** Back to the whole activity, from the suggestion or anything else. */
    data object Reset : TrimEvent

    /**
     * Moves the [start] or the end one small step [later] or earlier; [repeat] counts the steps of a held button (0 the press), which grow
     * as it is held ([TrimTexts.nudgeSeconds], [TrimTexts.nudgeMeters] in [units]).
     */
    data class Nudge(val start: Boolean, val later: Boolean, val repeat: Int, val units: UnitSystem) : TrimEvent

    /** Opens the exact value for the [start] or the end, filled in [units]. */
    data class EditExact(val start: Boolean, val units: UnitSystem) : TrimEvent

    /** Moves the end being edited one step later ([later]) or earlier: 10 s by time, 0.1 km or mi by distance in [units]. */
    data class StepExact(val later: Boolean, val units: UnitSystem) : TrimEvent

    /** Sets the end being edited to the value typed, read in [units]. */
    data class ApplyExact(val units: UnitSystem) : TrimEvent

    data object CancelExact : TrimEvent

    /** Saves: asks first for a saved activity, keeps the span for a draft. */
    data object Save : TrimEvent

    data object Confirm : TrimEvent

    data object CancelConfirm : TrimEvent
}
