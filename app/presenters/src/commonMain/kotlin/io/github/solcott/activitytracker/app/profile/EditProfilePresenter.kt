package io.github.solcott.activitytracker.app.profile

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.slack.circuit.codegen.annotations.CircuitInject
import com.slack.circuit.runtime.Navigator
import io.github.solcott.activitytracker.app.UiError
import io.github.solcott.activitytracker.atproto.runSuspendCatching
import io.github.solcott.activitytracker.data.di.SessionScope
import io.github.solcott.activitytracker.data.profile.OwnProfile
import io.github.solcott.activitytracker.data.profile.ProfileEdit
import io.github.solcott.activitytracker.data.profile.ProfileRepository
import kotlinx.coroutines.launch

@CircuitInject(EditProfileScreen::class, SessionScope::class)
@Composable
fun EditProfilePresenter(navigator: Navigator, profiles: ProfileRepository): EditProfileState {
    var displayName by rememberSaveable { mutableStateOf("") }
    var description by rememberSaveable { mutableStateOf("") }
    // Carries the details through a name or bio save untouched.
    var own by remember { mutableStateOf<OwnProfile?>(null) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<UiError?>(null) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        runSuspendCatching { profiles.ownProfile() }
            .onSuccess { profile ->
                // Keep what the athlete typed before a configuration change.
                if (own == null && displayName.isEmpty() && description.isEmpty()) {
                    displayName = profile.displayName.orEmpty()
                    description = profile.description.orEmpty()
                }
                own = profile
            }
            .onFailure { error = UiError.of(UiError.Kind.LoadProfile, it) }
    }

    return EditProfileState(displayName, description, own != null, saving, error) { event ->
        when (event) {
            is EditProfileEvent.DisplayNameChanged -> {
                displayName = event.value
            }

            is EditProfileEvent.DescriptionChanged -> {
                description = event.value
            }

            EditProfileEvent.Back -> {
                navigator.pop()
            }

            EditProfileEvent.Save -> {
                val current = own
                if (current != null && !saving) {
                    saving = true
                    error = null
                    scope.launch {
                        runSuspendCatching {
                            profiles.save(ProfileEdit(displayName, description, current.details, current.visibility))
                        }
                            .onSuccess { navigator.pop() }
                            .onFailure { error = UiError.of(UiError.Kind.SaveProfile, it) }
                        saving = false
                    }
                }
            }
        }
    }
}
