package io.github.solcott.activitytracker.app.detail

import com.slack.circuit.runtime.CircuitUiState
import com.slack.circuit.runtime.screen.Screen
import com.slack.circuit.serialization.CircuitSerializable
import io.github.solcott.activitytracker.data.di.SessionScope
import io.github.solcott.activitytracker.lexicon.app.ActivityView
import io.github.solcott.activitytracker.lexicon.app.CommentView

@CircuitSerializable(SessionScope::class) data class ActivityDetailScreen(val uri: String) : Screen

sealed interface ActivityDetailState : CircuitUiState {
    val eventSink: (ActivityDetailEvent) -> Unit

    data class Loading(override val eventSink: (ActivityDetailEvent) -> Unit) : ActivityDetailState

    data class Error(val message: String, override val eventSink: (ActivityDetailEvent) -> Unit) : ActivityDetailState

    data class Loaded(
        val activity: ActivityView,
        val comments: List<CommentView>,
        val draftComment: String,
        override val eventSink: (ActivityDetailEvent) -> Unit,
    ) : ActivityDetailState
}

sealed interface ActivityDetailEvent {
    data object Back : ActivityDetailEvent

    data object Retry : ActivityDetailEvent

    data object ToggleKudos : ActivityDetailEvent

    data object OpenAuthor : ActivityDetailEvent

    data class DraftChanged(val text: String) : ActivityDetailEvent

    data object SendComment : ActivityDetailEvent
}
