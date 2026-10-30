package io.github.solcott.activitytracker.app.moderation

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.Immutable
import com.slack.circuit.runtime.CircuitUiState
import com.slack.circuit.runtime.screen.Screen
import com.slack.circuit.serialization.CircuitSerializable
import io.github.solcott.activitytracker.app.UiError
import io.github.solcott.activitytracker.data.di.SessionScope
import io.github.solcott.activitytracker.lexicon.app.ActivityView
import io.github.solcott.activitytracker.lexicon.app.CommentView

/**
 * For the app's moderators, on desktop and web (`moderation/private-subjects`): opens a reported activity or comment by the AT-URI copied
 * from the labeler's queue, at any visibility while it's under review.
 */
@CircuitSerializable(SessionScope::class) data object ModerationScreen : Screen

@Immutable
data class ModerationState(
    /** The subject's AT-URI. */
    val uri: TextFieldState,
    val loading: Boolean,
    /** What the AppView answered for the last URI opened; null before one is. */
    val subject: ModerationSubject?,
    val error: UiError?,
    val eventSink: (ModerationEvent) -> Unit,
) : CircuitUiState

@Immutable
sealed interface ModerationSubject {
    /** The AppView answered not found: no such subject, or it isn't Public and isn't under review. */
    @Immutable data object NotUnderReview : ModerationSubject

    /** [visibility] is who may see it (a `Visibility` value). */
    @Immutable data class Activity(val activity: ActivityView, val visibility: String) : ModerationSubject

    @Immutable data class Comment(val comment: CommentView, val visibility: String) : ModerationSubject
}

sealed interface ModerationEvent {
    data object Back : ModerationEvent

    /** Opens the subject at the URI typed in [ModerationState.uri]. */
    data object Open : ModerationEvent
}
