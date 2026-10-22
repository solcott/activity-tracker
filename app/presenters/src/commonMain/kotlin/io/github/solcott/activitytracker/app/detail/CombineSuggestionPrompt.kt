package io.github.solcott.activitytracker.app.detail

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import com.slack.circuit.retained.rememberRetained
import com.slack.circuit.runtime.Navigator
import io.github.solcott.activitytracker.app.SingleFlight
import io.github.solcott.activitytracker.app.UiError
import io.github.solcott.activitytracker.atproto.runSuspendCatching
import io.github.solcott.activitytracker.data.activity.ActivityManager
import io.github.solcott.activitytracker.data.activity.CombineSubject
import io.github.solcott.activitytracker.data.activity.CombineSuggestion
import io.github.solcott.activitytracker.data.activity.CombineSuggestions
import io.github.solcott.activitytracker.data.outcomeFlow
import io.github.solcott.activitytracker.lexicon.app.ActivityView
import io.github.solcott.uistate.circuit.produceRetainedContentState
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.filterNotNull

/** The owner's combine suggestion for the page's activity: [state] is null without one. */
internal class CombineSuggestionPrompt(val state: CombineSuggestionState?, private val on: (ActivityDetailEvent) -> Unit) {
    fun on(event: ActivityDetailEvent) = on.invoke(event)
}

/**
 * Looks for a combine suggestion for the owner's [activity] (null on someone else's), again with [loadKey], shown as a prompt while
 * [suggestOnOpen] and not yet answered, then as a banner. It's extra: a failed lookup shows none, quietly. Combine combines them and opens
 * the combined activity in this page's place; Don't combine remembers the pair.
 */
@Composable
@Suppress("LongParameterList") // Injected collaborators.
internal fun rememberCombineSuggestion(
    activity: ActivityView?,
    loadKey: Int,
    suggestOnOpen: Boolean,
    suggestions: CombineSuggestions,
    manager: ActivityManager,
    navigator: Navigator,
    flights: SingleFlight,
): CombineSuggestionPrompt {
    val latest by rememberUpdatedState(activity)
    // Retained with the page, so coming back shows it, and an answer to it, as left.
    val found = remember {
        snapshotFlow { latest }.filterNotNull().distinctUntilChangedBy { it.uri }
    }
        .produceRetainedContentState<ActivityView, CombineSuggestion?>(null, loadKey) { own ->
            outcomeFlow { suggestions.forActivity(own) }
        }
    var dismissed by rememberRetained { mutableStateOf(false) }
    var putOff by rememberRetained { mutableStateOf(false) }
    var error by remember { mutableStateOf<UiError?>(null) }
    val current = found.data?.takeIf { !dismissed }
    val state =
        if (activity == null || current == null) null
        else
            CombineSuggestionState(
                cue = current.cue,
                other = current.shown,
                asPrompt = suggestOnOpen && !putOff,
                busy =
                    flights.isRunning(ActivityDetailEvent.CombineSuggested) || flights.isRunning(ActivityDetailEvent.DontCombineSuggestion),
                error = error,
            )
    return CombineSuggestionPrompt(state) { event ->
        if (activity == null || current == null) return@CombineSuggestionPrompt
        when (event) {
            ActivityDetailEvent.CombineSuggested ->
                flights.launch(event) {
                    error = null
                    runSuspendCatching {
                        val (into, other) = suggestions.targets(activity, current)
                        manager.combine(into, other)
                    }
                        .onSuccess { combined ->
                            // This page now shows a member: the combined activity's takes its place.
                            navigator.pop()
                            navigator.goTo(ActivityDetailScreen(combined.value))
                        }
                        .onFailure {
                            putOff = true
                            error = UiError.of(UiError.Kind.CombineActivity, it)
                        }
                }

            ActivityDetailEvent.NotNowSuggestion -> putOff = true

            ActivityDetailEvent.DontCombineSuggestion ->
                flights.launch(event) {
                    runSuspendCatching { suggestions.dismiss(CombineSubject(activity), current) }.onSuccess { dismissed = true }
                }

            else -> Unit
        }
    }
}
