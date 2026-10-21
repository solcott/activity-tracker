package io.github.solcott.activitytracker.app.segment

import androidx.compose.runtime.Immutable
import com.slack.circuit.runtime.CircuitUiState
import com.slack.circuit.runtime.screen.Screen
import com.slack.circuit.serialization.CircuitSerializable
import io.github.solcott.activitytracker.app.UiError
import io.github.solcott.activitytracker.data.di.SessionScope
import io.github.solcott.activitytracker.lexicon.AtUri
import io.github.solcott.activitytracker.lexicon.app.SegmentView
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.persistentSetOf

/** The signed-in athlete's starred segments (segments/segment-favorites), newest star first, from their own profile. */
@CircuitSerializable(SessionScope::class) data object StarredSegmentsScreen : Screen

/** One star: the segment as the AppView serves it, or null when it's no longer available (deleted, never indexed or taken down). */
@Immutable data class StarredSegment(val uri: AtUri, val segment: SegmentView?)

@Immutable
data class StarredSegmentsState(
    val segments: ImmutableList<StarredSegment>,
    val isLoading: Boolean,
    val canLoadMore: Boolean,
    /** Stars whose removal is being written. */
    val removing: ImmutableSet<AtUri> = persistentSetOf(),
    /** A load or removal that failed, or [UiError.Kind.SignInAgain] for a session that can't use stars. */
    val error: UiError?,
    val eventSink: (StarredSegmentsEvent) -> Unit,
) : CircuitUiState

sealed interface StarredSegmentsEvent {
    data object Back : StarredSegmentsEvent

    /** Loads the next page; ignored while a page is loading or when there are no more. */
    data object LoadMore : StarredSegmentsEvent

    /** Reloads from the first page after an error. */
    data object Retry : StarredSegmentsEvent

    /** Opens an available segment's screen. */
    data class Open(val uri: AtUri) : StarredSegmentsEvent

    /** Unstars [uri]: it leaves the list at once. */
    data class Unstar(val uri: AtUri) : StarredSegmentsEvent
}
