package io.github.solcott.activitytracker.app.profile

import androidx.compose.runtime.Immutable
import com.slack.circuit.runtime.CircuitUiState
import com.slack.circuit.runtime.screen.Screen
import com.slack.circuit.serialization.CircuitSerializable
import io.github.solcott.activitytracker.app.UiError
import io.github.solcott.activitytracker.data.di.SessionScope

/** Edit the signed-in athlete's display name and bio. */
@CircuitSerializable(SessionScope::class) data object EditProfileScreen : Screen

@Immutable
data class EditProfileState(
    val displayName: String,
    val description: String,
    /** False until the current profile record has been read, so a save can't blank fields the athlete never saw. */
    val loaded: Boolean,
    val saving: Boolean,
    val error: UiError?,
    val eventSink: (EditProfileEvent) -> Unit,
) : CircuitUiState {
    val valid: Boolean
        get() = displayName.length <= DISPLAY_NAME_MAX_LENGTH && description.length <= DESCRIPTION_MAX_LENGTH

    companion object {
        /**
         * The lexicon's `maxGraphemes`. Counting UTF-16 chars instead is stricter (an emoji can be two chars) but needs no grapheme
         * segmentation, and keeps well within the byte `maxLength`.
         */
        const val DISPLAY_NAME_MAX_LENGTH = 64
        const val DESCRIPTION_MAX_LENGTH = 256
    }
}

sealed interface EditProfileEvent {
    data class DisplayNameChanged(val value: String) : EditProfileEvent

    data class DescriptionChanged(val value: String) : EditProfileEvent

    data object Save : EditProfileEvent

    data object Back : EditProfileEvent
}
