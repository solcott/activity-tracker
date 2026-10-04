package io.github.solcott.activitytracker.app.profile

import androidx.compose.runtime.Immutable
import com.slack.circuit.runtime.CircuitUiState
import com.slack.circuit.runtime.screen.Screen
import com.slack.circuit.serialization.CircuitSerializable
import io.github.solcott.activitytracker.data.di.SessionScope
import io.github.solcott.activitytracker.lexicon.app.ActivityView
import io.github.solcott.activitytracker.lexicon.app.ProfileView
import kotlinx.collections.immutable.ImmutableList

/** [actor] is a DID or handle. */
@CircuitSerializable(SessionScope::class) data class ProfileScreen(val actor: String) : Screen

@Immutable
data class ProfileState(
    val profile: ProfileView?,
    val activities: ImmutableList<ActivityView>,
    val isSelf: Boolean,
    val error: String?,
    val eventSink: (ProfileEvent) -> Unit,
) : CircuitUiState

sealed interface ProfileEvent {
    data object Back : ProfileEvent

    data object ToggleFollow : ProfileEvent

    data object OpenFollowers : ProfileEvent

    data object OpenFollowing : ProfileEvent

    data object SignOut : ProfileEvent

    data class Open(val activity: ActivityView) : ProfileEvent

    data class ToggleKudos(val activity: ActivityView) : ProfileEvent
}
