package io.github.solcott.activitytracker.app.feed

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.slack.circuit.codegen.annotations.CircuitInject
import com.slack.circuit.runtime.Navigator
import io.github.solcott.activitytracker.app.UiError
import io.github.solcott.activitytracker.app.rememberSingleFlight
import io.github.solcott.activitytracker.atproto.runSuspendCatching
import io.github.solcott.activitytracker.data.activity.RejectedChange
import io.github.solcott.activitytracker.data.activity.RejectedChanges
import io.github.solcott.activitytracker.data.di.SessionScope
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.map

/**
 * The changes the athlete's server refused, read live from the outbox, so a retry the server accepts or a discard removes its item. Retry
 * and Discard each run once per change at a time; a discard asks first.
 */
@CircuitInject(UnsentChangesScreen::class, SessionScope::class)
@Composable
fun UnsentChangesPresenter(navigator: Navigator, rejected: RejectedChanges): UnsentChangesState {
    val changes by remember {
        rejected.changes.map { it.toImmutableList() }
    }
        .collectAsState<ImmutableList<RejectedChange>, ImmutableList<RejectedChange>?>(null)
    var confirming by remember { mutableStateOf<RejectedChange?>(null) }
    var error by remember { mutableStateOf<UiError?>(null) }
    val flights = rememberSingleFlight()
    val busy = changes.orEmpty().map { it.rkey }.filter { flights.isRunning(it) }.toImmutableList()
    return UnsentChangesState(changes, confirming, busy, error) { event ->
        when (event) {
            UnsentChangesEvent.Back -> navigator.pop()

            is UnsentChangesEvent.Retry -> {
                val change = changes?.firstOrNull { it.rkey == event.rkey } ?: return@UnsentChangesState
                error = null
                flights.launch(change.rkey) {
                    runSuspendCatching { rejected.retry(change) }.onFailure { error = UiError.of(UiError.Kind.RetryChange, it) }
                }
            }

            is UnsentChangesEvent.Discard -> {
                confirming = changes?.firstOrNull { it.rkey == event.rkey }
            }

            UnsentChangesEvent.CancelDiscard -> {
                confirming = null
            }

            is UnsentChangesEvent.ConfirmDiscard -> {
                val change = changes?.firstOrNull { it.rkey == event.rkey } ?: return@UnsentChangesState
                confirming = null
                error = null
                flights.launch(change.rkey) {
                    runSuspendCatching { rejected.discard(change) }.onFailure { error = UiError.of(UiError.Kind.DiscardChange, it) }
                }
            }
        }
    }
}
