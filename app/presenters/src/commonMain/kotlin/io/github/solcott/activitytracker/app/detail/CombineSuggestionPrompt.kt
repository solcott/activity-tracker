package io.github.solcott.activitytracker.app.detail

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.slack.circuit.runtime.Navigator
import io.github.solcott.activitytracker.app.SingleFlight
import io.github.solcott.activitytracker.app.UiError
import io.github.solcott.activitytracker.atproto.runSuspendCatching
import io.github.solcott.activitytracker.data.activity.ActivityManager
import io.github.solcott.activitytracker.data.activity.CombineSubject
import io.github.solcott.activitytracker.data.activity.CombineSuggestion
import io.github.solcott.activitytracker.data.activity.CombineSuggestions
import io.github.solcott.activitytracker.lexicon.app.ActivityView

/** The owner's combine suggestion for the page's activity: [state] is null without one. */
internal class CombineSuggestionPrompt(val state: CombineSuggestionState?, private val on: (ActivityDetailEvent) -> Unit) {
    fun on(event: ActivityDetailEvent) = on.invoke(event)
}

/**
 * Looks for a combine suggestion for the owner's [activity] (null on someone else's), shown as a prompt while [suggestOnOpen] and not yet
 * answered, then as a banner. Combine combines them and opens the combined activity in this page's place; Don't combine remembers the pair.
 */
@Composable
internal fun rememberCombineSuggestion(
    activity: ActivityView?,
    suggestOnOpen: Boolean,
    suggestions: CombineSuggestions,
    manager: ActivityManager,
    navigator: Navigator,
    flights: SingleFlight,
): CombineSuggestionPrompt {
    var suggestion by remember { mutableStateOf<CombineSuggestion?>(null) }
    var putOff by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<UiError?>(null) }
    LaunchedEffect(activity?.uri) { suggestion = activity?.let { runSuspendCatching { suggestions.forActivity(it) }.getOrNull() } }
    val current = suggestion
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
                    runSuspendCatching { suggestions.dismiss(CombineSubject(activity), current) }.onSuccess { suggestion = null }
                }

            else -> Unit
        }
    }
}
