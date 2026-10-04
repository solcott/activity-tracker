package io.github.solcott.activitytracker.app.login

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.slack.circuit.codegen.annotations.CircuitInject
import dev.zacsweers.metro.AppScope
import io.github.solcott.activitytracker.data.auth.AuthCoordinator.Status

@CircuitInject(LoginScreen::class, AppScope::class)
@Composable
fun LoginUi(state: LoginState, modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxSize().safeDrawingPadding().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Activity Tracker", style = MaterialTheme.typography.headlineMedium)
        Text(
            "Sign in with your ATProto account. Your activities are stored in your own repository.",
            style = MaterialTheme.typography.bodyMedium,
        )
        OutlinedTextField(
            value = state.handle,
            onValueChange = { state.eventSink(LoginEvent.HandleChanged(it)) },
            label = { Text("Handle (e.g. alice.test)") },
            singleLine = true,
            enabled = !state.busy,
            keyboardOptions =
                KeyboardOptions(
                    capitalization = KeyboardCapitalization.None,
                    autoCorrectEnabled = false,
                    keyboardType = KeyboardType.Uri,
                    imeAction = ImeAction.Go,
                ),
            keyboardActions = KeyboardActions(onGo = { state.eventSink(LoginEvent.Submit) }),
            modifier = Modifier.fillMaxWidth(),
        )
        when (val status = state.status) {
            is Status.Failed -> {
                Text(status.message, color = MaterialTheme.colorScheme.error)
            }

            Status.WaitingForBrowser -> {
                Text("Continue in your browser…")
                TextButton(onClick = { state.eventSink(LoginEvent.Cancel) }) { Text("Cancel") }
            }

            else -> Unit
        }
        if (state.busy) {
            CircularProgressIndicator()
        } else {
            Button(
                onClick = { state.eventSink(LoginEvent.Submit) },
                enabled = state.handle.isNotBlank(),
            ) {
                Text("Sign in")
            }
        }
    }
}
