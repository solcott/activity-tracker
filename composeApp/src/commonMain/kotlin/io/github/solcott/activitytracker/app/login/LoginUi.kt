package io.github.solcott.activitytracker.app.login

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.slack.circuit.codegen.annotations.CircuitInject
import dev.zacsweers.metro.AppScope
import io.github.solcott.activitytracker.app.ui.adaptive.AppButton
import io.github.solcott.activitytracker.app.ui.adaptive.AppProgressIndicator
import io.github.solcott.activitytracker.app.ui.adaptive.AppTextButton
import io.github.solcott.activitytracker.app.ui.adaptive.AppTextField
import io.github.solcott.activitytracker.app.ui.adaptive.readableWidth
import io.github.solcott.activitytracker.composeApp.resources.Res
import io.github.solcott.activitytracker.composeApp.resources.action_cancel
import io.github.solcott.activitytracker.composeApp.resources.app_name
import io.github.solcott.activitytracker.composeApp.resources.error_sign_in_complete
import io.github.solcott.activitytracker.composeApp.resources.error_sign_in_start
import io.github.solcott.activitytracker.composeApp.resources.login_handle_label
import io.github.solcott.activitytracker.composeApp.resources.login_intro
import io.github.solcott.activitytracker.composeApp.resources.login_sign_in
import io.github.solcott.activitytracker.composeApp.resources.login_waiting_for_browser
import io.github.solcott.activitytracker.data.auth.AuthCoordinator.Status
import org.jetbrains.compose.resources.stringResource

@CircuitInject(LoginScreen::class, AppScope::class)
@Composable
fun LoginUi(state: LoginState, modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxSize().safeDrawingPadding().readableWidth(LOGIN_MAX_WIDTH).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            stringResource(Res.string.app_name),
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            stringResource(Res.string.login_intro),
            style = MaterialTheme.typography.bodyMedium,
        )
        AppTextField(
            state = state.handle,
            label = stringResource(Res.string.login_handle_label),
            lineLimits = TextFieldLineLimits.SingleLine,
            enabled = !state.busy,
            keyboardOptions =
                KeyboardOptions(
                    capitalization = KeyboardCapitalization.None,
                    autoCorrectEnabled = false,
                    keyboardType = KeyboardType.Uri,
                    imeAction = ImeAction.Go,
                ),
            onKeyboardAction = { state.eventSink(LoginEvent.Submit) },
            modifier = Modifier.fillMaxWidth(),
        )
        when (val status = state.status) {
            is Status.Failed -> {
                Text(failedText(status), color = MaterialTheme.colorScheme.error)
            }

            Status.WaitingForBrowser -> {
                Text(stringResource(Res.string.login_waiting_for_browser))
                AppTextButton(onClick = { state.eventSink(LoginEvent.Cancel) }) { Text(stringResource(Res.string.action_cancel)) }
            }

            else -> Unit
        }
        if (state.busy) {
            AppProgressIndicator()
        } else {
            AppButton(
                onClick = { state.eventSink(LoginEvent.Submit) },
                enabled = state.handle.text.isNotBlank(),
            ) {
                Text(stringResource(Res.string.login_sign_in))
            }
        }
    }
}

/** A phone-width form, centred, on desktop and web. */
private val LOGIN_MAX_WIDTH = 420.dp

@Composable
private fun failedText(status: Status.Failed): String =
    status.detail
        ?: stringResource(
            when (status.stage) {
                Status.Stage.Start -> Res.string.error_sign_in_start
                Status.Stage.Complete -> Res.string.error_sign_in_complete
            }
        )
