package io.github.solcott.activitytracker.app.login

import io.github.solcott.activitytracker.app.fixtures.SampleKind
import io.github.solcott.activitytracker.data.auth.AuthCoordinator.Status
import io.github.solcott.activitytracker.textfield.textFieldStateOf
import io.github.solcott.dataresult.DataError

object LoginSamples {
    private fun state(
        status: Status,
        handle: String = "alice.test",
        expired: Expiry? = null,
        signingInAgain: Boolean = false,
        leftHandle: String? = null,
    ) = LoginState(textFieldStateOf(handle), status, expired, signingInAgain, leftHandle, eventSink = {})

    val all: Map<String, LoginState>
        get() =
            mapOf(
                SampleKind.LOADED to state(Status.Idle, handle = ""),
                "starting" to state(Status.Starting),
                "failed" to state(Status.Failed(Status.Stage.Start, DataError.Network)),
                "expired" to state(Status.Idle, expired = Expiry(recording = false)),
                "expiredRecording" to state(Status.Idle, expired = Expiry(recording = true)),
                "signInAgain" to state(Status.Idle, signingInAgain = true),
                "left" to state(Status.Idle, handle = "", leftHandle = "alice.test"),
            )
}
