package io.github.solcott.activitytracker.app.comments

import androidx.compose.runtime.Immutable
import com.slack.circuit.runtime.CircuitUiState
import com.slack.circuit.runtime.screen.Screen
import com.slack.circuit.serialization.CircuitSerializable
import io.github.solcott.activitytracker.app.UiError
import io.github.solcott.activitytracker.app.interactions.CommentsState
import io.github.solcott.activitytracker.app.report.Reporting
import io.github.solcott.activitytracker.app.text.AppText
import io.github.solcott.activitytracker.data.di.SessionScope
import io.github.solcott.uistate.ContentState

/**
 * A subject's comments on a screen of their own (feed/comments): an activity's or a post's, [subject] its AT-URI. [focus]: opened from the
 * page's "Add a comment", so the field takes focus once; it says only how the screen was reached, so its link leaves it out.
 */
@CircuitSerializable(SessionScope::class) data class CommentsScreen(val subject: String, val focus: Boolean = false) : Screen

/**
 * The Comments screen (ui/content-loading): [content]'s status and origin are the subject's load, which the count, its author's hides and a
 * reply's audience need; its data the page, null until the subject first loads.
 */
@Immutable
data class CommentsScreenState(
    val content: ContentState<CommentsPage?> = ContentState(null),
    /** Report on others' comments. */
    val reporting: Reporting = Reporting(),
    /** The field takes focus once, as the screen opens. */
    val focus: Boolean = false,
    /** The comments are on a post, not an activity: what a failed load says it couldn't load. */
    val onPost: Boolean = false,
    val eventSink: (CommentsScreenEvent) -> Unit = {},
) : CircuitUiState {
    /** The loaded page, typed for Swift, which can't read the erased [content]'s value; null until the subject first loads. */
    val page: CommentsPage?
        get() = content.data

    /** What couldn't be loaded when the subject's load fails. */
    val loadError: UiError.Kind
        get() = if (onPost) UiError.Kind.LoadPost else UiError.Kind.LoadActivity
}

/** The loaded Comments screen: the threads and the field ([comments]), and [subtitle] under the screen's title. */
@Immutable
data class CommentsPage(
    val comments: CommentsState,
    /** What the comments are on: the activity's title when it has one; null for a post. */
    val subtitle: AppText? = null,
) {
    /** The screen's title, "Comments". */
    val title: AppText
        get() = AppText.named("detail_section_comments")
}

/** The screen's own events; what the viewer does to the comments goes to [CommentsState.eventSink]. */
sealed interface CommentsScreenEvent {
    data object Back : CommentsScreenEvent

    /** Loads the subject again, after its load failed. */
    data object Retry : CommentsScreenEvent
}
