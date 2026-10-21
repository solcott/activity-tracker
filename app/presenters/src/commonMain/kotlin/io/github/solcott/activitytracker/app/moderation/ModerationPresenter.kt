package io.github.solcott.activitytracker.app.moderation

import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.slack.circuit.codegen.annotations.CircuitInject
import com.slack.circuit.runtime.Navigator
import io.github.solcott.activitytracker.app.UiError
import io.github.solcott.activitytracker.app.rememberSingleFlight
import io.github.solcott.activitytracker.atproto.runSuspendCatching
import io.github.solcott.activitytracker.data.di.SessionScope
import io.github.solcott.activitytracker.data.moderation.ModerationRepository
import io.github.solcott.activitytracker.lexicon.AtUri

@CircuitInject(ModerationScreen::class, SessionScope::class)
@Composable
fun ModerationPresenter(navigator: Navigator, moderation: ModerationRepository): ModerationState {
    val uri = rememberTextFieldState()
    var subject by remember { mutableStateOf<ModerationSubject?>(null) }
    var error by remember { mutableStateOf<UiError?>(null) }
    val flights = rememberSingleFlight()
    return ModerationState(uri, flights.isRunning(ModerationEvent.Open), subject, error) { event ->
        when (event) {
            ModerationEvent.Back -> navigator.pop()
            ModerationEvent.Open -> {
                val typed = uri.text.toString().trim()
                if (typed.isNotEmpty()) {
                    error = null
                    flights.launch(ModerationEvent.Open) {
                        runSuspendCatching { subjectOf(moderation, AtUri(typed)) }
                            .onSuccess { subject = it }
                            .onFailure {
                                subject = null
                                error = UiError.of(UiError.Kind.OpenSubject, it)
                            }
                    }
                }
            }
        }
    }
}

private suspend fun subjectOf(moderation: ModerationRepository, uri: AtUri): ModerationSubject {
    val output = moderation.subject(uri) ?: return ModerationSubject.NotUnderReview
    return output.activity?.let { ModerationSubject.Activity(it, output.visibility) }
        ?: output.comment?.let { ModerationSubject.Comment(it, output.visibility) }
        ?: ModerationSubject.NotUnderReview
}
