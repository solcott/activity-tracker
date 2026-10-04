package io.github.solcott.activitytracker.app.detail

import com.slack.circuit.runtime.CircuitUiState
import com.slack.circuit.runtime.screen.Screen
import com.slack.circuit.serialization.CircuitSerializable
import io.github.solcott.activitytracker.data.di.SessionScope
import io.github.solcott.activitytracker.lexicon.Did
import io.github.solcott.activitytracker.lexicon.app.ActivityView
import io.github.solcott.activitytracker.lexicon.app.CommentView
import io.github.solcott.activitytracker.lexicon.app.ProfileViewBasic

@CircuitSerializable(SessionScope::class) data class ActivityDetailScreen(val uri: String) : Screen

sealed interface ActivityDetailState : CircuitUiState {
    val eventSink: (ActivityDetailEvent) -> Unit

    data class Loading(override val eventSink: (ActivityDetailEvent) -> Unit) : ActivityDetailState

    data class Error(val message: String, override val eventSink: (ActivityDetailEvent) -> Unit) : ActivityDetailState

    data class Loaded(
        val activity: ActivityView,
        val comments: List<CommentView>,
        val draftComment: String,
        /** The activity is the signed-in athlete's own, so it can't take kudos. */
        val isOwn: Boolean,
        /** Who gave kudos, once the viewer has shown the list; null while it's hidden. */
        val kudosList: KudosList?,
        override val eventSink: (ActivityDetailEvent) -> Unit,
    ) : ActivityDetailState
}

data class KudosList(
    val athletes: List<ProfileViewBasic>,
    val isLoading: Boolean,
    val error: String?,
)

sealed interface ActivityDetailEvent {
    data object Back : ActivityDetailEvent

    data object Retry : ActivityDetailEvent

    data object ToggleKudos : ActivityDetailEvent

    data object OpenAuthor : ActivityDetailEvent

    /** Shows or hides who gave kudos; showing it reloads the list. */
    data object ToggleKudosList : ActivityDetailEvent

    data class OpenProfile(val did: Did) : ActivityDetailEvent

    data class DraftChanged(val text: String) : ActivityDetailEvent

    data object SendComment : ActivityDetailEvent
}
