package io.github.solcott.activitytracker.app.segment

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.Immutable
import com.slack.circuit.runtime.CircuitUiState
import com.slack.circuit.runtime.screen.Screen
import com.slack.circuit.serialization.CircuitSerializable
import io.github.solcott.activitytracker.app.UiError
import io.github.solcott.activitytracker.data.di.SessionScope
import io.github.solcott.activitytracker.lexicon.AtUri
import io.github.solcott.activitytracker.lexicon.app.SegmentView
import io.github.solcott.uistate.ContentState
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

/**
 * Creating a segment from the signed-in athlete's own activity at [activityUri] (segments/segment-creation): a stretch chosen along its
 * route as their privacy zones leave it, a name, and a duplicate check before it's published.
 */
@CircuitSerializable(SessionScope::class) data class CreateSegmentScreen(val activityUri: String) : Screen

@Immutable
data class CreateSegmentState(
    /**
     * The activity, its route and the privacy zones (ui/content-loading): loading or failed until all three first load, then loaded for
     * good, since a later load never changes the stretch being chosen.
     */
    val content: ContentState<Unit> = ContentState(Unit),
    /** What failed while [content] has failed: the activity, its route or the zones. */
    val loadErrorKind: UiError.Kind = UiError.Kind.LoadActivity,
    /** Why no segment can be made from this activity; null once it can, or while loading. */
    val unavailable: CreateSegmentUnavailable? = null,
    /** The activity's route as its privacy settings leave it, at precision 5, broken at [routeGaps]; null while loading. */
    val route: String? = null,
    /** Indices into [route] where it breaks, as `feed.activity#polylineGaps`. */
    val routeGaps: ImmutableList<Int>? = null,
    /** The segment's sport group, the activity's. */
    val sport: String? = null,
    /** The route's length, gaps left out, in meters: the range [start] and [end] are chosen in. */
    val lengthMeters: Float = 0f,
    /** The chosen stretch, in meters along the route. */
    val start: Float = 0f,
    val end: Float = 0f,
    /** Whether the stretch can be a segment, and how long it is. */
    val selection: SelectionCheck = SelectionCheck.CrossesGap,
    /** The chosen stretch at precision 5, drawn over [route]; null when it can't be a segment. */
    val highlight: String? = null,
    val name: TextFieldState = TextFieldState(),
    /** [name] has more than spaces. */
    val named: Boolean = false,
    /** The activity's route is public, so the segment names it as its source. */
    val namesActivity: Boolean = false,
    /** Existing segments the stretch duplicates, found by the last check; publishing is refused while there are any. */
    val duplicates: ImmutableList<SegmentView> = persistentListOf(),
    /** The athlete has created as many segments as they may today. */
    val limitReached: Boolean = false,
    /** Checking and publishing. */
    val publishing: Boolean = false,
    /** Why the last check or publish failed; never a loading failure, which is [content]'s. */
    val actionError: UiError? = null,
    val eventSink: (CreateSegmentEvent) -> Unit = {},
) : CircuitUiState {
    /** A stretch that can be a segment, a name, and nothing pending. */
    val canPublish: Boolean
        get() = selection is SelectionCheck.Ok && named && !publishing && !limitReached
}

/** Why a segment can't be created from an activity. */
enum class CreateSegmentUnavailable {
    /** It has no full track to cut a segment from. */
    NoRoute,

    /** Its sport has no sport group, such as an indoor or lift-served activity. */
    NoSportGroup,

    /** The privacy zones and hidden ends leave nothing of the route. */
    AllHidden,
}

/** Whether a chosen stretch can be a segment. */
@Immutable
sealed interface SelectionCheck {
    data class Ok(val meters: Double) : SelectionCheck

    /** Shorter than [minMeters], its sport group's minimum. */
    data class TooShort(val minMeters: Int) : SelectionCheck

    /** Longer than [maxMeters]. */
    data class TooLong(val maxMeters: Int) : SelectionCheck

    /** Spans a privacy zone, the hidden ends or a break in the recording. */
    data object CrossesGap : SelectionCheck
}

sealed interface CreateSegmentEvent {
    data object Back : CreateSegmentEvent

    /** Loads the activity, its route and the zones again after the first load failed. */
    data object Retry : CreateSegmentEvent

    /** The stretch moved to [start]..[end] meters along the route. */
    data class SelectionChanged(val start: Float, val end: Float) : CreateSegmentEvent

    /** Checks the stretch for duplicates and today's allowance, then publishes it and returns to the activity. */
    data object Publish : CreateSegmentEvent

    /** Opens an existing segment the stretch duplicates. */
    data class OpenSegment(val uri: AtUri) : CreateSegmentEvent
}
