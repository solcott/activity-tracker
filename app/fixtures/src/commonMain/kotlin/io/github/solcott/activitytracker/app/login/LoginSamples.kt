package io.github.solcott.activitytracker.app.login

import androidx.compose.foundation.text.input.TextFieldState
import io.github.solcott.activitytracker.app.fixtures.SampleKind
import io.github.solcott.activitytracker.data.auth.AuthCoordinator.Status
import io.github.solcott.dataresult.DataError

object LoginSamples {
    private fun state(status: Status, handle: String = "alice.test") = LoginState(TextFieldState(handle), status, eventSink = {})

    val all: Map<String, LoginState>
        get() =
            mapOf(
                SampleKind.LOADED to state(Status.Idle, handle = ""),
                "starting" to state(Status.Starting),
                "failed" to state(Status.Failed(Status.Stage.Start, DataError.Network)),
            )
}
