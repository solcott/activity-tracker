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
import io.github.solcott.activitytracker.data.profile.ProfileRepository
import kotlinx.coroutines.launch

@CircuitInject(EditProfileScreen::class, SessionScope::class)
@Composable
fun EditProfilePresenter(navigator: Navigator, profiles: ProfileRepository): EditProfileState {
    var displayName by rememberSaveable { mutableStateOf("") }
    var description by rememberSaveable { mutableStateOf("") }
    var loaded by rememberSaveable { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<UiError?>(null) }
    val scope = rememberCoroutineScope()

    // Reads the PDS rather than the AppView, which may not have indexed the latest edit yet.
    LaunchedEffect(Unit) {
        if (!loaded) {
            runSuspendCatching { profiles.ownProfileRecord() }
                .onSuccess { record ->
                    displayName = record?.displayName.orEmpty()
                    description = record?.description.orEmpty()
                    loaded = true
                }
                .onFailure { error = UiError.of(UiError.Kind.LoadProfile, it) }
        }
    }

    return EditProfileState(displayName, description, loaded, saving, error) { event ->
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
                if (loaded && !saving) {
                    saving = true
                    error = null
                    scope.launch {
                        runSuspendCatching { profiles.saveDetails(displayName, description) }
                            .onSuccess { navigator.pop() }
                            .onFailure { error = UiError.of(UiError.Kind.SaveProfile, it) }
                        saving = false
                    }
                }
            }
        }
    }
}
