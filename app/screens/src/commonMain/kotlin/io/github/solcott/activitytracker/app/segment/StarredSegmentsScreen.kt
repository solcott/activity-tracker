package io.github.solcott.activitytracker.app.segment

import androidx.compose.runtime.Immutable
import com.slack.circuit.runtime.CircuitUiState
import com.slack.circuit.runtime.screen.Screen
import com.slack.circuit.serialization.CircuitSerializable
import io.github.solcott.activitytracker.app.UiError
import io.github.solcott.activitytracker.data.di.SessionScope
import io.github.solcott.activitytracker.lexicon.AtUri
import io.github.solcott.activitytracker.lexicon.app.SegmentView
import io.github.solcott.uistate.PagedState
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.persistentSetOf

/** The signed-in athlete's starred segments (segments/segment-favorites), newest star first, from their own profile. */
@CircuitSerializable(SessionScope::class) data object StarredSegmentsScreen : Screen

/** One star: the segment as the AppView serves it, or null when it's no longer available (deleted, never indexed or taken down). */
@Immutable data class StarredSegment(val uri: AtUri, val segment: SegmentView?)

@Immutable
data class StarredSegmentsState(
    /** The stars, a page at a time (ui/content-loading); nothing loads for a session that [needsSignIn]. */
    val segments: PagedState<StarredSegment, Unit>,
    /** Stars whose removal is being written. */
    val removing: ImmutableSet<AtUri> = persistentSetOf(),
    /** Why the last unstar failed; never a loading failure, which is [segments]'. */
    val actionError: UiError? = null,
    /** The session predates the grant stars need, so signing in again is the only way to see them. */
    val needsSignIn: Boolean = false,
    val eventSink: (StarredSegmentsEvent) -> Unit,
) : CircuitUiState

sealed interface StarredSegmentsEvent {
    data object Back : StarredSegmentsEvent

    /** Loads the next page; ignored while a page is loading or when there are no more. */
    data object LoadMore : StarredSegmentsEvent

    /** Tries the page that failed to load again. */
    data object Retry : StarredSegmentsEvent

    /** Opens an available segment's screen. */
    data class Open(val uri: AtUri) : StarredSegmentsEvent

    /** Unstars [uri]: it leaves the list at once. */
    data class Unstar(val uri: AtUri) : StarredSegmentsEvent
}
